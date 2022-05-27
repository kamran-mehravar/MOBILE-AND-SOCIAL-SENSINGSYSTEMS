package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Chronometer;
import android.widget.TextView;

import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BarRenderer;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.PointLabelFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.InputField;
import org.jpmml.evaluator.TargetField;
import org.jpmml.evaluator.ModelEvaluatorBuilder;
import org.dmg.pmml.PMML;
import org.jpmml.model.SerializationUtil;



public class MonitorActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {

    private static final int MONITORING_REPETITIONS = 2; // collect data for 6 time windows (5s each)
    private static final int SAMPLE_SIZE = 256;

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean monitoringStatus = false;
    private Context c;
    private File inferenceTempFile;
    private StringBuilder rawData, filteredData;
    private XYPlot plot;

    // Monitoring results
    private int bikeValue = 0;
    private int scooterValue = 0;
    private int walkValue = 0;
    private int monitoringCounter = 0;
    private Evaluator evaluator;

    // FFT
    private double[][] mSampleWindows;
    private double[][] mDecoupler;
    private MonitorActivity.FourierRunnable mFourierRunnable;
    private int mOffset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        setSensors();
        c = this.getBaseContext();
        initPlot();
        // FFT
        this.mSampleWindows = new double[][]{
                // X, Y, Z.
                new double[SAMPLE_SIZE],
                new double[SAMPLE_SIZE],
                new double[SAMPLE_SIZE],
        };
        this.mDecoupler = new double[][]{
                // X, Y, Z.
                new double[SAMPLE_SIZE],
                new double[SAMPLE_SIZE],
                new double[SAMPLE_SIZE],
        };
        // Declare the FourierRunnable.
        this.mFourierRunnable = new MonitorActivity.FourierRunnable(this.getDecoupler());
        // Initialize the Timestamp.;
        // Start the FourierRunnable.
        (new Thread(this.getFourierRunnable())).start();
        /** Create Classifier from PMML file **/
        try(FileInputStream is = new FileInputStream(c.getFilesDir() + "/model.pmml.ser")){
            try {
                PMML pmml = SerializationUtil.deserializePMML(is);
                /** Build the model **/
                ModelEvaluatorBuilder evaluatorBuilder = new ModelEvaluatorBuilder(pmml);
                evaluator = evaluatorBuilder.build();
                evaluator.verify();
                Log.i("EVALUATOR:", "BUILT");
                List<InputField> inputFields = evaluator.getInputFields();
                Log.i("INPUT FIELDS:", inputFields.toString());
                List<TargetField> targetFields = evaluator.getTargetFields();
                Log.i("TARGET FIELDS:", targetFields.toString());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.startMonitoringButton) {
            if (!monitoringStatus) {
                startMonitoring();
            }
            else {
                /** no action performed while monitoring **/
            }
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                for (int i = 0; i < event.values.length; i++) {
                    // Update the SampleWindows.
                    this.getSampleWindows()[i][this.getOffset()] = event.values[i];
                }
                // Increase the Offset.
                this.setOffset(this.getOffset() + 1);
                // Is the buffer full?
                if (this.getOffset() == SAMPLE_SIZE) {
                    Log.i("fail", "RUN");
                    // Is the FourierRunnable ready?
                    if (this.getFourierRunnable().isReady()) {
                        // Copy over the buffered data.
                        for (int i = 0; i < this.getSampleWindows().length; i++) {
                            // Copy the data over to the shared buffer.
                            System.arraycopy(this.getSampleWindows()[i], 0, this.getDecoupler()[i], 0, this.getSampleWindows()[i].length);
                        }
                        // Synchronize along the Decoupler.
                        synchronized (this.getDecoupler()) {
                            // Notify any listeners. (There should be one; the FourierRunnable!)
                            this.getDecoupler().notify();
                        }
                    } else {
                        // Here, we've wasted an entire frame of accelerometer data.
                        Log.d("TB/API", "Wasted samples.");
                    }
                    // Reset the Offset.
                    this.monitoringCounter++;
                    this.setOffset(0);
                    // Re-initialize the Timestamp.;
                }
            }

            if (monitoringCounter == MONITORING_REPETITIONS){
                returnInference(inferenceTempFile.getAbsolutePath());
                stopMonitoring();
            }
        } catch (Exception e) {
            stopMonitoring();
            Log.i("fail", "", e);
        }

    }

    // FFT
    public final void onFourierResult(final double[][] pResultBuffer) {
        // Linearize execution.
        try {
            this.runOnUiThread(new Runnable() {
                @Override
                public final void run() {
                    StringBuilder sbMagnitude = new StringBuilder();
                    for (int i = 0; i < 3; i++) {
                        final double[] lResult = pResultBuffer[i];
                        for (int j = 0; j < lResult.length; j++) {
                            if (j < SAMPLE_SIZE/2) {
                                sbMagnitude.append(",").append(Math.pow(10, lResult[j]/20));
                            }
                        }
                        if (i == 2) {
                            sbMagnitude.append("\n");
                        }
                        if (i == 0) {
                            sbMagnitude.replace(0, 1, "");
                        }
                        DataWindow.writeOnFile(sbMagnitude.toString(), inferenceTempFile);
                        sbMagnitude = new StringBuilder();
                    }
                }
            });
        } catch (Exception e) {
            Log.i("fail", "", e);
        }
    }

    private void startMonitoring() {
        Chronometer timeRecorded = findViewById(R.id.monitoringChronometer);
        inferenceTempFile = DataWindow.initTempFiles("temp_sample_data", c.getFilesDir(), true);
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME); // SENSOR_DELAY_GAME: 20ms sample interval
        monitoringStatus = true;
        filteredData = new StringBuilder();
        rawData = new StringBuilder();
        timeRecorded.setBase(SystemClock.elapsedRealtime());
        timeRecorded.start();
        monitoringCounter = 0;
        scooterValue = 0;
        bikeValue = 0;
        walkValue = 0;
    }

    private void stopMonitoring() {
        Chronometer focus = findViewById(R.id.monitoringChronometer);
        sm.unregisterListener(this, sAcceleration);
        monitoringStatus = false;
        focus.setBase(SystemClock.elapsedRealtime());
        focus.stop();
        //inferenceTempFile.delete();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }


    private void returnInference(String dataFilePath) {
        int result = 10; // not recognizable value
        /** Read arguments from File and execute the model **/
        HashMap<String, String> arguments = new HashMap<>(); // create a map for the argument values
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFilePath))) {
            line = reader.readLine();
            String[] keys = line.split(",", (SAMPLE_SIZE/2)*3);
            Log.i("keys: ", line + "\n");
            for (int n=0 ; n<MONITORING_REPETITIONS; n++) { // iterate over number of monitoring windows captured (equal to the number of lines)
                if ((line = reader.readLine()) != null) {
                    String[] values = line.split(",", (SAMPLE_SIZE/2)*3);
                    /** Write sample data in the map **/
                    if (values.length > 1 && keys.length > 1){
                        for (int i=0; i<(SAMPLE_SIZE/2)*3; i++){
                            arguments.put(keys[i], values[i]);
                        }
                        /** get Inference value from the model **/
                        Log.i("Arguments Map: ", arguments.toString());
                        Map<String, ?> results = evaluator.evaluate(arguments);
                        results = EvaluatorUtil.decodeAll(results);
                        Log.i("RESULTS: ", results.toString());
                        arguments.clear();
                        result = (int) results.get("y");
                        if (result == 0){ bikeValue++; }
                        else if (result == 1){ scooterValue++; }
                        else if (result == 2){ walkValue++; }
                        else {Log.i("error: ", "model result not listed\n");}
                    }
                }
            }
            showResults();
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("Fail to Read file in inference map", "");
        }
    }

    private void showResults() {
        TextView tv = findViewById(R.id.tvResults);
        StringBuilder sb = new StringBuilder();
        sb.append("Bike: " + bikeValue + " Scooter: " + scooterValue + " Walk: " + walkValue);
        tv.setText(sb.toString());
    }


    private void initPlot() {
        plot = (XYPlot) findViewById(R.id.plot);
        plot.setVisibility(View.INVISIBLE);
        plot.setUserRangeOrigin(0);
        plot.setRangeBoundaries(0, 7, BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, 3,BoundaryMode.FIXED);
        plot.setRangeStep(XYStepMode.INCREMENT_BY_VAL, 1);
        XYSeries s1 = new SimpleXYSeries(SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, "Bus", 1, 6);
        plot.addSeries(s1, new BarFormatter(Color.GREEN, Color.BLACK));
        XYSeries s2 = new SimpleXYSeries(SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, "Car", 2, 4);
        BarFormatter bf1 = new BarFormatter(Color.RED, Color.BLACK);
        bf1.setPointLabelFormatter(new PointLabelFormatter(Color.WHITE));
        plot.addSeries(s2, bf1);
        BarRenderer renderer = (BarRenderer) plot.getRenderer(BarRenderer.class);
        renderer.setBarWidth(80);
        plot.getGraphWidget().getBackgroundPaint().setColor(Color.TRANSPARENT);
        plot.getGraphWidget().getGridBackgroundPaint().setColor(Color.TRANSPARENT);

        Paint bgPaint = new Paint();
        bgPaint.setColor(Color.BLACK);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAlpha(255);
        plot.setBackgroundPaint(bgPaint);
        plot.getGraphWidget().setDomainGridLinePaint(null);
        plot.getGraphWidget().setRangeGridLinePaint(null);
        plot.getBorderPaint().setColor(Color.TRANSPARENT);

        plot.getLayoutManager().remove(
                plot.getDomainLabelWidget());

        plot.getGraphWidget().setDomainOriginLinePaint(null);
        plot.getGraphWidget().setRangeOriginLinePaint(null);
        s1.getX(0);
        Rect bounds = new Rect();
        bounds.height(); //This should give you the height of the wrapped_content
/**
        TextView tv1 = findViewById(R.id.textViewBus);
        tv1.setPadding(225 + 1 * 250,  2100 - 490 - (6 * 215), 0, 0);
        tv1.setText("BUS");
        TextView tv2 = findViewById(R.id.textViewCar);
        tv2.setPadding(225 + 2 * 250, 2100 - 490 - (4 * 215), 0, 0);
        tv2.setText("CAR");
        plot.getLayoutManager().refreshLayout();
        plot.redraw();
**/
    }

    private double[][] getSampleWindows() {
        return this.mSampleWindows;
    }

    private final double[][] getDecoupler() {
        return this.mDecoupler;
    }

    private final MonitorActivity.FourierRunnable getFourierRunnable() {
        return this.mFourierRunnable;
    }

    private void setOffset(final int pOffset) {
        this.mOffset = pOffset;
    }

    private int getOffset() {
        return this.mOffset;
    }

    private final class FourierRunnable implements Runnable {
        /* Member Variables. */
        private final double[][] mSampleBuffer;
        private double[][] mResultBuffer;
        private boolean mReady;

        /**
         * Constructor.
         */
        public FourierRunnable(final double[][] pSampleBuffer) {
            // Initialize Member Variables.
            this.mSampleBuffer = pSampleBuffer;
            this.mResultBuffer = new double[][]{
                    // Storing two components; Magnitude _and_ Frequency.
                    new double[MonitorActivity.SAMPLE_SIZE],
                    new double[MonitorActivity.SAMPLE_SIZE],
                    new double[MonitorActivity.SAMPLE_SIZE],
            };
        }

        @Override
        public final void run() {
            // Do forever.
            try {
                while (true) { /** TODO: Extern control */
                    // Synchronize along the SampleBuffer.
                    synchronized (this.getSampleBuffer()) {
                        // Assert that we're ready for new samples.
                        this.setReady(true);
                        // Wait to be notified for when the SampleBuffer is ready.
                        try {
                            this.getSampleBuffer().wait();
                        } catch (InterruptedException pInterruptedException) {
                            pInterruptedException.printStackTrace();
                        }
                        // Assert that we're in the middle of processing, and therefore no longer ready.
                        this.setReady(false);
                    }
                    DataWindow.computeFFT(this.getSampleBuffer(), this.getResultBuffer());
                    // Update the Callback.
                    MonitorActivity.this.onFourierResult(this.getResultBuffer());
                }
            } catch (Exception e) {
                Log.i("fail", "", e);
            }
        }

        /* Getters. */
        private final double[][] getSampleBuffer() {
            return this.mSampleBuffer;
        }

        private final double[][] getResultBuffer() {
            return this.mResultBuffer;
        }

        private final void setReady(final boolean pIsReady) {
            this.mReady = pIsReady;
        }

        public final boolean isReady() {
            return this.mReady;
        }
    }

}