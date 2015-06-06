package com.raulprojects.android.ekfindoortracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;


public class MainMenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                // Settings option clicked.
                Intent dbmanager = new Intent(this,AndroidDatabaseManager.class);
                startActivity(dbmanager);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /* Button PL Estimation Click Listener */
    public void estimatePathLoss(View view){
        Intent intentPathLossEstimation = new Intent(this, PathLossEstimationStartActivity.class);
        startActivity(intentPathLossEstimation);
    }

    /* Button Least Square Tracker Click Listener */
    public void initiateLSTracker(View view){
        Intent intentLSIndoorTracker = new Intent(this, MapViewActivity.class);
        startActivity(intentLSIndoorTracker);

    }

    /* Button Non-Linear LS Tracker Click Listener */
    public void initiateNLLSTracker(View view){

    }

}
