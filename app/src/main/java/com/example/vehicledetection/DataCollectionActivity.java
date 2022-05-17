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
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private File dataFile;
    private File fixedDataFile;
    private SeekBar windowSizeBar, overlappingBar;
    private int currentVehicle = -1, currentWindow, overlapProgress;
    private StringBuilder[] currentData = new StringBuilder[2];

    // accelerometer data
    private final int MAX_TESTS_NUM = 40 * 5; // 40 samples in 5s
    private final ArrayList<Float> rawAccData = new ArrayList<Float>();
    private int rawAccDataIdx = 0;

    // LPF
    private ArrayList<String> lpfAccData = new ArrayList<String>();
    private final float[] lpfPrevData = new float[3];
    private int count = 0;
    private float beginTime = System.nanoTime();
    private float rc = 0.002f;
    private boolean isStarted;
    /** private StringBuilder[] filteredData = new StringBuilder[3]; **/
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
            /** add separate arrays **/
            rawAccData.addAll(Arrays.asList(event.values[0], event.values[1], event.values[2]));
            //System.arraycopy(event.values, 0, rawAccData, rawAccDataIdx, 3);


            applyLPF();
            rawAccDataIdx += 3;
            if (rawAccDataIdx >= rawAccData.size()) {
                isStarted = true;
                // stopMeasure();
            }

        }
    }

    private void applyLPF() {
        try {
            final float tm = System.nanoTime();
            final float dt = ((tm - beginTime) / 1000000000.0f) / count;

            final float alpha = rc / (rc + dt);

            if (count == 0) {
                lpfPrevData[0] = (1 - alpha) * rawAccData.get(rawAccDataIdx);
                lpfPrevData[1] = (1 - alpha) * rawAccData.get(rawAccDataIdx + 1);
                lpfPrevData[2] = (1 - alpha) * rawAccData.get(rawAccDataIdx + 2);
            } else {
                lpfPrevData[0] = alpha * lpfPrevData[0] + (1 - alpha) * rawAccData.get(rawAccDataIdx);
                lpfPrevData[1] = alpha * lpfPrevData[1] + (1 - alpha) * rawAccData.get(rawAccDataIdx + 1);
                lpfPrevData[2] = alpha * lpfPrevData[2] + (1 - alpha) * rawAccData.get(rawAccDataIdx + 2);
            }
            //if (isStarted) {
            lpfAccData.add(Float.toString(lpfPrevData[0]));
            lpfAccData.add(Float.toString(lpfPrevData[1]));
            lpfAccData.add(Float.toString(lpfPrevData[2]));
            //}
            ++count;
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("ERROR", e.toString());
        }
    }

    private void startRecording() {
        Chronometer focus = findViewById(R.id.chronometer1);
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
        sm.unregisterListener(this, sAcceleration);
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
        dataFile = new File(c.getFilesDir(), "data_collection_raw.csv");
        fixedDataFile = new File(c.getFilesDir(), "data_collection_fixed.csv");
        if (!dataFile.exists()) {
            StringBuilder init = new StringBuilder("LABEL,UUID");
            for (int i = 0; i < 200; i++) {
                init.append(",accx").append(i).append(",accy").append(i).append(",accz").append(i);
            }
            try {
                FileWriter fw = new FileWriter(dataFile, true);
                fw.write(init + "\n");
                fw.close();
                fw = new FileWriter(fixedDataFile, true);
                fw.write(init + "\n");
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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

    public void writeOnFixedFile(String data) {
        try {
            FileWriter fw = new FileWriter(fixedDataFile, true);
            fw.write(data);
            fw.close();
        } catch (IOException e) {
            Log.i("ERROR", e.toString());
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
        long uuid;
        do {
            uuid = UUID.randomUUID().getMostSignificantBits();
        } while(uuid < 0);
        currentData[0].append(currentVehicle).append(",").append(uuid);
        try {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    window.setData(new StringBuilder(currentData[1].toString()));
                    window.setWindow_time(delay/1000);
                    window.setFixData(lpfAccData.toArray(new String[0]));
                    String fixedData = window.fixDataLength();
                    writeOnFile(currentData[0].toString());
                    writeOnFile("," + fixedData + "\n");
                    writeOnFixedFile(currentData[0].toString());
                    writeOnFixedFile("," + Stream.of(window.getFixData()).filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining(",")) + "\n");
                    double overlaplines = ((double)overlapProgress/100) * RECORDS_SEC * windowSizeBar.getProgress();
                    String nextLines = getLastLines(currentData[0].toString() + fixedData, (int)overlaplines);
                    rawAccDataIdx = 0;
                    count = 0;
                    lpfAccData = new ArrayList<>();
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
            List<String> lines = Arrays.asList(string.split(","));
            ArrayList<String> tempArray = new ArrayList<>(lines.subList(Math.max(0, lines.size() - (numLines * 3)), lines.size()));
            return String.join(",", tempArray);
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
