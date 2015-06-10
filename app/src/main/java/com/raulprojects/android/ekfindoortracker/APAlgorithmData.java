package com.raulprojects.android.ekfindoortracker;

import android.graphics.Point;

/**
 * Algorithm data needed to face Least Square algorithm stage.
 */
public class APAlgorithmData {
    public String bssid;
    public double distance;
    public int RSS;
    public Point coordinatesAP;


    public APAlgorithmData(String bssid, double distance, int RSS, Point coordinatesAP){
        this.bssid = bssid;
        this.distance = distance;
        this.RSS = RSS;
        this.coordinatesAP = coordinatesAP;
    }

}
