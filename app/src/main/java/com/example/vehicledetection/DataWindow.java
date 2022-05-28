package com.example.vehicledetection;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class DataWindow {

    private static final int MAX_TESTS_NUM = 256;

    /**
     * Do mathematical computations from accelerometer data into FFT data.
     * @param sampleBuffer accelerometer data
     * @param resultBuffer FFT transformed data
     * @return FFT data
     */
    public static double[][] computeFFT(double[][] sampleBuffer, double[][] resultBuffer) {
        // Declare the SampleBuffer.
        final double[] lFFT = new double[MAX_TESTS_NUM * 2];
        // Allocate the FFT.
        final DoubleFFT_1D lDoubleFFT = new DoubleFFT_1D(MAX_TESTS_NUM);
        // Iterate the axis. (Limit to X only.)
        for (int i = 0; i < 3; i++) {
            // Fetch the sampled data.
            final double[] lSamples = sampleBuffer[i];
            // Copy over the Samples.
            System.arraycopy(lSamples, 0, lFFT, 0, lSamples.length);
            // Parse the FFT.
            lDoubleFFT.realForwardFull(lFFT);
            // Iterate the results. (Real/Imaginary components are interleaved.) (Ignoring the first harmonic.)
            for (int j = 0; j < lFFT.length; j += 2) {
                // Fetch the Real and Imaginary Components.
                final double lRe = lFFT[j];
                final double lIm = lFFT[j + 1];
                // Calculate the Magnitude, in decibels, of this current signal index.
                final double lMagnitude = 20.0 * Math.log10(Math.sqrt((lRe * lRe) + (lIm * lIm)) / lSamples.length);
                // Update the ResultBuffer.
                resultBuffer[i][j / 2] = lMagnitude;
                // this.getResultBuffer()[i][j / 2][1] = lFrequency;
            }
        }
        return resultBuffer;
    }

    /**
     * Write text into a file
     * @param data string to write into the file
     * @param file file to write the string
     */
    public static void writeOnFile(String data, File file) {
        try {
            FileWriter fw = new FileWriter(file, true);
            fw.write(data);
            fw.close();
        } catch (IOException e) {
            Log.i("ERROR", e.toString());
        }
    }

    /**
     * Init a new file (data collection or monitor data)
     * @param fileName
     * @param path
     * @param isInference if is monitor file need to have other init
     * @return the new file
     */
    public static File initTempFiles(String fileName, File path, boolean isInference) {
        File f = new File(path, fileName + ".csv");
        if (!f.exists()) {
            StringBuilder init;
            if (isInference) init = new StringBuilder();
            else init = new StringBuilder(",LABEL,UUID");
            for (int i = 0; i < MAX_TESTS_NUM / 2; i++) {
                init.append(",").append("mx").append(i);
            }
            for (int i = 0; i < MAX_TESTS_NUM / 2; i++) {
                init.append(",").append("my").append(i);
            }
            for (int i = 0; i < MAX_TESTS_NUM / 2; i++) {
                init.append(",").append("mz").append(i);
            }
            init.replace(0, 1, "");
            try {
                FileWriter fw = new FileWriter(f, true);
                fw.write(init + "\n");
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return f;
    }
}
