package com.example.vehicledetection;

import android.util.Log;

import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataWindow {

    private StringBuilder data;
    private double window_time;
    private int records_sec;
    private String[] fixData;

    public String[] getFixData() { return fixData; }

    public void setFixData(String[] fixData) { this.fixData = fixData; }

    public DataWindow(int records_sec) {
        this.setRecords_sec(records_sec);
    }

    public StringBuilder getData() {
        return data;
    }

    public void setData(StringBuilder data) {
        this.data = data;
    }

    public double getWindow_time() {
        return window_time;
    }

    public void setWindow_time(double window_time) {
        this.window_time = window_time;
    }

    public int getRecords_sec() {
        return records_sec;
    }

    public void setRecords_sec(int records_sec) {
        this.records_sec = records_sec;
    }

}
