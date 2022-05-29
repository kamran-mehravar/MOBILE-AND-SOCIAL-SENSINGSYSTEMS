package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class DataCollectionActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    // CONSTANT VALUES
    private static final int BIKE = 0;
    private static final int SCOOTER = 1;
    private static final int WALK = 2;
    private static final int RUN = 3;
    private static final int BUS = 4;
    private static final int MAX_TESTS_NUM = 256;

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean recording = false;
    private boolean firstRecord = false;
    private Context c;
    private File f;
    private SeekBar overlapBar;
    private int currentVehicle = BIKE, overlapProgress;
    private int windowRecords = 0;
    private double[][] overlapData;

    // FFT
    private double[][] mSampleWindows;
    private double[][] mDecoupler;
    private FourierRunnable mFourierRunnable;
    private int mOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        setSensors();
        c = this.getBaseContext();
        initListeners();
        findViewById(R.id.bikeButton).setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
        // FFT
        this.mSampleWindows = new double[][]{
                // X, Y, Z.
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
        };
        this.mDecoupler = new double[][]{
                // X, Y, Z.
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
        };
        // Declare the FourierRunnable.
        this.mFourierRunnable = new FourierRunnable(this.getDecoupler());
        // Start the FourierRunnable.
        (new Thread(this.getFourierRunnable())).start();
    }

    @Override
    public void onClick(View v) {
        if (recording) {
            if (v.getId() == R.id.recordButton) {
                stopRecording();
                overlapBar.setEnabled(true);
                announceEvent(windowRecords + " window records has been recorded on file " + f.getName());
            }
        } else {
            if (v.getId() == R.id.recordButton) {
                overlapBar.setEnabled(false);
                windowRecords = 0;
                startRecording();
            } else if (v.getId() == R.id.bikeButton) {
                setDefaultButtons();
                Button b = v.findViewById(R.id.bikeButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = BIKE;
            } else if (v.getId() == R.id.walkButton) {
                setDefaultButtons();
                Button b = v.findViewById(R.id.walkButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = WALK;
            } else if (v.getId() == R.id.scooterButton) {
                setDefaultButtons();
                Button b = v.findViewById(R.id.scooterButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = SCOOTER;
            } else if (v.getId() == R.id.runButton) {
                setDefaultButtons();
                Button b = v.findViewById(R.id.runButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = RUN;
            } else if (v.getId() == R.id.busButton) {
                setDefaultButtons();
                Button b = v.findViewById(R.id.busButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = BUS;
            } else if (v.getId() == R.id.removeFiles) {
                File directory = new File(c.getFilesDir().getPath());
                for (File file : Objects.requireNonNull(directory.listFiles())) {
                    if (!file.isDirectory()) {
                        if (file.delete()) {
                            announceEvent("Data files remove it on successful.");
                        } else {
                            announceEvent("Data files cannot been remove it.");
                        }
                    }
                }
            }
        }
    }

    /**
     * Announce a event regarding to collection on a text view
     *
     * @param data string to saw
     */
    private void announceEvent(String data) {
        TextView tv = findViewById(R.id.infoMessageTV);
        tv.setText(data);
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                tv.setText("");
            }
        }, 5000L);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            // Buffer the SensorEvent data.
            for (int i = 0; i < event.values.length; i++) {
                // Update the SampleWindows.
                this.getSampleWindows()[i][this.getOffset()] = event.values[i];
            }
            // Increase the Offset.
            this.setOffset(this.getOffset() + 1);
            // Is the buffer full?
            if (this.getOffset() == MAX_TESTS_NUM) {
                windowRecords++;
                // Is the FourierRunnable ready?
                if (this.getFourierRunnable().isReady()) {
                    // Copy over the buffered data.
                    for (int i = 0; i < this.getSampleWindows().length; i++) {
                        // Copy the data over to the shared buffer.
                        System.arraycopy(this.getSampleWindows()[i], 0, this.getDecoupler()[i], 0, this.getSampleWindows()[i].length);
                    }
                    // Synchronize along the Decoupler.
                    synchronized (this.getDecoupler()) {
                        // Notify any listeners. (There should be one; the FourierRunnable!)
                        this.getDecoupler().notify();
                    }
                }
                // Reset the Offset.
                this.setOffset((int) (MAX_TESTS_NUM * overlapProgress / 100f));
            }
        }
    }

    /**
     * Mix data collected on current window with overlapped data
     *
     * @param pResultBuffer current window data
     * @return mixed data
     */
    private double[][] getFinalResultBuffer(double[][] pResultBuffer) {
        double[][] finalResult = new double[3][MAX_TESTS_NUM];
        if (firstRecord) {
            for (int i = 0; i < 3; i++) {
                System.arraycopy(overlapData[i], 0, finalResult[i], 0, overlapData[i].length);
                if (MAX_TESTS_NUM - overlapData[i].length >= 0)
                    System.arraycopy(pResultBuffer[i], overlapData[i].length, finalResult[i], overlapData[i].length, MAX_TESTS_NUM - overlapData[i].length);
            }
        } else {
            return pResultBuffer;
        }
        return finalResult;
    }

    /**
     * Write FFT data onto a file and write overlapped data results onto another array
     * @param pResultBuffer
     */
    public final void onFourierResult(final double[][] pResultBuffer) {
        // Linearize execution.
        try {
            this.runOnUiThread(() -> {
                double[][] finalBuffer = getFinalResultBuffer(pResultBuffer);
                StringBuilder sbMagnitude = new StringBuilder(currentVehicle + "," + DataCollectionActivity.computeUUID());
                for (int i = 0; i < 3; i++) {
                    final double[] lResult = finalBuffer[i];
                    for (int j = 0; j < lResult.length; j++) {
                        if (j < MAX_TESTS_NUM / 2) {
                            sbMagnitude.append(",").append(Math.pow(10, lResult[j] / 20));
                        }
                        if (j >= MAX_TESTS_NUM - (MAX_TESTS_NUM * (overlapProgress / 100f))) {
                            overlapData[i][j - (MAX_TESTS_NUM - (int) (MAX_TESTS_NUM * overlapProgress / 100f))] = lResult[j];
                        }
                    }
                    if (i == 2) {
                        sbMagnitude.append("\n");
                    }
                    DataWindow.writeOnFile(sbMagnitude.toString(), f);
                    sbMagnitude = new StringBuilder();
                }
                firstRecord = true;
            });
        } catch (Exception e) {
            announceEvent("There was an error computing FFT data.");
        }
    }

    private double[][] getSampleWindows() {
        return this.mSampleWindows;
    }

    private double[][] getDecoupler() {
        return this.mDecoupler;
    }

    private FourierRunnable getFourierRunnable() {
        return this.mFourierRunnable;
    }

    private void setOffset(final int pOffset) {
        this.mOffset = pOffset;
    }

    private int getOffset() {
        return this.mOffset;
    }

    /**
     * Start getting data from accelerometer
     */
    private void startRecording() {
        Chronometer timeRecorded = findViewById(R.id.chronometer1);
        f = DataWindow.initTempFiles("fft_" + this.overlapProgress, c.getFilesDir(), false);
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
        recording = true;
        firstRecord = false;
        this.setOffset(0);
        overlapData = new double[3][(int) (MAX_TESTS_NUM * overlapProgress / 100f)];
        timeRecorded.setBase(SystemClock.elapsedRealtime());
        timeRecorded.start();
    }

    /**
     * Stop getting data from the accelerometer
     */
    private void stopRecording() {
        Chronometer focus = findViewById(R.id.chronometer1);
        sm.unregisterListener(this, sAcceleration);
        recording = false;
        focus.setBase(SystemClock.elapsedRealtime());
        focus.stop();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    /**
     * Init seekbar listener
     */
    public void initListeners() {
        overlapBar = findViewById(R.id.overlappingBar);
        overlapBar.setOnSeekBarChangeListener(this);
    }

    /**
     * Set buttons visual like on create
     */
    public void setDefaultButtons() {
        int nightModeFlags = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        findViewById(R.id.bikeButton).setBackgroundColor((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) ? this.getResources().getColor(R.color.teal_700, null) : this.getResources().getColor(R.color.teal_700, null));
        findViewById(R.id.walkButton).setBackgroundColor((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) ? this.getResources().getColor(R.color.teal_700, null) : this.getResources().getColor(R.color.teal_700, null));
        findViewById(R.id.scooterButton).setBackgroundColor((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) ? this.getResources().getColor(R.color.teal_700, null) : this.getResources().getColor(R.color.teal_700, null));
        findViewById(R.id.runButton).setBackgroundColor((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) ? this.getResources().getColor(R.color.teal_700, null) : this.getResources().getColor(R.color.teal_700, null));
        findViewById(R.id.busButton).setBackgroundColor((nightModeFlags == Configuration.UI_MODE_NIGHT_YES) ? this.getResources().getColor(R.color.teal_700, null) : this.getResources().getColor(R.color.teal_700, null));
    }

    /**
     * Compute a random UUID for a window record of integer 128 bits
     *
     * @return value
     */
    private static long computeUUID() {
        long uuid;
        do {
            uuid = UUID.randomUUID().getMostSignificantBits();
        } while (uuid < 0);
        return uuid;
    }

    // SEEK BAR EVENT HANDLERS
    @SuppressLint("SetTextI18n")
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
        if (seekBar.getId() == R.id.overlappingBar) {
            manageBarProgress(progress);
            ((TextView) findViewById(R.id.overlapValue)).setText(overlapProgress + "%");
        }
    }

    /**
     * Simple method to have the step value on 25 on a seek bar
     *
     * @param overlapProgress
     */
    private void manageBarProgress(int overlapProgress) {
        this.overlapProgress = (int) (overlapProgress / 19);
        this.overlapProgress = this.overlapProgress * 25;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private final class FourierRunnable implements Runnable {
        /* Member Variables. */
        private final double[][] mSampleBuffer;
        private final double[][] mResultBuffer;
        private boolean mReady;

        /**
         * Constructor.
         */
        public FourierRunnable(final double[][] pSampleBuffer) {
            // Initialize Member Variables.
            this.mSampleBuffer = pSampleBuffer;
            this.mResultBuffer = new double[][]{
                    // Storing two components; Magnitude _and_ Frequency.
                    new double[DataCollectionActivity.MAX_TESTS_NUM],
                    new double[DataCollectionActivity.MAX_TESTS_NUM],
                    new double[DataCollectionActivity.MAX_TESTS_NUM],
            };
        }

        @Override
        public final void run() {
            // Do forever.
            while (true) { /** TODO: Extern control */
                // Synchronize along the SampleBuffer.
                synchronized (this.getSampleBuffer()) {
                    // Assert that we're ready for new samples.
                    this.setReady(true);
                    // Wait to be notified for when the SampleBuffer is ready.
                    try {
                        this.getSampleBuffer().wait();
                    } catch (InterruptedException pInterruptedException) {
                        pInterruptedException.printStackTrace();
                    }
                    // Assert that we're in the middle of processing, and therefore no longer ready.
                    this.setReady(false);
                }
                DataWindow.computeFFT(this.getSampleBuffer(), this.getResultBuffer());
                // Update the Callback.
                DataCollectionActivity.this.onFourierResult(this.getResultBuffer());
            }
        }

        /* Getters. */
        private double[][] getSampleBuffer() {
            return this.mSampleBuffer;
        }

        private double[][] getResultBuffer() {
            return this.mResultBuffer;
        }

        private void setReady(final boolean pIsReady) {
            this.mReady = pIsReady;
        }

        public final boolean isReady() {
            return this.mReady;
        }
    }
}
