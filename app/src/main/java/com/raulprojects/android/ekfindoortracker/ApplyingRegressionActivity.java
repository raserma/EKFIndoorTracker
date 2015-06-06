package com.raulprojects.android.ekfindoortracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;


public class ApplyingRegressionActivity extends Activity {
    private int mIdBssidApSelected;
    private double [] mRssValuesDB;
    private double [] mDistanceValuesDB;

    private double [] mInputCoefficients;
    private EditText m_aCoeff, m_bCoeff, m_cCoeff, m_dCoeff;
    IndoorTrackerDatabaseHandler measdbh;
    public int getmIdBssidApSelected() {
        return mIdBssidApSelected;
    }
    public void setmIdBssidApSelected(int mIdBssidApSelected) {
        this.mIdBssidApSelected = mIdBssidApSelected;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applying_regression);
        initiateAndroid();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_applying_regression, menu);
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

    private void initiateAndroid() {
        // Creation of MAC/BSSID database
        measdbh = new IndoorTrackerDatabaseHandler(this);

        mInputCoefficients = new double[4];

        // Creation of Edit Texts
        m_aCoeff   = (EditText)findViewById(R.id.text_edit_aCoeff);
        m_aCoeff.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL |
                InputType.TYPE_NUMBER_FLAG_SIGNED);

        m_bCoeff   = (EditText)findViewById(R.id.text_edit_bCoeff);
        m_bCoeff.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL |
                InputType.TYPE_NUMBER_FLAG_SIGNED);

        m_cCoeff   = (EditText)findViewById(R.id.text_edit_cCoeff);
        m_cCoeff.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL |
                InputType.TYPE_NUMBER_FLAG_SIGNED);

        m_dCoeff   = (EditText)findViewById(R.id.text_edit_dCoeff);
        m_dCoeff.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL |
                InputType.TYPE_NUMBER_FLAG_SIGNED);

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
                        setmIdBssidApSelected(Integer.parseInt(input.getText().toString()));
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                        // code = 0 -> error
                        setmIdBssidApSelected(0);
                    }
                })
                .show();
    }

    public void startPolynomialRegression(View view){
        // We get set of data from database
        mRssValuesDB = measdbh.getRSSValuesDB (getmIdBssidApSelected());
        mDistanceValuesDB = measdbh.getDistanceValuesDB(getmIdBssidApSelected());
        /* Curve fitting using 4th order polynomial regression */
        // Creation of input data: y[] and X[][] - NOTE: I should consider using an external matrix
        // library
        int k = 4; // Polynomial degree is k - 1 = 3th degree
        int n = mRssValuesDB.length; // n = number of observations
        double y[] = mDistanceValuesDB;
        double x0 [] = new double[n];
        double x1 [] = new double[n];
        double x2 [] = new double[n];
        double x3 [] = new double[n];
        for (int i = 0; i < n; i++){
            x0[i] = Math.pow(mRssValuesDB[i], 0);
            x1[i] = Math.pow(mRssValuesDB[i], 1);
            x2[i] = Math.pow(mRssValuesDB[i], 2);
            x3[i] = Math.pow(mRssValuesDB[i], 3);
        }
        double X[][] = new double[n][k];
        for(int j = 0; j < k; j++){
            for (int i = 0; i < n; i++) {
                X[i][0] = x0[i];
                X[i][1] = x1[i];
                X[i][2] = x2[i];
                X[i][3] = x3[i];
            }
        }
        // Using OLSMultipleLinearRegression library
        OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
        ols.newSampleData(y, X);
        ols.setNoIntercept(true);
        ols.newSampleData(y, X);
        // distance = a + bx + cx² + dx³ --> coefficients = [a b c d]
        double[] coefficients = ols.estimateRegressionParameters();
        // Show them in the screen
        // Store them on database
        measdbh.addCoefficientsDB(mIdBssidApSelected, coefficients);
    }

    public void inputCoeffManually (View view){
        mInputCoefficients[0] = Double.parseDouble(m_aCoeff.getText().toString());
        mInputCoefficients[1] = Double.parseDouble(m_bCoeff.getText().toString());
        mInputCoefficients[2] = Double.parseDouble(m_cCoeff.getText().toString());
        mInputCoefficients[3] = Double.parseDouble(m_dCoeff.getText().toString());

        /* Stores input coefficients in database */
        measdbh.addCoefficientsDB(mIdBssidApSelected, mInputCoefficients);
    }


}
