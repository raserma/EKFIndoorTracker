package com.raulprojects.android.ekfindoortracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;


public class PathLossEstimationStartActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_path_loss_estimation_start);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_path_loss_estimation_start, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void openDataMeasuringActivity(View view){
        Intent intentMeasurementScreen = new Intent(this, MeasuringDataActivity.class);
        startActivity(intentMeasurementScreen);
    }
    public void openApplyingRegressionActivity(View view){
        Intent intentEstimationScreen = new Intent(this, ApplyingRegressionActivity.class);
        startActivity(intentEstimationScreen);
    }
}
