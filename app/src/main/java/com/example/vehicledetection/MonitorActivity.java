package com.example.vehicledetection;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Canvas;
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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.androidplot.util.PixelUtils;
import com.androidplot.util.ValPixConverter;
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

import java.io.IOException;

import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Map;

//import com.beust.jcommander.JCommander;
//import com.beust.jcommander.Parameter;
//import com.beust.jcommander.ParameterException;
//import org.dmg.pmml.PMML;
//import org.jpmml.evaluator.EvaluatorUtil;
//import org.jpmml.evaluator.testing.CsvUtil;
//import org.jpmml.model.PMMLUtil;


public class MonitorActivity extends AppCompatActivity implements SensorEventListener, View.OnClickListener {

    private static final int MONITORING_REPETITIONS = 6; // collect data for 6 time windows (5s each)
    private static final int RECORDS_SEC = 50;
    private static final int BUS = 0;
    private static final int CAR = 1;
    private static final int MOTO = 2;
    private static final int WALK = 3;
    private static final int TRAIN = 4;

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean monitoringStatus = false;
    private Context c;
    private File inferenceTempFile, filteredDataFile;
    private StringBuilder rawData, filteredData;
    private DataWindow window;
    private XYPlot plot;

    // accelerometer data
    private final int SAMPLE_SIZE = RECORDS_SEC * 5; // 5 seconds of window size
    private final float[] rawAccData = new float[3];

    // LPF
    private final float[] lpfPrevData = new float[3];
    private int count = 0;
    private final float beginTime = System.nanoTime();
    private final float rc = 0.002f;

    // Monitoring results
    private int busValue = 0;
    private int carValue = 0;
    private int motoValue = 0;
    private int walkValue = 0;
    private int trainValue = 0;

    private int monitoringCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        setSensors();
        c = this.getBaseContext();
        window = new DataWindow(RECORDS_SEC);
        initPlot();
    }

    private void initPlot() {
        plot = (XYPlot) findViewById(R.id.plot);
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

        TextView tv1 = findViewById(R.id.textViewBus);
        tv1.setPadding(225 + 1 * 250,  2100 - 490 - (6 * 215), 0, 0);
        tv1.setText("BUS");
        TextView tv2 = findViewById(R.id.textViewCar);
        tv2.setPadding(225 + 2 * 250, 2100 - 490 - (4 * 215), 0, 0);
        tv2.setText("CAR");
        plot.getLayoutManager().refreshLayout();
        plot.redraw();

    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.startMonitoringButton) {
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
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            rawData.append(",").append(x).append(",").append(y).append(",").append(z);
            System.arraycopy(event.values, 0, rawAccData, 0, 3);
            applyLPF();
            filteredData.append(",").append(lpfPrevData[0]).append(",").append(lpfPrevData[1]).append(",").append(lpfPrevData[2]);
            Log.i("LOG","Count: " + count + " Lines: " + filteredData.toString().split(",").length/3);
            if (count == SAMPLE_SIZE) {
                monitoringCounter++;
                rawData.replace(0, 1, "");
                filteredData.replace(0, 1, "");
                if (monitoringCounter == MONITORING_REPETITIONS){
                    stopMonitoring();
                } else {
                    startNewWindow();
                }
            }
        }
    }

    private void applyLPF() {
        final float tm = System.nanoTime();
        final float dt = ((tm - beginTime) / 1000000000.0f) / count;

        final float alpha = rc / (rc + dt);
        // alpha = 0.0909

        if (count == 0) {
            lpfPrevData[0] = (1 - alpha) * rawAccData[0];
            lpfPrevData[1] = (1 - alpha) * rawAccData[1];
            lpfPrevData[2] = (1 - alpha) * rawAccData[2];
        } else {
            lpfPrevData[0] = alpha * lpfPrevData[0] + (1 - alpha) * rawAccData[0];
            lpfPrevData[1] = alpha * lpfPrevData[1] + (1 - alpha) * rawAccData[1];
            lpfPrevData[2] = alpha * lpfPrevData[2] + (1 - alpha) * rawAccData[2];
        }
        ++count;
    }

    private void startMonitoring() {
        Chronometer timeRecorded = findViewById(R.id.monitoringChronometer);
        initTempFiles();
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME);
        monitoringStatus = true;
        monitoringCounter = 0;
        count = 0;
        filteredData = new StringBuilder();
        rawData = new StringBuilder();
        startNewWindow();
        timeRecorded.setBase(SystemClock.elapsedRealtime());
        timeRecorded.start();
    }

    private void stopMonitoring() {
        Chronometer focus = findViewById(R.id.monitoringChronometer);
        writeOnFile(filteredData.toString() + "\n", inferenceTempFile);
        sm.unregisterListener(this, sAcceleration);
        monitoringStatus = false;
        focus.setBase(SystemClock.elapsedRealtime());
        focus.stop();
        // TODO compute results
        // TODO plot results
        // inferenceTempFile.delete();
    }

    private void returnInference() { /**
        // Building a model evaluator from a PMML file
        Evaluator evaluator = new LoadingModelEvaluatorBuilder()
                .load(new File("model.pmml"))
                .build();

        // Perforing the self-check
        evaluator.verify();

        // Printing input (x1, x2, .., xn) fields
        List<InputField> inputFields = evaluator.getInputFields();
        System.out.println("Input fields: " + inputFields);

        // Printing primary result (y) field(s)
        List<TargetField> targetFields = evaluator.getTargetFields();
        System.out.println("Target field(s): " + targetFields);

        // Printing secondary result (eg. probability(y), decision(y)) fields
        List<OutputField> outputFields = evaluator.getOutputFields();
        System.out.println("Output fields: " + outputFields);

        // Iterating through columnar data (eg. a CSV file, an SQL result set)
        while(true){
            // Reading a record from the data source
            Map<String, ?> arguments = readRecord();
            if(arguments == null){
                break;
            }

            // Evaluating the model
            Map<String, ?> results = evaluator.evaluate(arguments);

            // Decoupling results from the JPMML-Evaluator runtime environment
            results = EvaluatorUtil.decodeAll(results);

            // Writing a record to the data sink
            writeRecord(results);
        }

        // Making the model evaluator eligible for garbage collection
        evaluator = null;
    **/
    }

    public static Map<String, String> retrieveInferenceDataFromFile(String filePath, boolean dupKeyOption) {
        HashMap<String, String> map = new HashMap<>();
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            while ((line = reader.readLine()) != null) {
                String[] keyValuePair = line.split(":", 2);
                if (keyValuePair.length > 1) {
                    String key = keyValuePair[0];
                    String value = keyValuePair[1];
                    if (dupKeyOption) {
                        map.put(key, value);
                    } else {
                        map.putIfAbsent(key, value);
                    }
                } else {
                    // System.out.println("No Key:Value found in line, ignoring: " + line); // debug
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        File temp = new File(filePath);
        temp.delete(); // delete temporary File
        return map;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    public void initTempFiles() {
        inferenceTempFile = new File(c.getCacheDir(), "temp_sample_data.csv");
        if (!inferenceTempFile.exists()) {
            StringBuilder init = new StringBuilder("accx0,accy0,accz0");
            for (int i = 1; i < SAMPLE_SIZE; i++) {
                init.append(",accx").append(i).append(",accy").append(i).append(",accz").append(i);
            }
            try {
                FileWriter fw = new FileWriter(inferenceTempFile, true);
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
        writeOnFile(filteredData.toString() + "\n", inferenceTempFile);
        count = 0;
        rawData = new StringBuilder();
        filteredData = new StringBuilder();
    }
}
