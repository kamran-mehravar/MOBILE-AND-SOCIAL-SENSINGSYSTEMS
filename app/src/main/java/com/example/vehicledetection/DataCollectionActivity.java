package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Stream;

public class DataCollectionActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean recording = false;
    private Context c;
    private File[] tempFiles;
    private SeekBar windowSizeBar, overlappingBar;
    private int currentVehicle = -1, currentWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        setSensors();
        c = this.getBaseContext();
        initTempFiles();
        initListeners();
        removeButtonBorder();
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.recordButton) {
            if (!recording) { sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME); recording = true; startWindowTimer(); currentWindow = 1; }
            else { sm.unregisterListener(this); recording = false; }
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
            writeOnFile(x, y, z);
        }
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

    public void writeOnFile(float x, float y, float z) {
        try {
            FileWriter fw = new FileWriter(tempFiles[currentVehicle], true);
            fw.write(currentWindow + "," + x + "," + y + "," + z + "\n");
            fw.close();
        } catch (IOException e) {
            Log.i("ERROR", e.toString());
        }
    }

    public void startWindowTimer() {
        Timer t = new Timer();
        t.schedule(new TimerTask() {
                       @Override
                       public void run() {
                               // if we want to optimize better we can run this function every second and delete for current second instead for each window
                               fixDataLength();

                               currentWindow += 1;
                               startWindowTimer();
                       }
                   }, windowSizeBar.getProgress() * 1000L
        );
    }

    public void fixDataLength() {
        long lineCount;
        String line;
        try {
            Stream<String> stream = Files.lines(tempFiles[currentVehicle].toPath(), StandardCharsets.UTF_8);
            lineCount = stream.count();
            Log.i("LINES", lineCount+"");
            if(windowSizeBar.getProgress() * 40 < lineCount) {
                FileReader fr = new FileReader(tempFiles[currentVehicle]);
                FileWriter fw = new FileWriter(tempFiles[currentVehicle]);
                LineNumberReader lnr = new LineNumberReader(fr);
                String[] randomNumbers = generateRandomNumbers(((int)lineCount - ((currentWindow - 1) * 40 * windowSizeBar.getProgress())) - (40 * windowSizeBar.getProgress()), (currentWindow - 1) * 40, (int)lineCount);
                while ((line = lnr.readLine()) != null) {
                    if (Arrays.stream(randomNumbers).anyMatch(Long.toString(lnr.getLineNumber())::equals)) {
                        //fw.write(System.getProperty("line.separator"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String[] generateRandomNumbers(int n, int min, int max) {
        Random rnd = new Random();
        String[] randoms = new String[n];
        String num;
        for (int i = 0; i < n;) {
            num = Integer.toString(rnd.nextInt(max - min + 1) + min);
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
