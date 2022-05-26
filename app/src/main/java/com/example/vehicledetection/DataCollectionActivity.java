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
    private Context c;
    private File f;
    private SeekBar overlappingBar;
    private int currentVehicle = -1, overlapProgress;
    private double[][] overlapData;
    private DataWindow window;

    // accelerometer data
    private final static int MAX_TESTS_NUM = 256; // 5 seconds of window size

    // FFT
    private double[][]      mSampleWindows;
    private double[][]      mDecoupler;
    private FourierRunnable mFourierRunnable;
    private int             mOffset;
    private long            mTimestamp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        setSensors();
        c = this.getBaseContext();
        initListeners();
        removeButtonBorder();
        window = new DataWindow(RECORDS_SEC);

        // FFT
        this.mSampleWindows   = new double[][] {
                // X, Y, Z.
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
        };
        this.mDecoupler       = new double[][] {
                // X, Y, Z.
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
                new double[MAX_TESTS_NUM],
        };
        // Declare the FourierRunnable.
        this.mFourierRunnable = new FourierRunnable(this.getDecoupler());
        // Initialize the Timestamp.
        this.mTimestamp       = -1L;
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
                    for(int i = 0; i < event.values.length; i++) {
                        // Update the SampleWindows.
                        this.getSampleWindows()[i][this.getOffset()] = event.values[i];
                    }
                    // Increase the Offset.
                    this.setOffset(this.getOffset() + 1);
                    // Is the buffer full?
                    if(this.getOffset() == MAX_TESTS_NUM) {
                        Log.i("fail", "RUN");
                        // Is the FourierRunnable ready?
                        if(this.getFourierRunnable().isReady()) {
                            // Fetch the Timestamp.
                            final long  lTimestamp = System.nanoTime();
                            // Convert the difference in time into the corresponding time in seconds.
                            final float lDelta     = (float)((lTimestamp - this.getTimestamp()) * (0.0000000001));
                            // Determine the Sample Rate.
                            final float lFs        = 1.0f / (lDelta / MAX_TESTS_NUM - (overlapProgress/100f));
                            // Provide the FourierRunnable with the Sample Rate.
                            // this.getFourierRunnable().setSampleRate(lFs);
                            // Copy over the buffered data.
                            for(int i = 0; i < this.getSampleWindows().length; i++) {
                                // Copy the data over to the shared buffer.
                                System.arraycopy(this.getSampleWindows()[i], 0, this.getDecoupler()[i], 0, this.getSampleWindows()[i].length);
                            }
                            // Synchronize along the Decoupler.
                            synchronized(this.getDecoupler()) {
                                // Notify any listeners. (There should be one; the FourierRunnable!)
                                this.getDecoupler().notify();
                            }
                        }
                        else {
                            // Here, we've wasted an entire frame of accelerometer data.
                            Log.d("TB/API", "Wasted samples.");
                        }
                        // Reset the Offset.
                        this.setOffset((int) (MAX_TESTS_NUM * overlapProgress/100f));
                        // Re-initialize the Timestamp.
                        this.setTimestamp(System.nanoTime());
                    }
                }
            } catch (Exception e) {
                Log.i("fail", "", e);
            }

    }

    private double[][] getFinalResultBuffer(double[][] pResultBuffer) {
        double[][] finalResult = new double[3][MAX_TESTS_NUM];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < overlapData[i].length; j++) {
                finalResult[i][j] = overlapData[i][j];
            }
            for (int j = overlapData[i].length; j < MAX_TESTS_NUM; j++) {
                finalResult[i][j] = pResultBuffer[i][j-overlapData[i].length];
            }
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
                                sbMagnitude.append(",").append(lResult[j]);
                            }
                            if (j > MAX_TESTS_NUM - (MAX_TESTS_NUM * (overlapProgress / 100f))) {
                                overlapData[i][j - (MAX_TESTS_NUM - (int)(MAX_TESTS_NUM*overlapProgress/100f))] = lResult[j];
                            }
                        }
                        if (i == 2) {
                            sbMagnitude.append("\n");
                        }
                        writeOnFile(sbMagnitude.toString(), f);
                        sbMagnitude = new StringBuilder();
                    }
                }
            });
        } catch (Exception e) {
            Log.i("fail", "", e);
        }
    }

    private double[][] getSampleWindows() {
        return this.mSampleWindows;
    }

    private final  double[][] getDecoupler() {
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

    private void setTimestamp(final long pTimestamp) {
        this.mTimestamp = pTimestamp;
    }

    private long getTimestamp() {
        return this.mTimestamp;
    }

    private void startRecording() {
        Chronometer timeRecorded = findViewById(R.id.chronometer1);
        initTempFiles();
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
        recording = true;
        this.setOffset(0);
        overlapData = new double[3][(int)(MAX_TESTS_NUM * overlapProgress/100f)];
        timeRecorded.setBase(SystemClock.elapsedRealtime());
        timeRecorded.start();
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
    
    public void initTempFiles() {
        f = new File(c.getFilesDir(), "fft_" + this.overlapProgress + ".csv");
        if (!f.exists()) {
            StringBuilder init = new StringBuilder("LABEL,UUID");
            for(int i = 0; i < MAX_TESTS_NUM/2; i++) {
                init.append(",").append("mx").append(i);
            }
            for(int i = 0; i < MAX_TESTS_NUM/2; i++) {
                init.append(",").append("my").append(i);
            }
            for(int i = 0; i < MAX_TESTS_NUM/2; i++) {
                init.append(",").append("mz").append(i);
            }
            try {
                FileWriter fw = new FileWriter(f, true);
                fw.write(init + "\n");
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writeOnFile(String data, File file) {
        try {
            FileWriter fw = new FileWriter(file, true);
            fw.write(data);
            fw.close();
        } catch (IOException e) {
            Log.i("ERROR", e.toString());
        }
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
        private final double[][] mResultBuffer;
        private       boolean    mReady;
        private       float      mSampleRate;
        /** Constructor. */
        public FourierRunnable(final double[][] pSampleBuffer) {
            // Initialize Member Variables.
            this.mSampleBuffer = pSampleBuffer;
            this.mResultBuffer = new double[][] {
                    // Storing two components; Magnitude _and_ Frequency.
                    new double[DataCollectionActivity.MAX_TESTS_NUM],
                    new double[DataCollectionActivity.MAX_TESTS_NUM],
                    new double[DataCollectionActivity.MAX_TESTS_NUM],
            };
            this.mSampleRate   = -1.0f;
        }
        @Override public final void run() {
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
                    // Declare the SampleBuffer.
                    final double[] lFFT = new double[DataCollectionActivity.MAX_TESTS_NUM * 2];
                    // Allocate the FFT.
                    final DoubleFFT_1D lDoubleFFT = new DoubleFFT_1D(DataCollectionActivity.MAX_TESTS_NUM);
                    // Iterate the axis. (Limit to X only.)
                    for (int i = 0; i < 3; i++) {
                        // Fetch the sampled data.
                        final double[] lSamples = this.getSampleBuffer()[i];
                        // Copy over the Samples.
                        System.arraycopy(lSamples, 0, lFFT, 0, lSamples.length);
                        // Parse the FFT.
                        lDoubleFFT.realForwardFull(lFFT);
                        // Iterate the results. (Real/Imaginary components are interleaved.) (Ignoring the first harmonic.)
                        for (int j = 0; j < lFFT.length; j += 2) {
                            // Fetch the Real and Imaginary Components.
                            final double lRe = lFFT[j];
                            final double lIm = lFFT[j + 1];
                            // Calculate the Magnitude, in decibels, of this current signal index.
                            final double lMagnitude = 20.0 * Math.log10(Math.sqrt((lRe * lRe) + (lIm * lIm)) / lSamples.length);
                            // Calculate the frequency at this magnitude.
                            // final double lFrequency = j * this.getSampleRate() / lFFT.length;
                            // Update the ResultBuffer.
                            this.getResultBuffer()[i][j / 2] = lMagnitude;
                            // this.getResultBuffer()[i][j / 2][1] = lFrequency;
                        }
                    }
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
        /*protected final void setSampleRate(final float pSampleRate) {
            this.mSampleRate = pSampleRate;
        }*/
/*        public final float getSampleRate() {
            return this.mSampleRate;
        }*/
    }
}
