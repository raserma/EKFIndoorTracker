package com.raulprojects.android.ekfindoortracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.qozix.tileview.TileView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main activity of CellIDIndoorTracker whose purpose is to create the basic system architecture
 * above where the different indoor position algorithms will be deployed. It is divided in two
 * main points:
 * -Showing and allowing the user to handle a first Tietotalo's floor indoor map.
 * -Scanning the available WiFi APs each X seconds in order to collect enough RSS data to
 * apply the necessary indoor techniques.
 *
 * In order to make an efficient app and don't block the UI whereas the scanning task is
 * happening and selected algorithm is being calculated, background threads are used. There are
 * three main threads:
 * -UI thread: shows and updates the map.
 * -Scanning thread: timer task which is scheduled to run every X seconds until the
 * activity is paused or destroyed.
 * -Algorithm thread: it collects data gathered by Scanning thread and uses it to apply the
 * algorithm.
 *
 *      -UI thread: shows and updates the map.
 *      -Scanning thread: timer task which is scheduled to run every X seconds until the
 *       activity is paused or destroyed.
 *      -Algorithm thread: it collects data gathered by Scanning thread and uses it to apply the
 *       algorithm.
 *
 *       UI (map) thread                  Scanning thread               Algorithm thread
 *              |                              |                               |
 *              |                              |startScan()     ---->          |
 *              |                              |           counting:1s         |receiveScanResults
 *              |                              |                               |applyAlgorithm
 *              |                              |           counting:2s         |
 *              |                              |                               |algorithm finished
 *              |                              |           counting:3s         |updateMap()
 *              |map updated                   |startScan()     ---->          |
 *              |                              |           counting:1s         |receiveScanResults
 *              |                              |                               |applyAlgorithm
 *             ...                            ...                             ...
 */

