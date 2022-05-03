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
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.io.IOException;
import java.util.NoSuchElementException;

public class DataCollectionActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean recording = false;
    private Context c;
    private File temp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        setSensors();
        c = this.getBaseContext();
        try { temp = File.createTempFile("data", ".xml", c.getCacheDir()); }
        catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.recordButton) {
            if (!recording) { sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME); recording = true; }
            else { sm.unregisterListener(this); recording = false; }
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

            // --------- Remove after file implement -------------//
//            TextView t = findViewById(R.id.auxiliarText);
//            t.setText("Remove after having data on a file\n\n" + "x: " + String.valueOf(x) + " y: " + String.valueOf(y) + " z: " + String.valueOf(z));
            // -------------------------------------------------- //

            try {
                c = this.getBaseContext();
                FileWriter fw = new FileWriter(temp, true);
                fw.write("x: " + String.valueOf(x) + " y: " + String.valueOf(y) + " z: " + String.valueOf(z) + "\n");
                fw.close();

                // ----------- Remove after seen that write is done successfully ------- //
                FileReader fr = new FileReader(temp);
                int content;
                StringBuffer sb = new StringBuffer();
                while ((content = fr.read()) != -1) {
                    sb.append((char) content);
                }
                Log.i("PRINT", sb.toString());
                TextView t = findViewById(R.id.auxiliarText);
                t.setText(temp.getPath());
                // --------------------------------------------------------------------- //

            } catch (IOException e) {
                Log.i("ERROR", e.toString());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }
}
