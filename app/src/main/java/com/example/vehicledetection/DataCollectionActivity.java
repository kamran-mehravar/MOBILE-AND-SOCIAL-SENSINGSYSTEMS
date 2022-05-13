package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

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

public class DataCollectionActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener {

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
    private File dataFile; // TODO remove array
    private SeekBar windowSizeBar, overlappingBar;
    private int currentVehicle = -1, currentWindow, overlapProgress;
    private StringBuilder[] currentData = new StringBuilder[2];
    private Timer timer;
    private DataWindow window;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        setSensors();
        c = this.getBaseContext();
        initTempFiles();
        initListeners();
        removeButtonBorder();
        timer = new Timer();
        window = new DataWindow(RECORDS_SEC);
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.recordButton) {
            if (!recording) { startRecording(); }
            else { stopRecording(); }
        } else if (v.getId() == R.id.busButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.busButton);
            b.setEnabled(false);
            currentVehicle = this.BUS;
        } else if (v.getId() == R.id.carButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.carButton);
            b.setEnabled(false);
            currentVehicle = this.CAR;
        } else if (v.getId() == R.id.walkButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.walkButton);
            b.setEnabled(false);
            currentVehicle = this.WALK;
        } else if (v.getId() == R.id.trainButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.trainButton);
            b.setEnabled(false);
            currentVehicle = this.TRAIN;
        } else if (v.getId() == R.id.motoButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.motoButton);
            b.setEnabled(false);
            currentVehicle = this.MOTO;
        } else if (v.getId() == R.id.removeFiles) {
            File directory = new File(c.getFilesDir().getPath());
            for (File file : Objects.requireNonNull(directory.listFiles())) {
                if (!file.isDirectory()) {
                    file.delete();
                }
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
        }
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

    private void stopRecording() {
        Chronometer focus = findViewById(R.id.chronometer1);
        sm.unregisterListener(this);
        recording = false;
        timer.cancel();
        focus.setBase(SystemClock.elapsedRealtime());
        focus.stop();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    @SuppressLint("NewApi")
    public void initListeners() {
        windowSizeBar = findViewById(R.id.windowSizeBar);
        windowSizeBar.setOnSeekBarChangeListener(this);
        windowSizeBar.setMin(1);
        overlappingBar = findViewById(R.id.overlappingBar);
        overlappingBar.setOnSeekBarChangeListener(this);
    }

    public void removeButtonBorder() {
        Button b = findViewById(R.id.motoButton);
        b.setEnabled(true);
        b = findViewById(R.id.busButton);
        b.setEnabled(true);
        b = findViewById(R.id.carButton);
        b.setEnabled(true);
        b = findViewById(R.id.walkButton);
        b.setEnabled(true);
        b = findViewById(R.id.trainButton);
        b.setEnabled(true);
    }

    public void initTempFiles() {
        dataFile = new File(c.getFilesDir(), "data_collection.csv");
    }

    public void writeOnFile(String data) {
        try {
            FileWriter fw = new FileWriter(dataFile, true);
            fw.write(data);
            fw.close();
        } catch (IOException e) {
            Log.i("ERROR", e.toString());
        }
    }

    public void startWindowTimer(boolean first) {
        double delay;
        if (first) delay = windowSizeBar.getProgress() * 1000L;
        else delay = windowSizeBar.getProgress() * 1000L - (windowSizeBar.getProgress() * ((double)overlapProgress/100) * 1000L);
        currentData[0].append(currentVehicle).append(",").append("INSERT RANDOM");
        try {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    window.setData(new StringBuilder(currentData[1].toString()));
                    window.setWindow_time(delay/1000);
                    String fixedData = window.fixDataLength();
                    writeOnFile(currentData[0].toString());
                    writeOnFile(fixedData + "\n");
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
    }

    private String getLastLines(String string, int numLines) {
        try {
            List<String> lines = Arrays.asList(string.split("\n"));
            ArrayList<String> tempArray = new ArrayList<>(lines.subList(Math.max(0, lines.size() - numLines), lines.size()));
            for(int i = 0; i < tempArray.size(); i++) {
                tempArray.set(i, tempArray.get(i).replaceFirst(currentWindow+"", (currentWindow+1)+""));
            }
            return String.join("\n", tempArray);
        } catch (Exception e) {
            Log.i("ERROR", e.toString());
            return "";
        }
    }

    // SEEK BAR EVENT HANDLERS
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
        if (seekBar.getId() == R.id.windowSizeBar) {
            TextView t = findViewById(R.id.windowSizeText);
            t.setText("Window Size    =   " + windowSizeBar.getProgress());
        } else if (seekBar.getId() == R.id.overlappingBar) {
            manageBarProgress(progress);
            ((TextView)findViewById(R.id.overlappingText)).setText("Overlapping    =   " + overlapProgress);
        } else {
            throw new NoSuchElementException();
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
}
