package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MonitorActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {

    private static final int RECORDS_SEC = 40;
    private static final int BUS = 0;
    private static final int CAR = 1;
    private static final int MOTO = 2;
    private static final int WALK = 3;
    private static final int TRAIN = 4;

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean recording = false;
    private Context c;
    private File dataFile;
    private SeekBar windowSizeBar, overlappingBar;
    private int currentVehicle = -1, currentWindow, overlapProgress;
    private StringBuilder[] currentData = new StringBuilder[2];

    // accelerometer data
    private final int MAX_TESTS_NUM = 40 * 5; // 40 samples in 5s
    private final float[] rawAccData = new float[MAX_TESTS_NUM * 3];
    private int rawAccDataIdx = 0;

    // LPF
    private final float[] lpfAccData = new float[MAX_TESTS_NUM * 3];
    private final float[] lpfPrevData = new float[3];
    private int count = 0;
    private float beginTime = System.nanoTime();
    private float rc = 0.002f;
    /** private StringBuilder[] filteredData = new StringBuilder[3]; **/
    private Timer timer;
    private DataWindow window;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        setSensors();
        c = this.getBaseContext();
        timer = new Timer();
        window = new DataWindow(RECORDS_SEC);
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.startMonitoringButton) {
            if (!recording) {
                startRecording();
                /** display timer **/
            }
            else {
                /** no action performed while monitoring **/
            }
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            currentData[1].append(",").append(x).append(",").append(y).append(",").append(z);
            /** add separate arrays **/
            System.arraycopy(event.values, 0, rawAccData, rawAccDataIdx, 3);
            applyLPF();
            rawAccDataIdx += 3;
            if (rawAccDataIdx >= rawAccData.length) {
                // stopMeasure();
            }
        }
    }

    private void applyLPF() {
        final float tm = System.nanoTime();
        final float dt = ((tm - beginTime) / 1000000000.0f) / count;

        final float alpha = rc / (rc + dt);

        if (count == 0) {
            lpfPrevData[0] = (1 - alpha) * rawAccData[rawAccDataIdx];
            lpfPrevData[1] = (1 - alpha) * rawAccData[rawAccDataIdx + 1];
            lpfPrevData[2] = (1 - alpha) * rawAccData[rawAccDataIdx + 2];
        } else {
            lpfPrevData[0] = alpha * lpfPrevData[0] + (1 - alpha) * rawAccData[rawAccDataIdx];
            lpfPrevData[1] = alpha * lpfPrevData[1] + (1 - alpha) * rawAccData[rawAccDataIdx + 1];
            lpfPrevData[2] = alpha * lpfPrevData[2] + (1 - alpha) * rawAccData[rawAccDataIdx + 2];
        }
        //if (isStarted) {
        lpfAccData[rawAccDataIdx]     = lpfPrevData[0];
        lpfAccData[rawAccDataIdx + 1] = lpfPrevData[1];
        lpfAccData[rawAccDataIdx + 2] = lpfPrevData[2];
        //}
        ++count;
    }

    private void startRecording() {
        Chronometer focus = (Chronometer) findViewById(R.id.chronometer1);
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
        recording = true;
        this.window.setWindow_time(windowSizeBar.getProgress());
        currentData[0] = new StringBuilder();
        currentData[1] = new StringBuilder();
        startWindowTimer(true);
        currentWindow = 1;
        focus.setBase(SystemClock.elapsedRealtime());
        focus.start();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    //@SuppressLint("NewApi")
    //public void initListeners() { }

    public void initTempFiles() {
        dataFile = new File(c.getFilesDir(), "data_collection.csv");
        if (!dataFile.exists()) {
            StringBuilder init = new StringBuilder("LABEL,UUID");
            // TODO change number somehow
            for (int i = 0; i < 200; i++) {
                init.append(",accx").append(i).append(",accy").append(i).append(",accz").append(i);
            }
            try {
                FileWriter fw = new FileWriter(dataFile);
                fw.write(init.toString() + "\n");
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stopListeners() {
        sm.unregisterListener(this, sAcceleration);
    }

    public void startListeners() {
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
    }

    public void startWindowTimer(boolean first) {
        double delay;
        if (first) delay = windowSizeBar.getProgress() * 1000L;
        else delay = windowSizeBar.getProgress() * 1000L - (windowSizeBar.getProgress() * ((double)overlapProgress/100) * 1000L);
        /** UUID = UUID.randomUUID().getMostSignificantBits();
         * if UUID < 0
         *      UUID = UUID*(-1);
         * **/
        currentData[0].append(currentVehicle).append(",").append(UUID.randomUUID().getMostSignificantBits());
        try {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    window.setData(new StringBuilder(currentData[1].toString()));
                    window.setWindow_time(delay/1000);
                    String fixedData = window.fixDataLength();
                    writeOnFile(currentData[0].toString());
                    writeOnFile("," + fixedData + "\n");
                    double overlaplines = ((double)overlapProgress/100) * RECORDS_SEC * windowSizeBar.getProgress();
                    String nextLines = getLastLines(currentData[0].toString() + fixedData, (int)overlaplines);
                    currentWindow++;
                    currentData[0] = new StringBuilder(nextLines);
                    currentData[1] = new StringBuilder(); // Remove all data after writing for next record
                    startWindowTimer(false);
                }
            }, (long) delay);
        } catch (Exception e) {
            Log.i("ERROR", e.toString());
        }

        /** end monitoring
         Chronometer focus = findViewById(R.id.chronometer1);
         sm.unregisterListener(this, sAcceleration);
         recording = false;
         timer.cancel();
         focus.setBase(SystemClock.elapsedRealtime());
         focus.stop();
         **/
    }

    private String getLastLines(String string, int numLines) {
        try {
            List<String> lines = Arrays.asList(string.split(","));
            ArrayList<String> tempArray = new ArrayList<>(lines.subList(Math.max(0, lines.size() - (numLines * 3)), lines.size()));
            return String.join(",", tempArray);
        } catch (Exception e) {
            Log.i("ERROR", e.toString());
            return "";
        }
    }


}