public class MapViewActivity extends Activity {
    public long start, end;
    private TileView mTileView;
    private ImageView mMarker;
    private Point mUserPosition;
    private WifiManager mWifi;
    private BroadcastReceiver mReceiver;
    private Timer mTimer;
    private boolean mIsActivityPaused = false;
    private boolean mIsScanned = false;
    private boolean mIsAlgorithmFinished = true;
    private int mNumberProcessingThreads = 0;
    private List<APAlgorithmData> mAlgorithmInputDataList;
    private Prefilter mFilter;
    private List<ScanResult> filteredResults;
    private LSAlgorithm mLSAlgorithm;
    private int mIterationK;
    private EKFAlgorithmData mEKFData;
    private int mIdBssidApSelected;
    public static final int UPDATE_MAP = 1;
    public static final int SCAN_INTERVAL = 2000; // 3 seconds
    public static final int SCAN_DELAY = 1000; // 1 second
    public static final int MAX_PROCESSING_THREADS = 1; // 1 thread
    /** UI Handler which updates map */
    Handler mUIHandler = new Handler (){
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case UPDATE_MAP:
                    // Gets Point message from ProcessResults thread
                    Point point = (Point) msg.obj;
                    // Updates map with user position
                    mTileView.removeMarker(mMarker);
                    // If less than 4 AP were acquired
                    if (point.x == -10){
                        // Toast
                        Toast.makeText(getBaseContext(), "Only "+ point.y +" APs acquired",
                                Toast.LENGTH_SHORT)
                                .show();
                    }else if(point.x < 0 || point.y < 0) { //Out of boundaries
                        // Toast
                        Toast.makeText(getBaseContext(), "Coordinates negative",
                                Toast.LENGTH_SHORT)
                                .show();
                    }else if(point.x > 80 || point.y > 150){ //Out of boundaries
                        // Toast
                        Toast.makeText(getBaseContext(), "Coordinates out of boundaries",
                                Toast.LENGTH_SHORT)
                                .show();
                    }
                    else{
                        //mTileView.moveToAndCenter(point.x, point.y);
                        mTileView.addMarker(mMarker, point.x, point.y, -0.5f, -1.0f);
                        Toast.makeText(getBaseContext(), "Correct scan", Toast.LENGTH_SHORT)
                                .show();
                    }
                    break;
                default:
                    break;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initiateMap();
        setScanningTask();
    }
    /**
     * CellIDIndoorTracker uses an external library called TileView to show the indoor map and
     * allow user to make basic movements on it. It is based on tiles which are stored in
     * /assets folder.
     * initiateMap() sets the basic configuration of this library.
     */
    private void initiateMap(){
        mTileView = new TileView(this);
        // Map image pixels
        mTileView.setSize(2332,5796);
        // mTileView.addDetailLevel(1f, "tilesfolder/1000_%col%_%row%.png",
        // "downsamplesfolder/map.png", 256, 256);
        // mTileView.addDetailLevel(0.5f, "tilesfolder/500_%col%_%row%.png",
        // "downsamplesfolder/map500.png", 256, 256);
        mTileView.addDetailLevel(0.25f, "tilesfolder/250_%col%_%row%.png",
                "map250.jpg", 256, 256);
        mTileView.addDetailLevel(0.125f, "tilesfolder/125_%col%_%row%.png",
                "map125.jpg", 256, 256);
        mTileView.setScale(0.25);
        setContentView(mTileView);
        /** Marker used in CellID indoor tracker */
        mMarker = new ImageView(MapViewActivity.this);
        mMarker.setImageResource(R.drawable.maps_marker_blue);
        mMarker.setAdjustViewBounds(true);
        // Define the bounds using the map coordinates: X: [0 - 60]; Y:[0 - 150]
        mTileView.defineRelativeBounds(0, 150, 60, 0);
    }
    /**
     * setScanningTask() is a Timer Task which is scheduled to run every SCAN_INTERVAL seconds.
     * Therefore, it creates a thread at the beginning of the cycle and runs repeatedly until
     * user or Android system pauses or destroys the application.
     */
    private void setScanningTask(){
        mIdBssidApSelected = 3; // By default, AP3 is chosen to provide pathloss model

        mWifi = (WifiManager) getSystemService(getApplicationContext().WIFI_SERVICE);
        mFilter = new Prefilter(this);
        mIterationK = 1;

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!mIsActivityPaused && mIsAlgorithmFinished) {
                    mIsScanned = false;
                    end = System.currentTimeMillis();
                    mWifi.startScan();
                }
            }
        }, SCAN_DELAY, SCAN_INTERVAL);
    }
    /**
     * processScanResults() should be triggered every time after startScan() is run in the Timer
     * task (hence, every SCAN_INTERVAL seconds). It runs in a separated thread.
     * Its function is gathering WiFi scanning results and using them to apply the indoor
     * algorithm. When it is finished, this method communicates with UI thread and updates user
     * position.
     *
     * NOTE: If necessary, we can deploy more than one thread for this method changing
     * mNumberProcessingThreads variable, in order to make a more efficient application. It
     * would execute at the same time the algorithm with different data, allowing to update the
     * map even faster, but would require better device performance.
     */
    public void processScanResults(final List<ScanResult> results) {
        if(results.size() > 0 && mNumberProcessingThreads <= MAX_PROCESSING_THREADS && !mIsScanned){
            /** Execute a new thread */
            Thread t = new Thread() {
                public void run() {
                    mNumberProcessingThreads++;
                    mIsScanned = true;
                    mIsAlgorithmFinished = false;

                    /** Extended Kalman Filter Algorithm  */
                    filteredResults = mFilter.filterEverythingOut(results);

                    // If more than 4 AP were acquired, apply EKF
                    if(filteredResults.size() >= 4){

                        /* Gets the 4 strongest RSS from 4 APs */
                        filteredResults = getStrongestRSSList(filteredResults);

                        /* Translates RSS to distance by using estimated pathloss model */
                        mAlgorithmInputDataList
                                = mFilter.translatesRSStoDistance (filteredResults, mIdBssidApSelected);

                        if (mIterationK == 1){ // First iteration uses WCLS algorithm

                            /* Initial guess using Weighted Circular Least Square algorithm */
                            Point initialGuess = mLSAlgorithm.applyWCLSAlgorithm
                                    (mAlgorithmInputDataList);
                            double[] x = new double[]{initialGuess.x, initialGuess.y};

                            /* Set covariance matrix P */
                            double[][] P = new double[][]{
                                    {10,0},
                                    {0, 10}
                            };

                            mEKFData = new EKFAlgorithmData(x, P);

                            /* Apply EKF algorithm with algorithmInputDataList and initial
                            guesses from WCLS algorithm */
                            mEKFData = mEKFData.applyEKFAlgorithm(mAlgorithmInputDataList,
                                    mEKFData);
                            mIterationK++;
                        }
                        else
                        {
                            /* Apply EKF algorithm with algorithmInputDataList and estimates from
                             previous iteration */
                            mEKFData = mEKFData.applyEKFAlgorithm(mAlgorithmInputDataList,
                                        mEKFData);
                            mIterationK++;
                        }

                        int coordinateX = (int) mEKFData.x.get(0);
                        int coordinateY = (int) mEKFData.x.get(1);

                        mUserPosition = new Point(coordinateX, coordinateY);

                    }
                    else // If less than 4 AP were acquired, return error code
                    {
                        mUserPosition = new Point (-10, filteredResults.size());
                    }

                    /* Call the UPDATE_MAP case method of UI Handler with user position on it */
                    Message msg = mUIHandler.obtainMessage(UPDATE_MAP, mUserPosition);
                    mUIHandler.sendMessage(msg);
                    mNumberProcessingThreads--;
                    mIsAlgorithmFinished = true;



                }
            };
            t.start(); // start new scan thread
        }
    }

    /**
     * Gets X strongest RSS list from X APs:
     *      Sorts ScanResult list in descending order by "level" field
     *      Gets the X first elements of the list by using subList
     * @param results WiFi scan results list with all the known AP data
     * @return WiFi scan results list with the X strongest APs
     */
    private List<ScanResult> getStrongestRSSList (List<ScanResult> results){

        /* Sorts the list by "level" field name */
        Collections.sort(results, new Comparator<ScanResult>() {
            public int compare(ScanResult one, ScanResult other) {
                int returnVal = 0;

                if (one.level > other.level) {
                    returnVal = -1;
                } else if (one.level < other.level) {
                    returnVal = 1;
                } else if (one.level == other.level) {
                    returnVal = 0;
                }
                return returnVal;

            }
        });

        /* Gets the X first elements of the list in a new list */
        int X = 4; // X = 4
        List<ScanResult> strongestResults = results.subList(0, X);

        return strongestResults;
    }


    /* Register Broadcast receiver */
    @Override
    protected void onStart(){
        super.onStart();
        // Registers the BroadCast receiver
        mReceiver = new BroadcastReceiver ()
        {
            @Override
            public void onReceive(Context c, Intent intent)
            {
                if(!mIsScanned)
                    start = System.currentTimeMillis();
                processScanResults(mWifi.getScanResults());
            }
        };
        registerReceiver(mReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    }
    /* Unregister Broadcast receiver */
    @Override
    protected void onStop(){
        unregisterReceiver(mReceiver);
        super.onStop();
    }
    /* Activity is paused */
    @Override
    protected void onPause(){
        super.onPause();
        mIsActivityPaused = true;
    }
    /* Activity is resumed */
    @Override
    protected void onResume(){
        super.onResume();
        mIsActivityPaused = false;
    }
    /* Activity is destroyed */
    @Override
    protected void onDestroy(){
        super.onDestroy();
        mTimer.cancel();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_map_view, menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_database:
                // Settings option clicked.
                Intent dbmanager = new Intent(this,AndroidDatabaseManager.class);
                startActivity(dbmanager);
                return true;
            case R.id.action_pathloss_model:
                dialogBssid();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Asks for desired AP which will provide pathloss model */
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
}