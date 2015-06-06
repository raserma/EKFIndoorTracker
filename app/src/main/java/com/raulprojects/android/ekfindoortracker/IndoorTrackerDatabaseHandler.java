package com.raulprojects.android.ekfindoortracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Point;
import android.net.wifi.ScanResult;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Database Handler for app. There are three main tables:
 *      + BSSIDs table
 *      + MEASUREMENTS table
 *      + COEFFICIENTS table
 *
 * Database can be viewed in real time in the own app by using AndroidDatabaseManager class
 */
public class IndoorTrackerDatabaseHandler extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "accesspointData";
    private static final String TABLE_BSSIDS = "bssids";
    private static final String TABLE_MEASUREMENTS = "measurements";
    private static final String TABLE_COEFFICIENTS = "coefficients";


    // bssids table columns names
    private static final String KEY_BSSID_ID = "id";
    private static final String KEY_BSSID_NAME = "name";
    private static final String KEY_BSSID_POS_X = "pos_x";
    private static final String KEY_BSSID_POS_Y = "pos_y";

    // measurements table column names
    private static final String KEY_MEASUREMENT_ID = "id";
    private static final String KEY_BSSID = "id_bssid";
    private static final String KEY_RSS = "value_rss";
    private static final String KEY_DISTANCE = "value_distance";

    // coefficients table column names
    private static final String KEY_COEFFICIENT_ID = "id";
    //private static final String KEY_BSSID = "id_bssid";
    private static final String KEY_COEFFICIENT_VALUE = "value_coefficient";


    // AP MAC ADDRESS LIST
    private static final String BSSID1 = "00:17:0f:d9:71:d";
    private static final String BSSID2 = "00:17:0f:d9:6c:8";
    private static final String BSSID3 = "00:17:0f:d9:6f:d";
    private static final String BSSID4 = "f4:7f:35:f6:ab:a";
    private static final String BSSID5 = "18:33:9d:fe:9c:6";
    private static final String BSSID6 = "18:33:9d:fe:91:c";
    private static final String BSSID7 = "18:33:9d:f9:31:8";
    private static final String BSSID8 = "18:33:9d:f9:84:7";
    private static final String BSSID9 = "04:da:d2:a7:2f:c";
    private static final String BSSID10 = "04:da:d2:29:bf:8";
    private static final String BSSID11 = "04:da:d2:57:0a:3";
    private static final String BSSID12 = "04:da:d2:29:c4:c";
    private static final String BSSID13 = "04:da:d2:57:0a:3";
    private static final String BSSID14 = "04:da:d2:56:ee:e";
    private static final String BSSID15 = "04:da:d2:29:c2:3";
    private static final String BSSID16 = "04:da:d2:57:0d:a";
    private static final String BSSID17 = "04:da:d2:29:b4:0";
    private static final String BSSID18 = "04:da:d2:57:0e:5";

    /**
     * CONSTRUCTOR
     */
    public IndoorTrackerDatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * INSTANCE METHODS
     */

    /** Only runs once, when there is no database in the system */
    public void onCreate(SQLiteDatabase db) {

        /** Create bssids table */
        String CREATE_BSSID_TABLE = "CREATE TABLE " + TABLE_BSSIDS + "("
                + KEY_BSSID_ID + " INTEGER PRIMARY KEY,"
                + KEY_BSSID_NAME + " TEXT, "
                + KEY_BSSID_POS_X + " INTEGER, "
                + KEY_BSSID_POS_Y + " INTEGER" + ")";
        db.execSQL(CREATE_BSSID_TABLE);
        // fills bssids table
        String [] bssids = {
                BSSID1, BSSID2, BSSID3, BSSID4, BSSID5, BSSID6, BSSID7, BSSID8, BSSID9, BSSID10,
                BSSID11, BSSID12, BSSID13, BSSID14, BSSID15, BSSID16, BSSID17, BSSID18
        };
        int [] bssidXPositions = {
                44, 29, 46, 39, 50, 40, 51, 40, 32, 19,
                15, 17, 8, 15, 17, 8, 13, 6
        };
        int [] bssidYPositions = {
                11, 28, 40, 61, 72, 100, 102, 115, 138, 146,
                123, 108, 108, 100, 83, 83, 70, 37
        };
        // Fill static table with AP bssids and their positions
        ContentValues bssidValues = new ContentValues();
        for (int i = 0; i < bssids.length; i++) {
            bssidValues.put(KEY_BSSID_NAME, bssids[i]);
            bssidValues.put(KEY_BSSID_POS_X, bssidXPositions[i]);
            bssidValues.put(KEY_BSSID_POS_Y, bssidYPositions[i]);
            db.insert(TABLE_BSSIDS, null, bssidValues);
        }

        /** Create measurements table */
        String CREATE_MEASUREMENTS_TABLE = "CREATE TABLE " + TABLE_MEASUREMENTS + "("
                + KEY_MEASUREMENT_ID + " INTEGER PRIMARY KEY,"
                + KEY_BSSID + " INTEGER,"
                + KEY_RSS + " INTEGER,"
                + KEY_DISTANCE + " INTEGER" + ")";
        db.execSQL(CREATE_MEASUREMENTS_TABLE);

        /** Create coefficients table */
        String CREATE_COEFFICIENTS_TABLE = "CREATE TABLE " + TABLE_COEFFICIENTS + "("
                + KEY_COEFFICIENT_ID + " INTEGER PRIMARY KEY,"
                + KEY_BSSID + " INTEGER,"
                + KEY_COEFFICIENT_VALUE + " DOUBLE" + ")";
        db.execSQL(CREATE_COEFFICIENTS_TABLE);
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BSSIDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEASUREMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_COEFFICIENTS);
        // Create tables again
        onCreate(db);
    }

    /**
     * INDOOR TRACKER HANDLER METHODS
     */

    /**
     * Compares WiFi AP scan results with APs stored in database and remove those that are not in
     * it.
     * @param results WiFi scan results list with all the AP data scanned in MapViewActivity
     * @return WiFi scan results list with AP data already filtered.
     */
    public List<ScanResult> filterKnownAPDB (List<ScanResult> results){
        /* Sort Scan results list by BSSID */

        Collections.sort(results, new Comparator<ScanResult>() {
            public int compare(ScanResult one, ScanResult other) {
                return one.BSSID.compareTo(other.BSSID);
            }
        });

        SQLiteDatabase db = this.getReadableDatabase();
        // Select all BSSIDs from bssids table
        String selectQuery = "SELECT * FROM " + TABLE_BSSIDS;
        Cursor cursor = db.rawQuery(selectQuery, null);

        boolean apIsKnown;

        List<ScanResult> toRemove = new ArrayList<ScanResult>();
        /* Compares both lists element by element. If an item is not in known AP table,
        is inserted into toRemove list in order to remove it at the end */
        for (int j = 0; j < results.size(); j++) { // Scan results list
            apIsKnown = false;
            if (cursor.moveToFirst()) {
                for (int i = 0; i < cursor.getCount(); i++) { // Known APs in database
                    if (removeLastDigitBssid(results.get(j).BSSID).equals(cursor.getString(1))) {
                        apIsKnown = true;
                        // Overwrite with common BSSID name (bssid without last digit)
                        results.get(j).BSSID = cursor.getString(1);
                    }
                    cursor.moveToNext();
                }
                // Remove AP from list
                if (!apIsKnown)
                    toRemove.add(results.get(j));
            }
        }
        // Removes all items stored into toRemove list from results list
        results.removeAll(toRemove);
        db.close();
        return results;
    }

    /**
     * Gets AP position from bssids table
     * @param bssid AP BSSID of interest
     * @return Point object with AP position
     */
    public Point getAPPositionDB (String bssid) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BSSIDS, new String[]{KEY_BSSID_POS_X, KEY_BSSID_POS_Y},
                KEY_BSSID_NAME + "=?",
                new String[]{bssid}, null, null, null, null);
        int x = 0, y = 0;
        if (cursor.moveToFirst()) {
            x = Integer.parseInt(cursor.getString(0));
            y = Integer.parseInt(cursor.getString(1));
        }
        db.close();
        return new Point (x, y);
    }

    /**
     * Gets coefficients [a b c d] from estimated pathloss model of BSSID selected
     * @param idBssidApSelected BSSID selected
     * @return coefficients [a b c d]
     */

    public double[] getCoefficientsDB (int idBssidApSelected){
        SQLiteDatabase db = this.getReadableDatabase();
        double[] coefficients = new double[4];
        Cursor cursor = db.query(TABLE_COEFFICIENTS, new String[]{KEY_COEFFICIENT_VALUE},
                KEY_BSSID + "=?",
                new String[]{String.valueOf(idBssidApSelected)}, null, null, null, null);
        // There is at least one register
        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getCount(); i++) {
                coefficients[i] = cursor.getDouble(0);
                cursor.moveToNext();
            }
        }
        db.close();

        return coefficients;

    }
    private String removeLastDigitBssid (String bssid){
        if (bssid.length() > 0) {
            bssid = bssid.substring(0, bssid.length()-1);
        }
        return bssid;
    }

    /**
     * PATH LOSS ESTIMATION HANDLER METHODS
     */

    /**
     * getBssidNameDB (int id_BSSID)
     * Gets MAC/BSSID address which corresponds to id_BSSID
     *
     * @param id_BSSID Identification number of currently used AP MAC/BSSID
     */
    public String getBssidNameDB(int id_BSSID) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_BSSIDS, new String[]{KEY_BSSID_NAME}, KEY_BSSID_ID + "=?",
                new String[]{String.valueOf(id_BSSID)}, null, null, null, null);
        String bssid = "";
        if (cursor.moveToFirst()) {
            bssid = cursor.getString(0);
        }
        db.close();
        return bssid;
    }

    /**
     * addMeasurementDB (int id_BSSID, int value_RSS, int value_distance)
     * <p/>
     * Adds measurement sets to "measurements" table
     *
     * @param id_BSSID       Identification number of currently used AP MAC/BSSID
     * @param value_RSS      RSS value gathered from AP for a determined value_distance
     * @param value_distance meters away from where value_RSS was measured
     */
    public void addMeasurementDB(int id_BSSID, int value_RSS, int value_distance) {
        // If id_BSSID is already in table, update measurements
        // If id_BSSID is not in table, insert measurements
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues measurementValues = new ContentValues();
        measurementValues.put(KEY_BSSID, id_BSSID);
        measurementValues.put(KEY_RSS, value_RSS);
        measurementValues.put(KEY_DISTANCE, value_distance);
        // insert AP measurements into measurements table
        db.insert(TABLE_MEASUREMENTS, null, measurementValues);
        db.close();
    }

    /**
     * Reads the RSS array which corresponds to id_BSSID from measurements table
     *
     * @param id_BSSID Identification number of currently used AP MAC/BSSID
     * @return rssArray
     */
    public double[] getRSSValuesDB(int id_BSSID) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MEASUREMENTS, new String[]{KEY_RSS},
                KEY_BSSID + "=?",
                new String[]{String.valueOf(id_BSSID)}, null, null, null, null);
        double[] rssArray = new double[cursor.getCount()];
        // There is at least one register
        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getCount(); i++) {
                rssArray[i] = cursor.getDouble(0);
                cursor.moveToNext();
            }
        }
        db.close();
        return rssArray;
    }

    /**
     * Reads the distance array which corresponds to id_BSSID from measurements table
     *
     * @param id_BSSID Identification number of currently used AP MAC/BSSID
     * @return distanceArray
     */
    public double[] getDistanceValuesDB(int id_BSSID) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_MEASUREMENTS, new String[]{KEY_DISTANCE},
                KEY_BSSID + "=?",
                new String[]{String.valueOf(id_BSSID)}, null, null, null, null);
        double[] distanceArray = new double[cursor.getCount()];
        // There is at least one register
        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getCount(); i++) {
                distanceArray[i] = cursor.getDouble(0);
                cursor.moveToNext();
            }
        }
        db.close();
        return distanceArray;
    }

    /**
     * Adds coefficients to database. They are stored in [a b c d] order
     * @param id_BSSID Identification number of currently used AP MAC/BSSID
     * @param coefficients coefficients [a b c d]
     */
    public void addCoefficientsDB(int id_BSSID, double[] coefficients) {
        // NEEDS TO BE DONE:
        //      If id_BSSID is already in table, update coefficients
        //      If id_BSSID is not in table, insert coefficients
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues coefficientValues = new ContentValues();
        coefficientValues.put(KEY_BSSID, id_BSSID);
        coefficientValues.put(KEY_COEFFICIENT_VALUE, coefficients[0]);
        db.insert(TABLE_COEFFICIENTS, null, coefficientValues);
        coefficientValues.put(KEY_BSSID, id_BSSID);
        coefficientValues.put(KEY_COEFFICIENT_VALUE, coefficients[1]);
        db.insert(TABLE_COEFFICIENTS, null, coefficientValues);
        coefficientValues.put(KEY_BSSID, id_BSSID);
        coefficientValues.put(KEY_COEFFICIENT_VALUE, coefficients[2]);
        db.insert(TABLE_COEFFICIENTS, null, coefficientValues);
        coefficientValues.put(KEY_BSSID, id_BSSID);
        coefficientValues.put(KEY_COEFFICIENT_VALUE, coefficients[3]);
        db.insert(TABLE_COEFFICIENTS, null, coefficientValues);
        db.close();
    }


    /** Used to check database, help method of AndroidDatabaseManager activity */
    public ArrayList<Cursor> getData(String Query) {
        //get writable database
        SQLiteDatabase sqlDB = this.getWritableDatabase();
        String[] columns = new String[]{"mesage"};
        //an array list of cursor to save two cursors one has results from the query
        //other cursor stores error message if any errors are triggered
        ArrayList<Cursor> alc = new ArrayList<Cursor>(2);
        MatrixCursor Cursor2 = new MatrixCursor(columns);
        alc.add(null);
        alc.add(null);
        try {
            String maxQuery = Query;
            //execute the query results will be save in Cursor c
            Cursor c = sqlDB.rawQuery(maxQuery, null);
            //add value to cursor2
            Cursor2.addRow(new Object[]{"Success"});
            alc.set(1, Cursor2);
            if (null != c && c.getCount() > 0) {
                alc.set(0, c);
                c.moveToFirst();
                return alc;
            }
            return alc;
        }
        catch (Exception ex) {
            Log.d("printing exception", ex.getMessage());
            //if any exceptions are triggered save the error message to cursor an return the arraylist
            Cursor2.addRow(new Object[]{"" + ex.getMessage()});
            alc.set(1, Cursor2);
            return alc;
        }
    }
}
