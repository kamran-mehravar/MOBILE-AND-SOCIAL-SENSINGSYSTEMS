package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
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
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;

import java.io.InputStream;
import java.util.Arrays;
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

    // CONSTANT VALUES
    private static final int MONITORING_REPETITIONS = 6; // collect data for 6 time windows (5s each)
    private static final int SAMPLE_SIZE = 256;
    private static final double SAMPLE_MIN_THRESHOLD = 0.3; // if the sample collected has a maximum value lower than the threshold it tis discarded

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean monitoringStatus = false;
    private Context c;
    private File inferenceTempFile;
    private XYPlot plot;

    // Monitoring results
    private int bikeValue = 0;
    private int scooterValue = 0;
    private int walkValue = 0;
    private int runValue = 0;
    private int busValue = 0;

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
        // Start the FourierRunnable.
        (new Thread(this.getFourierRunnable())).start();
        createClassifier();
    }

    /**
     * Create ML classifier
     */
    private void createClassifier() {
        try {
            InputStream is = getResources().getAssets().open("model.pmml.ser");
            PMML pmml = SerializationUtil.deserializePMML(is);
            ModelEvaluatorBuilder evaluatorBuilder = new ModelEvaluatorBuilder(pmml);
            evaluator = evaluatorBuilder.build();
            evaluator.verify();
            List<InputField> inputFields = evaluator.getInputFields();
            List<TargetField> targetFields = evaluator.getTargetFields();
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.startMonitoringButton) {
            if (!monitoringStatus) {
                startMonitoring();
            }
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            for (int i = 0; i < event.values.length; i++) {
                // Update the SampleWindows.
                this.getSampleWindows()[i][this.getOffset()] = event.values[i];
            }
            // Increase the Offset.
            this.setOffset(this.getOffset() + 1);
            // Is the buffer full?
            if (this.getOffset() == SAMPLE_SIZE) {
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
                }
                // Reset the Offset.
                this.monitoringCounter++;
                this.setOffset(0);
                if (monitoringCounter == MONITORING_REPETITIONS) {
                    stopMonitoring();
                }
            }
        }
    }

    /**
     * Write fft data into a file
     *
     * @param pResultBuffer fft data
     */
    public final void onFourierResult(final double[][] pResultBuffer) {
        // Linearize execution.
        StringBuilder sbMagnitude = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            final double[] lResult = pResultBuffer[i];
            for (int j = 0; j < lResult.length; j++) {
                if (j < SAMPLE_SIZE / 2) {
                    sbMagnitude.append(",").append(Math.pow(10, lResult[j] / 20));
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

    /**
     * Start to get data from accelerometer
     */
    private void startMonitoring() {
        try {
            Chronometer timeRecorded = findViewById(R.id.monitoringChronometer);
            timeRecorded.setVisibility(View.VISIBLE);
            plot.setVisibility(View.INVISIBLE);
            hideTextResults();
            inferenceTempFile = DataWindow.initTempFiles("temp_sample_data", c.getFilesDir(), true);
            sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME); // SENSOR_DELAY_GAME: 20ms sample interval
            monitoringStatus = true;
            timeRecorded.setBase(SystemClock.elapsedRealtime());
            timeRecorded.start();
            monitoringCounter = 0;
            scooterValue = 0;
            bikeValue = 0;
            walkValue = 0;
            busValue = 0;
            runValue = 0;
        } catch (Exception e) {
            Log.i("fail", "", e);
        }

    }

    private void hideTextResults() {
        findViewById(R.id.textViewBike).setVisibility(View.INVISIBLE);
        findViewById(R.id.textViewScooter).setVisibility(View.INVISIBLE);
        findViewById(R.id.textViewWalk).setVisibility(View.INVISIBLE);
        findViewById(R.id.textViewRun).setVisibility(View.INVISIBLE);
        findViewById(R.id.textViewBus).setVisibility(View.INVISIBLE);
    }

    /**
     * Stop getting data from accelerometer and start computing results
     */
    private void stopMonitoring() {
        Chronometer focus = findViewById(R.id.monitoringChronometer);
        sm.unregisterListener(this, sAcceleration);
        monitoringStatus = false;
        focus.setVisibility(View.INVISIBLE);
        focus.setBase(SystemClock.elapsedRealtime());
        focus.stop();
        returnInference(inferenceTempFile);
        showResults();
        inferenceTempFile.delete();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    /**
     * Initialize sensor manager and accelerometer objects
     */
    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    /**
     * Get the expected activity from data recorded
     *
     * @param f file where data is taken
     */
    public void returnInference(File f) {
        int result; // not recognizable value
        // Read arguments from File and execute the model
        HashMap<String, String> arguments = new HashMap<>(); // create a map for the argument values
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            line = reader.readLine();
            String[] keys = line.split(","); // keys are only mx0 to mx127 - the values from y and z axis are added to the x axis values
            Log.i("keys: ", line + "\n");
            for (int n = 0; n < MONITORING_REPETITIONS; n++) { // iterate over number of monitoring windows captured (equal to the number of lines)
                if ((line = reader.readLine()) != null) {
                    String[] values = line.split(",", (SAMPLE_SIZE / 2) *3);
                    // Write sample data in the map
                    if (values.length > 1 && keys.length > 1) {
                        Log.i("fail", values.length + " " + keys.length);
                        double[] values_all = Arrays.stream(values).mapToDouble(Double::parseDouble).toArray(); // convert values to double
                        double[] combined_values = new double[(SAMPLE_SIZE / 2)];
                        for (int k=0; k<(SAMPLE_SIZE / 2);k++ ) { // add y and z axis data to x axis data
                            combined_values[k] = values_all[k]+values_all[k+128]+values_all[k+256];
                        }
                        double max_sample_value = arrayMax(combined_values);
                        if (max_sample_value > SAMPLE_MIN_THRESHOLD) {
                            for (int i = 0; i < (SAMPLE_SIZE / 2); i++) {
                                double normal_value = combined_values[i]/max_sample_value; // normalize the sample input
                                arguments.put(keys[i], Double.toString(normal_value));
                            }
                            // get Inference value from the model
                            Log.i("Arguments Map: ", arguments.toString());
                            Map<String, ?> results = evaluator.evaluate(arguments);
                            results = EvaluatorUtil.decodeAll(results);
                            Log.i("RESULTS: ", results.toString());
                            arguments.clear();
                            result = (int) results.get("y");
                            if (result == 0) bikeValue++;
                            else if (result == 1) scooterValue++;
                            else if (result == 2) walkValue++;
                            else if (result == 3) runValue++;
                            else if (result == 4) busValue++;
                            else {
                                Log.i("error: ", "model result not listed\n");
                            }
                        }
                        else {
                            Log.i("error: ", "Sample DISCARDED - Min Threshold not crossed\n");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("Fail to Read file in inference map", "", e);
        }
    }

    public static double arrayMax(double[] arr) {
        double max = Double.NEGATIVE_INFINITY;
        for(double cur: arr)
            max = Math.max(max, cur);
        return max;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics());
    }

    /**
     * Show the results computed by machine learning on a plot
     */
    private void showResults() {
        // if / else fast conditions as last param
        XYSeries s1 = new SimpleXYSeries(SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, "Bike", 0.95, (bikeValue == 0) ? 0.1 : bikeValue);
        plot.addSeries(s1, new BarFormatter(Color.GREEN, Color.BLACK));
        XYSeries s2 = new SimpleXYSeries(SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, "Scooter", 1.7, (scooterValue == 0) ? 0.1 : scooterValue);
        plot.addSeries(s2, new BarFormatter(Color.RED, Color.BLACK));
        XYSeries s3 = new SimpleXYSeries(SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, "Walk", 2.45, (walkValue == 0) ? 0.1 : walkValue);
        plot.addSeries(s3, new BarFormatter(Color.YELLOW, Color.BLACK));
        XYSeries s4 = new SimpleXYSeries(SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, "Run", 3.2, (runValue == 0) ? 0.1 : runValue);
        plot.addSeries(s4, new BarFormatter(Color.BLUE, Color.BLACK));
        XYSeries s5 = new SimpleXYSeries(SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, "Bus", 3.95, (busValue == 0) ? 0.1 : busValue);
        plot.addSeries(s5, new BarFormatter(Color.parseColor("#FFA500"), Color.BLACK));
        BarRenderer renderer = (BarRenderer) plot.getRenderer(BarRenderer.class);
        float scale = getResources().getDisplayMetrics().density;
        renderer.setBarWidth((int) (43 * scale + 0.5f));
        int constantHeight = dpToPx(764);
        int constantPadding = dpToPx(220);
        int variablePadding = 87;
        findViewById(R.id.textViewBike).setVisibility(View.VISIBLE);
        findViewById(R.id.textViewScooter).setVisibility(View.VISIBLE);
        findViewById(R.id.textViewWalk).setVisibility(View.VISIBLE);
        findViewById(R.id.textViewRun).setVisibility(View.VISIBLE);
        findViewById(R.id.textViewBus).setVisibility(View.VISIBLE);
        TextView tv1 = findViewById(R.id.textViewBike);
        tv1.setPadding(dpToPx(69), constantHeight - constantPadding - dpToPx(bikeValue * variablePadding), 0, 0);
        tv1.setText("Bike" + "\n" + String.format("%.1f", bikeValue / (double) MONITORING_REPETITIONS * 100.0) + "%");
        TextView tv2 = findViewById(R.id.textViewScooter);
        tv2.setPadding(dpToPx(114), constantHeight - constantPadding - dpToPx(scooterValue * variablePadding), 0, 0);
        tv2.setText("Scooter" + "\n" + String.format("%.1f", scooterValue / (double) MONITORING_REPETITIONS * 100.0) + "%");
        TextView tv3 = findViewById(R.id.textViewWalk);
        tv3.setPadding(dpToPx(181), constantHeight - constantPadding - dpToPx(walkValue * variablePadding), 0, 0);
        tv3.setText("Walk" + "\n" + String.format("%.1f", walkValue / (double) MONITORING_REPETITIONS * 100.0) + "%");
        TextView tv4 = findViewById(R.id.textViewRun);
        tv4.setPadding(dpToPx(243), constantHeight - constantPadding - dpToPx(runValue * variablePadding), 0, 0);
        tv4.setText("Run" + "\n" + String.format("%.1f", runValue / (double) MONITORING_REPETITIONS * 100.0)  + "%");
        TextView tv5 = findViewById(R.id.textViewBus);
        tv5.setPadding(dpToPx(299), constantHeight - constantPadding - dpToPx(busValue * variablePadding), 0, 0);
        tv5.setText("Bus" + "\n" + String.format("%.1f", busValue / (double) MONITORING_REPETITIONS * 100.0) + "%");
        plot.getLayoutManager().refreshLayout();
        plot.redraw();
        plot.setVisibility(View.VISIBLE);
    }

    /**
     * Start default characteristics of the plot used to show results
     */
    private void initPlot() {
        plot = findViewById(R.id.plot);
        plot.setVisibility(View.INVISIBLE);
        plot.setUserRangeOrigin(0);
        plot.setRangeBoundaries(0, 6, BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, 5, BoundaryMode.FIXED);
        plot.setRangeStep(XYStepMode.INCREMENT_BY_VAL, 1);

        plot.getGraphWidget().getBackgroundPaint().setColor(Color.TRANSPARENT);
        plot.getGraphWidget().getGridBackgroundPaint().setColor(Color.TRANSPARENT);

        Paint bgPaint = new Paint();
        int nightModeFlags = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES)
            bgPaint.setColor(Color.parseColor("#121212"));
        else bgPaint.setColor(Color.WHITE);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setAlpha(255);
        plot.setBackgroundPaint(bgPaint);
        plot.getGraphWidget().setDomainGridLinePaint(null);
        plot.getGraphWidget().setRangeGridLinePaint(null);
        plot.getBorderPaint().setColor(Color.TRANSPARENT);

        plot.getGraphWidget().setDomainOriginLinePaint(null);
        plot.getGraphWidget().setRangeOriginLinePaint(null);
        plot.getLayoutManager().remove(plot.getDomainLabelWidget());
        plot.getLayoutManager().remove(plot.getLegendWidget());
    }

    private double[][] getSampleWindows() {
        return this.mSampleWindows;
    }

    private double[][] getDecoupler() {
        return this.mDecoupler;
    }

    private MonitorActivity.FourierRunnable getFourierRunnable() {
        return this.mFourierRunnable;
    }

    private void setOffset(final int pOffset) {
        this.mOffset = pOffset;
    }

    private int getOffset() {
        return this.mOffset;
    }

    /**
     * Compute FFT from data recorded on each window of the accelerometer
     */
    private final class FourierRunnable implements Runnable {
        /* Member Variables. */
        private final double[][] mSampleBuffer;
        private final double[][] mResultBuffer;
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
        private double[][] getSampleBuffer() {
            return this.mSampleBuffer;
        }

        private double[][] getResultBuffer() {
            return this.mResultBuffer;
        }

        private void setReady(final boolean pIsReady) {
            this.mReady = pIsReady;
        }

        public final boolean isReady() {
            return this.mReady;
        }
    }

}