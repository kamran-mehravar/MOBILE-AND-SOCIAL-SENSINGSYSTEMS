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
import java.util.UUID;

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
    private File rawDataFile, filteredDataFile;
    private SeekBar windowSizeBar, overlappingBar;
    private int currentVehicle = -1, overlapProgress;
    private StringBuilder rawData, filteredData;
    private DataWindow window;

    // accelerometer data
    private final int MAX_TESTS_NUM = RECORDS_SEC * 5; // 5 seconds of window size
    private final float[] rawAccData = new float[MAX_TESTS_NUM * 3];
    private int rawAccDataIdx = 0;

    // LPF
    private final float[] lpfPrevData = new float[3];
    private int count = 0;
    private final float beginTime = System.nanoTime();
    private final float rc = 0.002f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        setSensors();
        c = this.getBaseContext();
        initListeners();
        removeButtonBorder();
        window = new DataWindow(RECORDS_SEC);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.recordButton) {
            if (!recording) {
                startRecording();
            } else {
                stopRecording();
            }
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
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            rawData.append(",").append(x).append(",").append(y).append(",").append(z);
            System.arraycopy(event.values, 0, rawAccData, rawAccDataIdx, 3);
            applyLPF();
            filteredData.append(",").append(lpfPrevData[0]).append(",").append(lpfPrevData[1]).append(",").append(lpfPrevData[2]);
            if (count == RECORDS_SEC * windowSizeBar.getProgress()) {
                startNewWindow();
            }
        }
    }

    private void applyLPF() {
        final float tm = System.nanoTime();
        final float dt = ((tm - beginTime) / 1000000000.0f) / count;

        final float alpha = rc / (rc + dt);

        if (count == 0) {
            lpfPrevData[0] = (1 - alpha) * rawAccData[0];
            lpfPrevData[1] = (1 - alpha) * rawAccData[1];
            lpfPrevData[2] = (1 - alpha) * rawAccData[2];
        } else {
            lpfPrevData[0] = alpha * lpfPrevData[0] + (1 - alpha) * rawAccData[count * 3];
            lpfPrevData[1] = alpha * lpfPrevData[1] + (1 - alpha) * rawAccData[(count * 3) + 1];
            lpfPrevData[2] = alpha * lpfPrevData[2] + (1 - alpha) * rawAccData[(count * 3) + 2];
        }
        ++count;
    }

    private void startRecording() {
        Chronometer timeRecorded = findViewById(R.id.chronometer1);
        initTempFiles();
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
        recording = true;
        this.window.setWindow_time(windowSizeBar.getProgress());
        filteredData = new StringBuilder();
        rawData = new StringBuilder();
        startNewWindow();
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

    // TODO se le puede pasar el archivo por param y comprobar que ese archivo existe
    public void initTempFiles() {
        rawDataFile = new File(c.getFilesDir(), "data_collection_raw_" + this.overlapProgress + ".csv");
        filteredDataFile = new File(c.getFilesDir(), "data_collection_filtered_" + this.overlapProgress + ".csv");
        // TODO also check other files
        if (!rawDataFile.exists()) {
            StringBuilder init = new StringBuilder("LABEL,UUID");
            for (int i = 0; i < 200; i++) {
                init.append(",accx").append(i).append(",accy").append(i).append(",accz").append(i);
            }
            try {
                FileWriter fw = new FileWriter(rawDataFile, true);
                fw.write(init.toString());
                fw.close();
                fw = new FileWriter(filteredDataFile, true);
                fw.write(init.toString());
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

    public void startNewWindow() {
        writeOnFile(rawData.toString() + "\n", rawDataFile);
        writeOnFile(filteredData.toString() + "\n", filteredDataFile);
        double numOverlappedLines = ((double) overlapProgress / 100) * RECORDS_SEC * windowSizeBar.getProgress();
        String overlappedLinesRaw = "";
        String overlappedLinesFiltered = "";
        if (numOverlappedLines > 0) {
            overlappedLinesRaw = "," + getOverlappedRecords(rawData.toString(), (int) numOverlappedLines);
            overlappedLinesFiltered = "," + getOverlappedRecords(filteredData.toString(), (int) numOverlappedLines);
        }
        rawAccDataIdx = 0;
        count = 0;
        long uuid = computeUUID();
        rawData = new StringBuilder(this.currentVehicle + "," + uuid + overlappedLinesRaw);
        filteredData = new StringBuilder(this.currentVehicle + "," + uuid + overlappedLinesFiltered);
    }

    private long computeUUID() {
        long uuid;
        do {
            uuid = UUID.randomUUID().getMostSignificantBits();
        } while (uuid < 0);
        return uuid;
    }

    private String getOverlappedRecords(String data, int linesOverlapped) {
        try {
            List<String> lines = Arrays.asList(data.split(","));
            ArrayList<String> tempArray = new ArrayList<>(lines.subList(Math.max(0, lines.size() - (linesOverlapped * 3)), lines.size()));
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
            ((TextView) findViewById(R.id.overlappingText)).setText("Overlapping    =   " + overlapProgress);
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
