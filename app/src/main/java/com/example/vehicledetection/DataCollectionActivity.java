package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.io.IOException;
import java.util.NoSuchElementException;

public class DataCollectionActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {

    Sensor sAcceleration;
    SensorManager sm;
    boolean recording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);
        setSensors();
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
            TextView t = findViewById(R.id.auxiliarText);
            t.setText("Remove after having data on a file\n\n" + "x: " + String.valueOf(x) + " y: " + String.valueOf(y) + " z: " + String.valueOf(z));
            // -------------------------------------------------- //

            try {
                File f = new File("");
                f.createTempFile("data", "xml");
//                FileWriter fw = new FileWriter(f);
//                fw.write("x: " + String.valueOf(x) + " y: " + String.valueOf(y) + " z: " + String.valueOf(z) + "\n");
//                fw.close();

                // ----------- Remove after seen that write is done successfully ------- //
/*                FileReader fr = new FileReader(f);
                int content;
                StringBuffer sb = new StringBuffer();
                while ((content = fr.read()) != -1) {
                    sb.append((char) content);
                }
                Log.i("", sb.toString());*/
                // --------------------------------------------------------------------- //

            } catch (IOException e) {
                e.printStackTrace();
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
