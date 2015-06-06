package com.raulprojects.android.ekfindoortracker;

import android.content.Context;
import android.graphics.Point;
import android.net.wifi.ScanResult;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.ops.CommonOps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *  Algorithm class which will estimate user position in several steps:
 *      1) Filters APs which are not in database out
 *      2) Filters repetitive SSIDs out
 *      3) If resulted list is less than 4 APs, algorithm won't be able to estimate user position
 *      4) Gets the 4 strongest RSSs from 4 different APs
 *      5) Translates previous strongest RSSs to distances by using estimated PL model
 *      6) Applies different least square approaches depending on user choice.
 *
 *  For a more detailed explanation on how these algorithms have been derived and deployed,
 *  please refer to Msc Thesis.
 *
 *  NOTE: A Java matrix library will be used to handle matrix operations in an efficient way.
 *  EJML has been chosen because its good performance showed at Java Matrix Benchmark
 *  (https://code.google.com/p/java-matrix-benchmark/)
 */
public class LSAlgorithm {
    public Context mapViewActivityContext;

    public LSAlgorithm(Context context){
        this.mapViewActivityContext = context;
    }

    /**
     * Main method which finds user position based on WiFi scan results.
     * @param results WiFi scan results list with all the known AP data
     * @return Point object with AP position
     *         Point(-10, results.size()) if acquired APs < required AP for LS
     */
    public Point getUserPosition(List<ScanResult> results, int idBssidApSelected, int
            posAlgSelected){
        IndoorTrackerDatabaseHandler apdbhandler =  new IndoorTrackerDatabaseHandler
                (mapViewActivityContext);

        /* Filters known APs from database */
        results = apdbhandler.filterKnownAPDB (results);

        /* TUT WLAN network has 4 SSIDs for each AP. Therefore, only one must be chosen,
        filtering out the rest */
        results = filterSSIDs (results);

        // If less than 4 AP were acquired
        if(results.size() < 4)
            return new Point (-10, results.size());

        else {

            /* Gets the X strongest RSS from X APs */
            results = getStrongestRSSList(results);

            /* Translates RSS to distance by using estimated pathloss model */
            List<APAlgorithmData> algorithmInputDataList
                    = translatesRSStoDistance (results, idBssidApSelected);

            /* Depending on user selection, positioning will be calculated by different approaches*/
            Point userPosition = new Point(0, 0);
            switch(posAlgSelected) {
                case 0: // Hyperbolic algorithm
                    userPosition = hyperbolicAlgorithm (algorithmInputDataList);
                    break;
                case 1: // Weighted Hyperbolic algorithm
                    userPosition = weightedHyperbolicAlgorithm(algorithmInputDataList);
                    break;
                case 2: // Circular algorithm
                    userPosition = circularAlgorithm(algorithmInputDataList);
                    break;
                case 3: // Weighted Circular algorithm
                    userPosition = weightedCircularAlgorithm(algorithmInputDataList);
                    break;
            }
            return userPosition;
        }
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

                if(one.level > other.level){
                    returnVal =  -1;
                }else if(one.level < other.level){
                    returnVal =  1;
                }else if(one.level == other.level){
                    returnVal =  0;
                }
                return returnVal;

            }
        });

        /* Gets the X first elements of the list in a new list */
        int X = 4; // X = 4
        List<ScanResult> strongestResults = results.subList(0, X);

        return strongestResults;
    }

    /**
     * Translates RSS to distance by using estimated pathloss model and stores it onto a new List
     * of APAlgorithmData.
     * @param results WiFi scan results list with the X strongest RSS from X APs
     * @param idBssidApSelected BSSID selected
     * @return List of APAlgorithmData objects with the 4 AP data (BSSID - estimated distance -
     * RSS)
     */
    private List<APAlgorithmData> translatesRSStoDistance  (List<ScanResult> results,
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



    /** User position can be acquired by using different approaches:
     *      + Hyperbolic algorithm:
     *          - "Radio Tracking of Open Range Sheep" with BS1 as fixed BS
     *      + Weighted Hyperbolic algorithm:
     *          - "Weighted Least Square Techniques for Improved RSS based location" with BS1 as
     *          fixed BS
     *      + Circular algorithm:
     *          - "Radio Tracking of ORS" with BS1 as fixed BS
     *      + Weighted Circular algorithm:
     *          - "Weighted Least Square Techniques for Improved RSS based location" with BS1 as
     *          fixed BS
     */

    /**
     * Estimates user position by applying an hyperbolic algorithm based on linearisation and Least
     * Square approach:
     *
     *      From "Radio Tracking of ORS" with random BS as fixed BS.
     *
     *      A = - [(x2-x1) (y2-y1) r2,1;
     *             (x3-x1) (y3-y1) r3,1;
     *             (x4-x1) (y4-y1) r4,1]
     *
     *      b = 1/2* [r2,1^2-K2-K1;
     *                r3,1^2-K3-K1;
     *                r4.1^2-K4-K1]
     *
     *      x = (A'A)^-1*A'*b
     *
     *      x = [x; y; r1]
     *
     * @param algInputList List of APAlgorithmData objects with the 4 AP data (BSSID - estimated
     *                     distance - RSS)
     * @return User position estimated
     */
    private Point hyperbolicAlgorithm(List<APAlgorithmData> algInputList) {
        /* Gather all the collected data: AP coordinates and distances */
        Point coordAP1 = algInputList.get(0).coordinatesAP;
        Point coordAP2 = algInputList.get(1).coordinatesAP;
        Point coordAP3 = algInputList.get(2).coordinatesAP;
        Point coordAP4 = algInputList.get(3).coordinatesAP;

        double distAP1 = algInputList.get(0).distance;
        double distAP2 = algInputList.get(1).distance;
        double distAP3 = algInputList.get(2).distance;
        double distAP4 = algInputList.get(3).distance;

        // ri_j = ri - rj
        double r2_1 = distAP2 - distAP1;
        double r3_1 = distAP3 - distAP1;
        double r4_1 = distAP4 - distAP1;

        // Ki = xi^2 + yi^2
        double K1 = Math.pow(coordAP1.x, 2) + Math.pow(coordAP1.y, 2);
        double K2 = Math.pow(coordAP2.x, 2) + Math.pow(coordAP2.y, 2);
        double K3 = Math.pow(coordAP3.x, 2) + Math.pow(coordAP3.y, 2);
        double K4 = Math.pow(coordAP4.x, 2) + Math.pow(coordAP4.y, 2);



        /* Generation of Matrix A and vector b */
        double [][]matrixA = new double[][]{

                {coordAP2.x - coordAP1.x, coordAP2.y - coordAP1.y, r2_1},
                {coordAP3.x - coordAP1.x, coordAP3.y - coordAP1.y, r3_1},
                {coordAP4.x - coordAP1.x, coordAP4.y - coordAP1.y, r4_1}};
        DenseMatrix64F A = new DenseMatrix64F(matrixA);
        CommonOps.changeSign(A); // A is negative

        double []vectorB = new double[]{
                Math.pow(r2_1, 2) - K2 + K1,
                Math.pow(r3_1, 2) - K3 + K1,
                Math.pow(r4_1, 2) - K4 + K1

        };
        DenseMatrix64F b = new DenseMatrix64F(3,1, false, vectorB);
        CommonOps.scale(0.5,b); //1/2*b

        /* Generation of solution vector x */
        DenseMatrix64F x = new DenseMatrix64F(3,1);

        /* Linear Solver Least Square */

        LinearSolver<DenseMatrix64F> solver = LinearSolverFactory.leastSquares(3, 3);

        if( !solver.setA(A) ) {
            throw new IllegalArgumentException("Singular matrix");
        }

        if( solver.quality() <= 1e-8 )
            throw new IllegalArgumentException("Nearly singular matrix");

        solver.solve(b,x);

        double xUserPos = x.get(0);
        double yUserPos = x.get(1);

        return new Point((int)xUserPos, (int)yUserPos);
    }

    /**
     * Estimates user position by applying an hyperbolic algorithm based on linearisation and
     * Weighted Least Square approach:
     *
     *      From "Weighted Least Square Techniques for Improved RSS based location"  with BS1
     *      as fixed BS.
     *
     *          [(x2-x1) (y2-y1) r2,1;
     *      A = (x3-x1) (y3-y1) r3,1;
     *          (x4-x1) (y4-y1) r4,1]
     *
     *
     *      b = 1/2* [r2,1^2-K2-K1;
     *                r3,1^2-K3-K1;
     *                r4.1^2-K4-K1]
     *
     *      S = [Var(r1²)+Var(r2²) Var(r1²) Var(r1²);
     *           Var(r1²) Var(r1²)+Var(r3²) Var(r1²);
     *           Var(r1²) Var(r1²) Var(r1²)+Var(r4²)]
     *
     *      x = (A'*S^-1*A)^-1*A'*S^−1*b
     *
     *      x = [x; y; r1]
     *
     * @param algInputList List of APAlgorithmData objects with the 4 AP data (BSSID - estimated
     *                     distance - RSS)
     * @return User position estimated
     */
    private Point weightedHyperbolicAlgorithm(List<APAlgorithmData> algInputList) {
        /* Gather all the collected data: AP coordinates and distances */
        Point coordAP1 = algInputList.get(0).coordinatesAP;
        Point coordAP2 = algInputList.get(1).coordinatesAP;
        Point coordAP3 = algInputList.get(2).coordinatesAP;
        Point coordAP4 = algInputList.get(3).coordinatesAP;

        double distAP1 = algInputList.get(0).distance;
        double distAP2 = algInputList.get(1).distance;
        double distAP3 = algInputList.get(2).distance;
        double distAP4 = algInputList.get(3).distance;

        // ri_j = ri - rj
        double r2_1 = distAP2 - distAP1;
        double r3_1 = distAP3 - distAP1;
        double r4_1 = distAP4 - distAP1;

        // Ki = xi^2 + yi^2
        double K1 = Math.pow(coordAP1.x, 2) + Math.pow(coordAP1.y, 2);
        double K2 = Math.pow(coordAP2.x, 2) + Math.pow(coordAP2.y, 2);
        double K3 = Math.pow(coordAP3.x, 2) + Math.pow(coordAP3.y, 2);
        double K4 = Math.pow(coordAP4.x, 2) + Math.pow(coordAP4.y, 2);


        /* Generation of Matrix A and vector b */
        double [][]matrixA = new double[][]{

                {coordAP2.x - coordAP1.x, coordAP2.y - coordAP1.y, r2_1},
                {coordAP3.x - coordAP1.x, coordAP3.y - coordAP1.y, r3_1},
                {coordAP4.x - coordAP1.x, coordAP4.y - coordAP1.y, r4_1}};
        DenseMatrix64F A = new DenseMatrix64F(matrixA);
        CommonOps.changeSign(A); // A is negative

        double []vectorB = new double[]{
                Math.pow(r2_1, 2) - K2 + K1,
                Math.pow(r3_1, 2) - K3 + K1,
                Math.pow(r4_1, 2) - K4 + K1

        };
        DenseMatrix64F b = new DenseMatrix64F(3,1, false, vectorB);
        CommonOps.scale(0.5,b); //1/2*b

        /* Generation of solution vector x */
        DenseMatrix64F x = new DenseMatrix64F(3,1);

        /* Generation of Matrix S of Variances */
        double [][] matrixS = new double [][]{
                {Math.pow(distAP1,4) + Math.pow(distAP2,4), Math.pow(distAP1,4),
                        Math.pow(distAP1,4)},
                {Math.pow(distAP1,4), Math.pow(distAP1,4) + Math.pow(distAP3,4),
                        Math.pow(distAP1,4)},
                {Math.pow(distAP1,4), Math.pow(distAP1,4), Math.pow(distAP1,
                        4) +  Math.pow(distAP4,4)},
        };

        DenseMatrix64F S = new DenseMatrix64F(matrixS);

        DenseMatrix64F SInv = new DenseMatrix64F(3,3);
        CommonOps.invert(S, SInv);


        // WEIGHTED LEAST SQUARE

        // A' = S^-1*A
        DenseMatrix64F APrime = new DenseMatrix64F(3,3);
        CommonOps.mult(SInv, A, APrime);

        // b' = W*b
        DenseMatrix64F bPrime = new DenseMatrix64F(3,1);
        CommonOps.mult(SInv, b, bPrime);


        /** Weighted Solver Least Square */

        LinearSolver<DenseMatrix64F> solver = LinearSolverFactory.leastSquares(3, 3);

        if( !solver.setA(APrime) ) {
            throw new IllegalArgumentException("Singular matrix");
        }

        if( solver.quality() <= 1e-8 )
            throw new IllegalArgumentException("Nearly singular matrix");

        solver.solve(bPrime,x);

        double xUserPosW = x.get(0);
        double yUserPosW = x.get(1);

        return new Point((int)xUserPosW, (int)yUserPosW);
    }


    /**
     * Estimates user position by applying a circular algorithm based on linearisation and
     * Least Square approach:
     *
     *      From "Radio Tracking of ORS" with BS1 as fixed BS.
     *
     *      A =   [(x2-x1) (y2-y1);
     *             (x3-x1) (y3-y1);
     *             (x4-x1) (y4-y1)]
     *
     *      b = 1/2 * [b21; b32; b41]
     *
     *      x = (A'A)^-1*A'*b
     *
     *      x = [x-x1; y-y1]
     *
     * @param algInputList List of APAlgorithmData objects with the 4 AP data (BSSID - estimated
     *                     distance - RSS)
     * @return User position estimated
     *
     */
    private Point circularAlgorithm(List<APAlgorithmData> algInputList) {
        /* Gather all the collected data: AP coordinates and distances */

        Point coordAP1 = algInputList.get(0).coordinatesAP;
        Point coordAP2 = algInputList.get(1).coordinatesAP;
        Point coordAP3 = algInputList.get(2).coordinatesAP;
        Point coordAP4 = algInputList.get(3).coordinatesAP;

        double distAP1 = algInputList.get(0).distance;
        double distAP2 = algInputList.get(1).distance;
        double distAP3 = algInputList.get(2).distance;
        double distAP4 = algInputList.get(3).distance;

        double d2_1 = Math.pow((coordAP2.x - coordAP1.x), 2) +  Math.pow((coordAP2.y - coordAP1
                .y), 2);
        double d3_1 = Math.pow((coordAP3.x - coordAP1.x), 2) +  Math.pow((coordAP3.y - coordAP1
                .y), 2);
        double d4_1 = Math.pow((coordAP4.x - coordAP1.x), 2) +  Math.pow((coordAP4.y - coordAP1
                .y), 2);

        double b2_1 = (Math.pow(distAP1,2) - Math.pow(distAP2,2) + d2_1);
        double b3_1 = (Math.pow(distAP1,2) - Math.pow(distAP3,2) + d3_1);
        double b4_1 = (Math.pow(distAP1,2) - Math.pow(distAP4,2) + d4_1);


        /* Generation of Matrix A and vector b */
        double [][]matrixA = new double[][]{

                {coordAP2.x - coordAP1.x, coordAP2.y - coordAP1.y},
                {coordAP3.x - coordAP1.x, coordAP3.y - coordAP1.y},
                {coordAP4.x - coordAP1.x, coordAP4.y - coordAP1.y}};
        DenseMatrix64F A = new DenseMatrix64F(matrixA);

        double []vectorB = new double[]{
                b2_1, b3_1, b4_1
        };
        DenseMatrix64F b = new DenseMatrix64F(3,1, false, vectorB);
        CommonOps.scale(0.5,b); //1/2*b

        /* Generation of solution vector x */
        DenseMatrix64F x = new DenseMatrix64F(2,1);


        /* Linear Solver Least Square */
        LinearSolver<DenseMatrix64F> solver = LinearSolverFactory.leastSquares(3, 3);

        if( !solver.setA(A) ) {
            throw new IllegalArgumentException("Singular matrix");
        }

        if( solver.quality() <= 1e-8 )
            throw new IllegalArgumentException("Nearly singular matrix");

        solver.solve(b,x);

        double xUserPos = x.get(0) + coordAP1.x; // x = [x-x1; y-y1]
        double yUserPos = x.get(1) + coordAP1.y; // x = [x-x1; y-y1]

        return new Point((int)xUserPos, (int)yUserPos);
    }

    /**
     * Estimates user position by applying a circular algorithm based on linearisation and
     * Weighted Least Square approach:
     *
     *      From "Weighted Least Square Techniques for Improved RSS based location"  with BS1
     *      as fixed BS.
     *
     *      A =   [(x2-x1) (y2-y1);
     *             (x3-x1) (y3-y1);
     *             (x4-x1) (y4-y1)]
     *
     *      b = 1/2 * [b21; b32; b41]
     *
     *      S = [Var(r1²)+Var(r2²) Var(r1²) Var(r1²);
     *           Var(r1²) Var(r1²)+Var(r3²) Var(r1²);
     *           Var(r1²) Var(r1²) Var(r1²)+Var(r4²)]
     *
     *      x = (A'*S^-1*A)^-1*A'*S^−1*b
     *
     *      x = [x-x1; y-y1]
     *
     * @param algInputList List of APAlgorithmData objects with the 4 AP data (BSSID - estimated
     *                     distance - RSS)
     * @return User position estimated
     */
    private Point weightedCircularAlgorithm(List<APAlgorithmData> algInputList) {
        /* Gather all the collected data: AP coordinates and distances */

        Point coordAP1 = algInputList.get(0).coordinatesAP;
        Point coordAP2 = algInputList.get(1).coordinatesAP;
        Point coordAP3 = algInputList.get(2).coordinatesAP;
        Point coordAP4 = algInputList.get(3).coordinatesAP;

        double distAP1 = algInputList.get(0).distance;
        double distAP2 = algInputList.get(1).distance;
        double distAP3 = algInputList.get(2).distance;
        double distAP4 = algInputList.get(3).distance;

        double d2_1 = Math.pow((coordAP2.x - coordAP1.x), 2) +  Math.pow((coordAP2.y - coordAP1
                .y), 2);
        double d3_1 = Math.pow((coordAP3.x - coordAP1.x), 2) +  Math.pow((coordAP3.y - coordAP1
                .y), 2);
        double d4_1 = Math.pow((coordAP4.x - coordAP1.x), 2) +  Math.pow((coordAP4.y - coordAP1
                .y), 2);

        double b2_1 = (Math.pow(distAP1,2) - Math.pow(distAP2,2) + d2_1);
        double b3_1 = (Math.pow(distAP1,2) - Math.pow(distAP3,2) + d3_1);
        double b4_1 = (Math.pow(distAP1,2) - Math.pow(distAP4,2) + d4_1);


        /* Generation of Matrix A, Matrix S of Variances and vector b */

        double [][]matrixA = new double[][]{

                {coordAP2.x - coordAP1.x, coordAP2.y - coordAP1.y},
                {coordAP3.x - coordAP1.x, coordAP3.y - coordAP1.y},
                {coordAP4.x - coordAP1.x, coordAP4.y - coordAP1.y}};
        DenseMatrix64F A = new DenseMatrix64F(matrixA);

        double [][] matrixS = new double [][]{
                {Math.pow(distAP1,4) + Math.pow(distAP2,4), Math.pow(distAP1,4),
                        Math.pow(distAP1,4)},
                {Math.pow(distAP1,4), Math.pow(distAP1,4) + Math.pow(distAP3,4),
                        Math.pow(distAP1,4)},
                {Math.pow(distAP1,4), Math.pow(distAP1,4), Math.pow(distAP1,
                        4) +  Math.pow(distAP4,4)},
        };

        DenseMatrix64F S = new DenseMatrix64F(matrixS);

        DenseMatrix64F SInv = new DenseMatrix64F(3,3);
        CommonOps.invert(S, SInv);

        double []vectorB = new double[]{
                b2_1, b3_1, b4_1
        };
        DenseMatrix64F b = new DenseMatrix64F(3,1, false, vectorB);
        CommonOps.scale(0.5,b); //1/2*b

        DenseMatrix64F x = new DenseMatrix64F(2,1);


        /* Weighted Solver Least Square */

        // A' = S^-1*A
        DenseMatrix64F APrime = new DenseMatrix64F(3,2);
        CommonOps.mult(SInv, A, APrime);

        // b' = W*b
        DenseMatrix64F bPrime = new DenseMatrix64F(3,1);
        CommonOps.mult(SInv, b, bPrime);


        // WEIGHTED LEAST SQUARE

        LinearSolver<DenseMatrix64F> solver = LinearSolverFactory.leastSquares(3, 3);

        if( !solver.setA(APrime) ) {
            throw new IllegalArgumentException("Singular matrix");
        }

        if( solver.quality() <= 1e-8 )
            throw new IllegalArgumentException("Nearly singular matrix");

        solver.solve(bPrime,x);

        double xUserPosW = x.get(0) + coordAP1.x;
        double yUserPosW = x.get(1) + coordAP1.y;

        return new Point((int)xUserPosW, (int)yUserPosW);
    }
}
