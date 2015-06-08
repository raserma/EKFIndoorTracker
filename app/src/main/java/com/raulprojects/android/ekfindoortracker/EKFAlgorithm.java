package com.raulprojects.android.ekfindoortracker;

import android.graphics.Point;

import org.ejml.data.DenseMatrix64F;

import static org.ejml.ops.CommonOps.*;

public class EKFAlgorithm {

    // these are predeclared for efficiency reasons
    private DenseMatrix64F a,b;
    private DenseMatrix64F y,S,S_inv,c,d;
    private DenseMatrix64F K;

    private Point getUserPosition (Point initialGuess){

        /** Creation of parameters F, H, Q, R, x and P */

        // F, Jacobian of f
        double [][]matrixF = new double[][]{

                {1, 0},
                {0, 1}};
        DenseMatrix64F F = new DenseMatrix64F(matrixF);

        int dimenX = F.numCols;

        //H, Jacobian of h

        // Q, process noise covariance matrix
        double [][]matrixQ = new double[][]{
                {0.001, 0},
                {0, 0.001}};
        DenseMatrix64F Q = new DenseMatrix64F(matrixQ);

        // R, measurement noise covariance matrix

        // Initial mean x
        double []vectorX = new double[]{
                initialGuess.x,
                initialGuess.y
        };
        DenseMatrix64F x = new DenseMatrix64F(dimenX, 1, false, vectorX);

        // Initial covariance matrix P
        double [][]matrixP = new double[][]{
                {1, 0},
                {0, 1}};
        DenseMatrix64F P = new DenseMatrix64F(matrixP);

        /** Prediction step */

        // x = F x
        mult(F,x,a);
        x.set(a);

        // P = F P F' + Q
        mult(F,P,b);
        multTransB(b,F, P);
        addEquals(P,Q);

    }
}
