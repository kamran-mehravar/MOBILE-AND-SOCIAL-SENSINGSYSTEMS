package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;

import java.io.IOException;

import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class DataCollectionActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    private static final int RECORDS_SEC = 50;
    private static final int BUS = 0;
    private static final int CAR = 1;
    private static final int MOTO = 2;
    private static final int WALK = 3;
    private static final int TRAIN = 4;

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean recording = false;
    private boolean first = false;
    private Context c;
    private File f;
    private SeekBar overlappingBar;
    private int currentVehicle = -1, overlapProgress;
    private double[][] overlapData;

    // accelerometer data
    private final static int MAX_TESTS_NUM = 256; // 5 seconds of window size

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
        removeButtonBorder();

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
        // Initialize the Timestamp.;
        // Start the FourierRunnable.
        (new Thread(this.getFourierRunnable())).start();
    }

    @Override
    public void onClick(View v) {
        if (recording) {
            if (v.getId() == R.id.recordButton) {
                stopRecording();
                overlappingBar.setEnabled(true);
                announceEvent("Data files save on successful");
            }
        } else {
            if (v.getId() == R.id.recordButton) {
                overlappingBar.setEnabled(false);
                startRecording();
            } else if (v.getId() == R.id.busButton) {
                removeButtonBorder();
                Button b = v.findViewById(R.id.busButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = BUS;
            } else if (v.getId() == R.id.carButton) {
                removeButtonBorder();
                Button b = v.findViewById(R.id.carButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = CAR;
            } else if (v.getId() == R.id.walkButton) {
                removeButtonBorder();
                Button b = v.findViewById(R.id.walkButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = WALK;
            } else if (v.getId() == R.id.trainButton) {
                removeButtonBorder();
                Button b = v.findViewById(R.id.trainButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = TRAIN;
            } else if (v.getId() == R.id.motoButton) {
                removeButtonBorder();
                Button b = v.findViewById(R.id.motoButton);
                b.setBackgroundColor(ContextCompat.getColor(c, com.androidplot.R.color.ap_gray));
                currentVehicle = MOTO;
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

    private void announceEvent(String data) {
        TextView tv = findViewById(R.id.infoMessageTV);
        tv.setText(data);
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                tv.setText("");
            }
        }, 5000);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
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
                    Log.i("fail", "RUN");
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
                    } else {
                        // Here, we've wasted an entire frame of accelerometer data.
                        Log.d("TB/API", "Wasted samples.");
                    }
                    // Reset the Offset.
                    this.setOffset((int) (MAX_TESTS_NUM * overlapProgress / 100f));
                    // Re-initialize the Timestamp.;
                }
            }
        } catch (Exception e) {
            Log.i("fail", "", e);
        }

    }

    private double[][] getFinalResultBuffer(double[][] pResultBuffer) {
        double[][] finalResult = new double[3][MAX_TESTS_NUM];
        Log.i("fail", "" + pResultBuffer[0].length);
        if (first) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < overlapData[i].length; j++) {
                    finalResult[i][j] = overlapData[i][j];
                }
                for (int j = overlapData[i].length; j < MAX_TESTS_NUM; j++) {
                    finalResult[i][j] = pResultBuffer[i][j];
                }
            }
        } else {
            return pResultBuffer;
        }
        return finalResult;
    }

    // FFT
    public final void onFourierResult(final double[][] pResultBuffer) {
        // Linearize execution.
        try {
            this.runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    double[][] finalBuffer = getFinalResultBuffer(pResultBuffer);
                    StringBuilder sbMagnitude = new StringBuilder(currentVehicle + "," + DataCollectionActivity.computeUUID());
                    for (int i = 0; i < 3; i++) {
                        final double[] lResult = finalBuffer[i];
                        for (int j = 0; j < lResult.length; j++) {
                            if (j < MAX_TESTS_NUM/2) {
                                sbMagnitude.append(",").append(Math.pow(10, lResult[j]/20));
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
                    first = true;
                }
            });
        } catch (Exception e) {
            Log.i("fail", "", e);
        }
    }

    private double[][] getSampleWindows() {
        return this.mSampleWindows;
    }

    private final double[][] getDecoupler() {
        return this.mDecoupler;
    }

    private final FourierRunnable getFourierRunnable() {
        return this.mFourierRunnable;
    }

    private void setOffset(final int pOffset) {
        this.mOffset = pOffset;
    }

    private int getOffset() {
        return this.mOffset;
    }

    private void startRecording() {
        try {
            Chronometer timeRecorded = findViewById(R.id.chronometer1);
            f = DataWindow.initTempFiles("fft_" + this.overlapProgress, c.getFilesDir(), false);
            sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
            recording = true;
            first = false;
            this.setOffset(0);
            overlapData = new double[3][(int) (MAX_TESTS_NUM * overlapProgress / 100f)];
            timeRecorded.setBase(SystemClock.elapsedRealtime());
            timeRecorded.start();
        } catch(Exception e) {
            Log.i("fail", "", e);
        }
    }

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

    @SuppressLint("NewApi")
    public void initListeners() {
        overlappingBar = findViewById(R.id.overlappingBar);
        overlappingBar.setOnSeekBarChangeListener(this);
    }

    public void removeButtonBorder() {
        Button b = findViewById(R.id.motoButton);
        b.setBackgroundColor(this.getResources().getColor(R.color.purple_200, null));
        b = findViewById(R.id.busButton);
        b.setBackgroundColor(this.getResources().getColor(R.color.purple_200, null));
        b = findViewById(R.id.carButton);
        b.setBackgroundColor(this.getResources().getColor(R.color.purple_200, null));
        b = findViewById(R.id.walkButton);
        b.setBackgroundColor(this.getResources().getColor(R.color.purple_200, null));
        b = findViewById(R.id.trainButton);
        b.setBackgroundColor(this.getResources().getColor(R.color.purple_200, null));
    }

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
            ((TextView) findViewById(R.id.overlappingText)).setText("Overlap    =   " + overlapProgress);
        }
    }

    private void manageBarProgress(int overlapProgress) {
        this.overlapProgress = overlapProgress / 25;
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
        private double[][] mResultBuffer;
        private boolean mReady;
        private float mSampleRate;

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
            this.mSampleRate = -1.0f;
        }

        @Override
        public final void run() {
            // Do forever.
            try {
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
                    mResultBuffer = DataWindow.computeFFT(this.getSampleBuffer(), this.getResultBuffer());
                    // Update the Callback.
                    DataCollectionActivity.this.onFourierResult(this.getResultBuffer());
                }
            } catch (Exception e) {
                Log.i("fail", "", e);
            }
        }

        /* Getters. */
        private final double[][] getSampleBuffer() {
            return this.mSampleBuffer;
        }

        private final double[][] getResultBuffer() {
            return this.mResultBuffer;
        }

        private final void setReady(final boolean pIsReady) {
            this.mReady = pIsReady;
        }

        public final boolean isReady() {
            return this.mReady;
        }
    }
}
