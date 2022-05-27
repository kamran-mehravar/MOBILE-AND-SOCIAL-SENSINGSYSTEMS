package com.example.vehicledetection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import java.util.NoSuchElementException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.dataCollectionButton) {
            Intent intent = new Intent(this, DataCollectionActivity.class);
            startActivity(intent);
        } else if(v.getId() == R.id.monitorButton) {
            Intent intent = new Intent(this, MonitorActivity.class);
            startActivity(intent);
        } else {
            throw new NoSuchElementException();
        }
    }
}