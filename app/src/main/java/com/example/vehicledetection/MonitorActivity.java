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
    private static final int RECORDS_SEC = 40;
    private static final int BUS = 0;
    private static final int CAR = 1;
    private static final int MOTO = 2;
    private static final int WALK = 3;
    private static final int TRAIN = 4;

    private Sensor sAcceleration;
    private SensorManager sm;
    private boolean monitoringStatus = false;
    private Context c;
    private File inferenceTempFile;
    private StringBuilder rawData, filteredData;
    private XYPlot plot;

    // Monitoring results
    private int busValue = 0;
    private int carValue = 0;
    private int motoValue = 0;
    private int walkValue = 0;
    private int trainValue = 0;
    private int count = 0;
    private int monitoringCounter = 0;
    private Evaluator evaluator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        setSensors();
        c = this.getBaseContext();
        initPlot();
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
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            if (count == 0) {rawData.append(x).append(",").append(y).append(",").append(z);}
            else {rawData.append(",").append(x).append(",").append(y).append(",").append(z);}
            count++;
            // System.arraycopy(event.values, 0, rawAccData, rawAccDataIdx, 3);
            // applyLPF();
            // filteredData.append(",").append(lpfPrevData[0]).append(",").append(lpfPrevData[1]).append(",").append(lpfPrevData[2]);
            if (count == SAMPLE_SIZE) {
                writeOnFile("\n" + rawData.toString(), inferenceTempFile);
                Log.i("Save Window"+monitoringCounter, "");
                count = 0;
                rawData = new StringBuilder();
                filteredData = new StringBuilder();
                monitoringCounter++;
            }
        }

        if (monitoringCounter == MONITORING_REPETITIONS){
            returnInference(inferenceTempFile.getAbsolutePath());
            stopMonitoring();
        }
    }

    private void startMonitoring() {
        Chronometer timeRecorded = findViewById(R.id.monitoringChronometer);
        initTempFiles();
        sm.registerListener(this, sAcceleration, SensorManager.SENSOR_DELAY_GAME); // SENSOR_DELAY_GAME: 20ms sample interval
        monitoringStatus = true;
        filteredData = new StringBuilder();
        rawData = new StringBuilder();
        timeRecorded.setBase(SystemClock.elapsedRealtime());
        timeRecorded.start();
        count=0;
        busValue = 0;
        carValue = 0;
        motoValue = 0;
        walkValue = 0;
        trainValue = 0;
    }

    private void stopMonitoring() {
        Chronometer focus = findViewById(R.id.monitoringChronometer);
        sm.unregisterListener(this, sAcceleration);
        monitoringStatus = false;
        focus.setBase(SystemClock.elapsedRealtime());
        focus.stop();
        inferenceTempFile.delete();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) { }

    public void setSensors() {
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sAcceleration = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    public void initTempFiles() {
        inferenceTempFile = new File(c.getFilesDir(), "temp_sample_data.csv");
        Log.i("path", c.getFilesDir().toString());
        // filteredDataFile = new File(c.getFilesDir(), "data_collection_filtered_" + this.overlapProgress + ".csv");
        // TODO also check other files
        if (!inferenceTempFile.exists()) {
            Log.i("create file", c.getFilesDir().toString());
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

    private void returnInference(String dataFilePath) {
        int result = 10; // not recognizable value
        /** Read arguments from File and execute the model **/
        HashMap<String, String> arguments = new HashMap<>(); // create a map for the argument values
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFilePath))) {
            line = reader.readLine();
            String[] keys = line.split(",", SAMPLE_SIZE*3);
            Log.i("keys: ", line + "\n");
            for (int n=0 ; n<MONITORING_REPETITIONS; n++) { // iterate over number of monitoring windows captured (equal to the number of lines)
                if ((line = reader.readLine()) != null) {
                    String[] values = line.split(",", SAMPLE_SIZE*3);
                    /** Write sample data in the map **/
                    if (values.length > 1 && keys.length > 1){
                        for (int i=0; i<SAMPLE_SIZE*3; i++){
                            arguments.put(keys[i], values[i]);
                        }
                        /** get Inference value from the model **/
                        Log.i("Arguments Map: ", arguments.toString());
                        Map<String, ?> results = evaluator.evaluate(arguments);
                        results = EvaluatorUtil.decodeAll(results);
                        Log.i("RESULTS: ", results.toString());
                        arguments.clear();
                        result = (int) results.get("y");
                        if (result == 0){ busValue++; }
                        else if (result == 1){ carValue++; }
                        else if (result == 2){ motoValue++; }
                        else if (result == 3){ walkValue++; }
                        else if (result == 4){ trainValue++; }
                        else {Log.i("error: ", "model result not listed\n");}
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.d("Fail to Read file in inference map", "");
        }
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

}