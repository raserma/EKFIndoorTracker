package com.raulprojects.android.ekfindoortracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;


public class MeasuringDataActivity extends Activity {

    Button buttonStart;
    TextView text_bssid;
    TextView text_dBm;
    TextView text_iteration;
    TextView text_meters;
    IndoorTrackerDatabaseHandler measdbh;
    private int mIdBssidApSelected;
    private String mNameBssidApSelected;
    private int mRssTotal;
    private int mIterations;
    // mRssMean = mRssTotal/mIterations
    private int mRssMean;
    private int mMetersAway;
    WifiManager mMainWifi;
    WifiReceiver mReceiverWifi;
    List<ScanResult> mWifiList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measuring_data);
        initiateAndroid();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_measuring_data, menu);
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
    private void initiateAndroid(){
        // User Interface
        buttonStart = (Button)findViewById(R.id.button_start);
        text_bssid = (TextView) findViewById(R.id.text_bssid);
        text_dBm = (TextView) findViewById(R.id.text_dBm_variation);
        text_iteration = (TextView) findViewById(R.id.text_iterations_variation);
        text_meters = (TextView) findViewById(R.id.text_meters_variation);
        // Initiate variables
        mRssTotal = 0;
        mIterations = 0;
        mRssMean = 0;
        mMetersAway = 0;
        // Creation of MAC/BSSID database
        measdbh = new IndoorTrackerDatabaseHandler(this);
        // WiFi Manager API
        mMainWifi =(WifiManager) getSystemService(Context.WIFI_SERVICE);
        mReceiverWifi = new WifiReceiver();
        // Dialog for introducing desired AP MAC address
        dialogBssid();
    }
    /** Asks for the selected AP from where user will estimate PathLoss */
    private void dialogBssid() {
        // Set up the input
        final EditText input = new EditText(this);
        // Specify the type of input expected; this, for example, sets the input as a number
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_bssid)
                .setView(input)
                // Set up the buttons
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIdBssidApSelected = Integer.parseInt(input.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        // code = 0 -> error
                        mIdBssidApSelected = 0;
                    }
                })
                .show();
    }
    /**
     * onClick() method:
     * Starts scanning WiFi APs and collecting RSS and distance when user presses "Start" button.
     * @param view View
     */
    public void nextMeasurement(View view){
//        Intent dbmanager = new Intent(this,AndroidDatabaseManager.class);
//        startActivity(dbmanager);
        mNameBssidApSelected = measdbh.getBssidNameDB(mIdBssidApSelected);
        // If "start", means used has moved away 1m
        if (buttonStart.getText().equals(this.getString(R.string.button_start))){
            mRssTotal = 0;
            mMetersAway++;
            mIterations = 0;
            buttonStart.setText(R.string.button_next);
        }

        // WiFi Scanner
        registerReceiver(mReceiverWifi, new
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        mMainWifi.startScan();
    }
    /**
     * onClick() method:
     * RSS-distance value is inserted into the database Measurements
     * @param view View
     */
    public void stopMeasurements(View view){
        // Changes button text
        buttonStart.setText(R.string.button_start);
        try{
            mRssMean = mRssTotal/mIterations;
        }catch(ArithmeticException e){
            mRssMean = 0;
        }
        Toast.makeText(getApplicationContext(), mRssMean + " dBms",
                Toast.LENGTH_LONG).show();
        // Store the pair value RSS-distance of selected AP to database
        measdbh.addMeasurementDB(mIdBssidApSelected, mRssMean, mMetersAway);
    }
    class WifiReceiver extends BroadcastReceiver {
        int rss = -100;
        String bssid;
        Boolean succeedScanning;
        public void onReceive(Context c, Intent intent) {
            succeedScanning = false;
            // Returns a list of scanned APs by mobile phone
            mWifiList = mMainWifi.getScanResults();
            // Searches for the BSSID/MAC address selected by the user in dialog
            for(int i = 0; i < mWifiList.size(); i++){
                bssid = mWifiList.get(i).BSSID;
                // Collects RSS level if finds BSSID/MAC address user input in the list
                if (removeLastDigitBssid(bssid).equals(mNameBssidApSelected)) {
                    rss = mWifiList.get(i).level;
                    mRssTotal += rss;
                    mIterations++;
                    i = mWifiList.size();
                    succeedScanning = true;
                }
            }
            unregisterReceiver(mReceiverWifi);
            // Update interface
            updateInterface(mIdBssidApSelected, rss, mIterations, mMetersAway);
        }
    }
    /** Updates interface each time user presses "next" measurement */
    private void updateInterface(int id_bssid, int rss, int iterations, int metersAway){
        if(rss ==-100)
            Toast.makeText(getApplicationContext(), "AP selected not scanned",
                    Toast.LENGTH_SHORT).show();
        text_bssid.setText(Integer.toString(id_bssid));
        text_dBm.setText(Integer.toString(rss));
        text_iteration.setText(Integer.toString(iterations));
        text_meters.setText(Integer.toString(metersAway));
    }
    private String removeLastDigitBssid (String bssid){
        if (bssid.length() > 0) {
            bssid = bssid.substring(0, bssid.length()-1);
        }
        return bssid;
    }
}
