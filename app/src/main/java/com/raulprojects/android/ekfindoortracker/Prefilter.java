package com.raulprojects.android.ekfindoortracker;

import android.content.Context;
import android.graphics.Point;
import android.net.wifi.ScanResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler class used to filter not necessary WiFi scan results
 */
public class Prefilter {
    public Context mapViewActivityContext;

    public Prefilter(Context context){
        this.mapViewActivityContext = context;
    }

    /**
     *
     * @param results WiFi scan results list with the X strongest RSS from X APs
     * @return WiFi scan results list with all the known AP data and without duplicate BSSIDs
     */
    public List<ScanResult> filterEverythingOut (List<ScanResult> results){
        IndoorTrackerDatabaseHandler apdbhandler =  new IndoorTrackerDatabaseHandler
                (mapViewActivityContext);
        /* Filters known APs from database */
        results = apdbhandler.filterKnownAPDB (results);

        /* TUT WLAN network has 4 SSIDs for each AP. Therefore, only one must be chosen,
        filtering out the rest */
        results = filterSSIDs (results);

        return results;

    }

    /**
     * Translates RSS to distance by using estimated pathloss model and stores it onto a new List
     * of APAlgorithmData.
     * @param results WiFi scan results list with the X strongest RSS from X APs
     * @param idBssidApSelected BSSID selected
     * @return List of APAlgorithmData objects with the 4 AP data (BSSID - estimated distance -
     * RSS)
     */
    public List<APAlgorithmData> translatesRSStoDistance  (List<ScanResult> results,
                                                            int idBssidApSelected){

        /* Gets pathloss model coefficients from Database */
        IndoorTrackerDatabaseHandler itdbh = new IndoorTrackerDatabaseHandler
                (mapViewActivityContext);
        double[] coefficients = itdbh.getCoefficientsDB(idBssidApSelected);

        /* Converts RSS to distance by applying these coefficients */
        String BSSID; double estimatedDistance; int RSS; Point coordinatesAP;
        List<APAlgorithmData> algorithmInputDataList = new ArrayList<APAlgorithmData>();
        for (int i = 0; i < results.size(); i++){
            BSSID = results.get(i).BSSID;
            coordinatesAP = itdbh.getAPPositionDB(BSSID);
            RSS = results.get(i).level;

            /* Empirical pathloss model: d = a + b*RSS + c*RSS² + d*RSS³ */
            estimatedDistance = coefficients[0] + coefficients[1]*RSS + coefficients[2]*Math.pow
                    (RSS, 2) + coefficients[3]*Math.pow(RSS, 3);

            algorithmInputDataList.add(new APAlgorithmData(BSSID, estimatedDistance, RSS,
                    coordinatesAP));
        }
        return algorithmInputDataList;
    }


    /**
     * Filters duplicate BSSIDs out caused by different TUT SSIDs.
     * NOTE: TUT WLAN network provides four BSSIDs (MACs) for each AP, one for each SSID: TUT,
     * TUT-WPA, LANGATON-WPA and eduroam. Since these four BSSIDs represent the same position and
     * same power level (this is not actually true), only one must prevail.
     *
     * @param results WiFi scan results list with all the known AP data
     * @return WiFi scan results list with all the known AP data and without duplicate BSSIDs
     */
    private List<ScanResult> filterSSIDs (List<ScanResult> results){
        // It adds the contents of wifi scan results to a Map which will not allow duplicates and
        // then add the Map back to the wifi scan List.
        Map<String, ScanResult> map = new LinkedHashMap<String, ScanResult>();
        for (ScanResult ays : results) {
            map.put(ays.BSSID.toString(), ays);
        }
        results.clear();
        results.addAll(map.values());
        return results;
    }

}
