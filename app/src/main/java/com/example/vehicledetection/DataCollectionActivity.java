package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

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

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

public class DataCollectionActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean recording = false;
    private Context c;
    private File[] tempFiles;
    private SeekBar windowSizeBar, overlappingBar;
    private int currentVehicle = -1, currentWindow;
    private StringBuffer currentData;
    private Timer timer;

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
            currentData.append(currentWindow + "," + x + "," + y + "," + z + "\n");
        }
    }

    private void startRecording() {
        Chronometer focus = (Chronometer) findViewById(R.id.chronometer1);
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
        recording = true;
        startWindowTimer();
        currentWindow = 1;
        currentData = new StringBuffer();
        focus.setBase(SystemClock.elapsedRealtime());
        focus.start();
    }

    private void stopRecording() {
        Chronometer focus = (Chronometer) findViewById(R.id.chronometer1);
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

    public void startWindowTimer() {
        try {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    String fixedData = fixDataLength();
                    writeOnFile(fixedData);
                    currentData = new StringBuffer(); // Remove all data after writing for next record
                    currentWindow += 1;
                    startWindowTimer();
                }
            }, windowSizeBar.getProgress() * 1000L, 100000000);
        } catch (Exception e) {
            Log.i("ERROR", e.toString());
        }
    }

    public String fixDataLength() {
        int lineCount;
        sm.unregisterListener(this);
        StringBuffer fixedData = new StringBuffer();
        try {
            lineCount = countLines(currentData.toString());
            if(windowSizeBar.getProgress() * 40 < lineCount) {
                String[] randoms = generateRandomNumbers(lineCount - (40 * windowSizeBar.getProgress()), lineCount);
                Scanner sc = new Scanner(currentData.toString());
                // Iter over string lines
                int i = 0;
                while (sc.hasNextLine()) {
                    if (!Arrays.stream(randoms).anyMatch(Integer.toString(i)::equals)) {
                        fixedData.append(sc.nextLine()+"\n");
                    } else {
                        sc.nextLine();
                    }
                    i++;
                }
                sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
                return fixedData.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return currentData.toString();
    }

    private int countLines(String data) {
        Scanner sc = new Scanner(data);
        int i = 0;
        while (sc.hasNextLine()) {
            sc.nextLine();
            i++;
        }
        return i;
    }

    private String[] generateRandomNumbers(int n, int max) {
        Random rnd = new Random();
        String[] randoms = new String[n];
        String num;
        for (int i = 0; i < n;) {
            num = Integer.toString(rnd.nextInt(max));
            if (!Arrays.stream(randoms).anyMatch(num::equals)) {
                randoms[i] = num;
                i++;
            }
        }
        return randoms;
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
