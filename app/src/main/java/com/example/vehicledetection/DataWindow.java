package com.example.vehicledetection;

import android.util.Log;

import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

public class DataWindow {

    private StringBuilder data;
    private double window_time;
    private int records_sec;

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

    public String fixDataLength() {
        int lineCount;
        StringBuilder fixedData = new StringBuilder();
        try {
            lineCount = countLines(data.toString());
            if(this.getWindow_time() * this.getRecords_sec() < lineCount) {
                String[] randoms = generateRandomNumbers((int)(lineCount - (this.getRecords_sec() * this.getWindow_time())), lineCount);
                Scanner sc = new Scanner(this.getData().toString());
                // Iter over string lines
                int i = 0;
                while (sc.hasNextLine()) {
                    if (Arrays.stream(randoms).noneMatch(Integer.toString(i)::equals)) {
                        fixedData.append(sc.nextLine()).append("\n");
                    } else {
                        sc.nextLine();
                    }
                    i++;
                }
                return fixedData.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data.toString();
    }

    public int countLines(String data) {
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
            if (Arrays.stream(randoms).noneMatch(num::equals)) {
                randoms[i] = num;
                i++;
            }
        }
        return randoms;
    }
}
