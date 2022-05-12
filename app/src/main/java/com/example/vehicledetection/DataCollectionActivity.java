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

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean recording = false;
    private Context c;
    private File[] tempFiles;
    private SeekBar windowSizeBar, overlappingBar;
    private int currentVehicle = -1, currentWindow;
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
            currentVehicle = 0;
        } else if (v.getId() == R.id.carButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.carButton);
            b.setEnabled(false);
            currentVehicle = 1;
        } else if (v.getId() == R.id.walkButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.walkButton);
            b.setEnabled(false);
            currentVehicle = 3;
        } else if (v.getId() == R.id.trainButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.trainButton);
            b.setEnabled(false);
            currentVehicle = 4;
        } else if (v.getId() == R.id.motoButton) {
            removeButtonBorder();
            Button b = v.findViewById(R.id.motoButton);
            b.setEnabled(false);
            currentVehicle = 2;
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
            currentData[1].append(currentWindow).append(",").append(x).append(",").append(y).append(",").append(z).append("\n");
        }
    }

    private void startRecording() {
        Chronometer focus = (Chronometer) findViewById(R.id.chronometer1);
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
        recording = true;
        this.window.setWindow_time(windowSizeBar.getProgress());
        startWindowTimer(true);
        currentWindow = 1;
        currentData[0] = new StringBuilder();
        currentData[1] = new StringBuilder();
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
        overlappingBar.setMin(25);
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
        tempFiles = new File[5];
        try {
            tempFiles[0] = File.createTempFile("bus_data", ".csv", c.getFilesDir());
            tempFiles[1] = File.createTempFile("car_data", ".csv", c.getFilesDir());
            tempFiles[2] = File.createTempFile("motorbike_data", ".csv", c.getFilesDir());
            tempFiles[3] = File.createTempFile("walk_data", ".csv", c.getFilesDir());
            tempFiles[4] = File.createTempFile("train_data", ".csv", c.getFilesDir());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeOnFile(String data) {
        try {
            FileWriter fw = new FileWriter(tempFiles[currentVehicle], true);
            fw.write(data);
            fw.close();
        } catch (IOException e) {
            Log.i("ERROR", e.toString());
        }
    }

    public void startWindowTimer(boolean first) {
        double delay;
        if (first) delay = windowSizeBar.getProgress() * 1000L;
        else delay = windowSizeBar.getProgress() * 1000L - (windowSizeBar.getProgress() * ((double)overlappingBar.getProgress()/100) * 1000L);
        Log.i("OVERLAP", delay + "");
        try {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    window.setData(new StringBuilder(currentData[1].toString()));
                    window.setWindow_time(delay/1000);
                    String fixedData = window.fixDataLength();
                    Log.i("LINES", "Fixed lines: " + window.countLines(fixedData));
                    writeOnFile(currentData[0].toString());
                    writeOnFile(fixedData);
                    double overlaplines = ((double)overlappingBar.getProgress()/100) * RECORDS_SEC * windowSizeBar.getProgress();
                    Log.i("OVERLAP", overlaplines + "");
                    String nextLines = getLastLines(fixedData, (int)overlaplines);
                    currentWindow++;
                    currentData[0] = new StringBuilder(nextLines+"\n");
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
            progress = progress / 25;
            progress = progress * 25;
            ((TextView)findViewById(R.id.overlappingText)).setText("Overlapping    =   " + progress);
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
