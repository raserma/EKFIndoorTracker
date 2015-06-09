package com.raulprojects.android.ekfindoortracker;

import android.graphics.Point;

import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.ops.CommonOps;

import java.util.List;

import static org.ejml.ops.CommonOps.addEquals;
import static org.ejml.ops.CommonOps.mult;
import static org.ejml.ops.CommonOps.multTransB;

public class EKFAlgorithm {

    // system state estimate
    private DenseMatrix64F x,P;
    int dimenX = 2;

    // kinematics description
    private DenseMatrix64F F,H,Q,R;
    int dimenZ = 4;

    // these are predeclared for efficiency reasons
    private DenseMatrix64F a,b;
    private DenseMatrix64F y,S,S_inv,c,d;
    private DenseMatrix64F K;

    private LinearSolver<DenseMatrix64F> solver;

    public Point applyEKFAlgorithm ( List<APAlgorithmData> algorithmInputDataList,
                                     Point initialGuess){
        /** Creation of parameters x, P, F, H, Q and R */
        creationAPrioriEstimates(initialGuess);

        creationPredeclaredVariables();

        creationJacobianMatrices(x, algorithmInputDataList);

        creationNoiseCovarianceMatrices();


        /** Prediction step */

        // x = F x
        mult(F,x,a);
        x.set(a);

        // P = F P F' + Q
        mult(F,P,b);
        multTransB(b,F, P);
        addEquals(P,Q);

    }
    private void creationAPrioriEstimates(Point initialGuess) {
        // Initial mean x
        double []vectorX = new double[]{
                initialGuess.x,
                initialGuess.y
        };
        x = new DenseMatrix64F(dimenX, 1, false, vectorX);

        // Initial covariance matrix P
        double [][]matrixP = new double[][]{
                {1, 0},
                {0, 1}};
        P = new DenseMatrix64F(matrixP);

    }

    private void creationPredeclaredVariables() {
        a = new DenseMatrix64F(dimenX,1);
        b = new DenseMatrix64F(dimenX,dimenX);
        y = new DenseMatrix64F(dimenZ,1);
        S = new DenseMatrix64F(dimenZ,dimenZ);
        S_inv = new DenseMatrix64F(dimenZ,dimenZ);
        c = new DenseMatrix64F(dimenZ,dimenX);
        d = new DenseMatrix64F(dimenX,dimenZ);
        K = new DenseMatrix64F(dimenX,dimenZ);


        // covariance matrices are symmetric positive semi-definite
        solver = LinearSolverFactory.symmPosDef(dimenX);
    }

    private void creationJacobianMatrices(DenseMatrix64F x,
                                          List<APAlgorithmData> algorithmInputDataList) {
        // F, Jacobian of f
        F = CommonOps.identity(2);

        // H, Jacobian of h

    }

    private void creationNoiseCovarianceMatrices() {
        // Q, process noise covariance matrix
        Q = CommonOps.identity(dimenX);
        double Q0 = 0.001;
        CommonOps.scale (Q0, Q);

        // R, measurement noise covariance matrix
        R = CommonOps.identity(dimenZ);
        double R0 = 0.001;
        CommonOps.scale (R0, R);
    }


}
