package com.raulprojects.android.ekfindoortracker;

import android.graphics.Point;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.ops.CommonOps;

import java.util.List;

/**
 *  Algorithm class which will estimate user position by applying Weighted Circular Least Square
 *  algorithm. It is used for getting the initial guess x0/0 for inputting EKF algorithm.
 *
 *  For a more detailed explanation on how these algorithms have been derived and deployed,
 *  please refer to Msc Thesis.
 *
 *  NOTE: A Java matrix library will be used to handle matrix operations in an efficient way.
 *  EJML has been chosen because its good performance showed at Java Matrix Benchmark
 *  (https://code.google.com/p/java-matrix-benchmark/)
 */
public class LSAlgorithm {

    /**
     * Main method which finds user position based on WiFi scan results.
     * @param algorithmInputDataList List of APAlgorithmData objects with the 4 AP data  (BSSID -
     *                               estimated distance - RSS)
     * @return Point object with AP position
     *
     */
    public Point applyWCLSAlgorithm(List<APAlgorithmData> algorithmInputDataList){
            Point initialUserPosition = new Point(0, 0);
            // Weighted Circular algorithm
            initialUserPosition = weightedCircularAlgorithm(algorithmInputDataList);

            return initialUserPosition;
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
     *
     *      NOTE: In this application (EKFTracker), only WCLS is used.
     */


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
