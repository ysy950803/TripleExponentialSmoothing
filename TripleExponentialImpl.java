/*
  Copyright 2011 Nishant Chandra
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.ysy.ourstory.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Given a time series, say a complete monthly data for 12 months, the Holt-Winters smoothing and forecasting
 * technique is built on the following formulae (multiplicative version):
 * <p>
 * St[i] = alpha * y[i] / It[i - period] + (1.0 - alpha) * (St[i - 1] + Bt[i - 1])
 * Bt[i] = gamma * (St[i] - St[i - 1]) + (1 - gamma) * Bt[i - 1]
 * It[i] = beta * y[i] / St[i] + (1.0 - beta) * It[i - period]
 * Ft[i + m] = (St[i] + (m * Bt[i])) * It[i - period + m]
 * <p>
 * Note: Many authors suggest calculating initial values of St, Bt and It in a variety of ways, but
 * some of them are incorrect e.g. determination of It parameter using regression. I have used
 * the NIST recommended methods.
 * <p>
 * For more details, see:
 * http://adorio-research.org/wordpress/?p=1230
 * http://www.itl.nist.gov/div898/handbook/pmc/section4/pmc435.htm
 *
 * @author Nishant Chandra
 */
public class TripleExponentialImpl {

    /**
     * This method is the entry point. It calculates the initial values and returns the forecast
     * for the m periods.
     *
     * @param y      - Time series data.
     * @param alpha  - Exponential smoothing coefficients for level, trend, seasonal components.
     * @param beta   - Exponential smoothing coefficients for level, trend, seasonal components.
     * @param gamma  - Exponential smoothing coefficients for level, trend, seasonal components.
     * @param period - A complete season's data consists of L periods. And we need to estimate
     *               the trend factor from one period to the next. To accomplish this, it is advisable to use
     *               two complete seasons; that is, 2L periods.
     * @param m      - Extrapolated future data points.
     * @param debug  - Print debug values. Useful for testing.
     *               <p>
     *               4 quarterly
     *               7 weekly.
     *               12 monthly
     */
    public static double[] forecast(double[] y, double alpha, double beta,
                                    double gamma, int period, int m, boolean debug) {
        if (y == null) {
            return null;
        }

        int seasons = y.length / period;
        double a0 = calculateInitialLevel(y, period);
        double b0 = calculateInitialTrend(y, period);
        double[] initialSeasonalIndices = calculateSeasonalIndices(y, period, seasons);

        if (debug) {
            System.out.println(String.format(
                    "Total observations: %d, Seasons %d, Periods %d", y.length,
                    seasons, period));
            System.out.println("Initial level value a0: " + a0);
            System.out.println("Initial trend value b0: " + b0);
            printArray("Seasonal Indices: ", initialSeasonalIndices);
        }

        double[] forecast = calculateHoltWinters(y, a0, b0, alpha, beta, gamma,
                initialSeasonalIndices, period, m, debug);

//		if (debug) {
//			printArray("Forecast", forecast);
//		}

        return forecast;
    }

    /**
     * This method realizes the Holt-Winters equations.
     *
     * @param y
     * @param a0
     * @param b0
     * @param alpha
     * @param beta
     * @param gamma
     * @param initialSeasonalIndices
     * @param period
     * @param m
     * @param debug
     * @return - Forecast for m periods.
     */
    private static double[] calculateHoltWinters(double[] y, double a0, double b0, double alpha,
                                                 double beta, double gamma, double[] initialSeasonalIndices, int period, int m, boolean debug) {
        double[] St = new double[y.length];
        double[] Bt = new double[y.length];
        double[] It = new double[y.length];
        double[] Ft = new double[y.length + m];

        //Initialize base values
        St[1] = a0;
        Bt[1] = b0;

        for (int i = 0; i < period; i++) {
            It[i] = initialSeasonalIndices[i];
        }

        Ft[m] = (St[0] + (m * Bt[0])) * It[0];//This is actually 0 since Bt[0] = 0
        Ft[m + 1] = (St[1] + (m * Bt[1])) * It[1];//Forecast starts from period + 2

        //Start calculations
        for (int i = 2; i < y.length; i++) {

            //Calculate overall smoothing
            if ((i - period) >= 0) {
                St[i] = alpha * y[i] / It[i - period] + (1.0 - alpha) * (St[i - 1] + Bt[i - 1]);
            } else {
                St[i] = alpha * y[i] + (1.0 - alpha) * (St[i - 1] + Bt[i - 1]);
            }

            //Calculate trend smoothing
            Bt[i] = gamma * (St[i] - St[i - 1]) + (1 - gamma) * Bt[i - 1];

            //Calculate seasonal smoothing
            if ((i - period) >= 0) {
                It[i] = beta * y[i] / St[i] + (1.0 - beta) * It[i - period];
            }

            //Calculate forecast
            if (((i + m) >= period)) {
                Ft[i + m] = (St[i] + (m * Bt[i])) * It[i - period + m];
            }

            if (debug) {
                System.out.println(String.format(
                        "i = %d, y = %f, S = %f, Bt = %f, It = %f, F = %f", i,
                        y[i], St[i], Bt[i], It[i], Ft[i]));
            }
        }

        return Ft;
    }

    /**
     * See: http://robjhyndman.com/researchtips/hw-initialization/
     * 1st period's average can be taken. But y[0] works better.
     *
     * @return - Initial Level value i.e. St[1]
     */
    private static double calculateInitialLevel(double[] y, int period) {
        /**
         double sum = 0;
         for (int i = 0; i < period; i++) {
         sum += y[i];
         }
         return sum / period;
         **/
        return y[0];
    }

    /**
     * See: http://www.itl.nist.gov/div898/handbook/pmc/section4/pmc435.htm
     *
     * @return - Initial trend - Bt[1]
     */
    private static double calculateInitialTrend(double[] y, int period) {
        double sum = 0;

        for (int i = 0; i < period; i++) {
            sum += (y[period + i] - y[i]);
        }

        return sum / (period * period);
    }

    /**
     * See: http://www.itl.nist.gov/div898/handbook/pmc/section4/pmc435.htm
     *
     * @return - Seasonal Indices.
     */
    private static double[] calculateSeasonalIndices(double[] y, int period, int seasons) {
        double[] seasonalAverage = new double[seasons];
        double[] seasonalIndices = new double[period];

        double[] averagedObservations = new double[y.length];

        for (int i = 0; i < seasons; i++) {
            for (int j = 0; j < period; j++) {
                seasonalAverage[i] += y[(i * period) + j];
            }
            seasonalAverage[i] /= period;
        }

        for (int i = 0; i < seasons; i++) {
            for (int j = 0; j < period; j++) {
                averagedObservations[(i * period) + j] = y[(i * period) + j] / seasonalAverage[i];
            }
        }

        for (int i = 0; i < period; i++) {
            for (int j = 0; j < seasons; j++) {
                seasonalIndices[i] += averagedObservations[(j * period) + i];
            }
            seasonalIndices[i] /= seasons;
        }

        return seasonalIndices;
    }

    /**
     * Utility method to pring array values.
     *
     * @param description
     * @param data
     */
    private static void printArray(String description, double[] data) {
        System.out.println(String.format("******************* %s *********************", description));

        for (int i = 0; i < data.length; i++) {
            System.out.println(data[i]);
        }

        System.out.println(String.format("*****************************************************************", description));
    }

    public static void main(String[] args) {
        // TODO start
        int period = 6, m = 1;
        List<String> records = new ArrayList<>();
        records.add("40");
        records.add("35");
        records.add("32");
        records.add("37");
        records.add("40");
        records.add("33");
        records.add("42");
        records.add("38");
        records.add("29");
        records.add("34");
        records.add("34");
        records.add("34");
        records.add("45");
        // TODO end
        double[] real = new double[records.size()];
        for (int i = 0; i < records.size(); i++) {
            real[i] = Double.valueOf(records.get(i));
        }
        double alpha = 0.1, beta = 0.45, gamma = 0;
        boolean debug = false;
        double[] predict = forecast(real, alpha, beta, gamma, period, m, debug);
        System.out.println("-----------predict----------------------------------");
        for (int i = real.length; i < predict.length; i++) {
            System.out.println(predict[i]);
        }
        // TODO test
        // 36.64707954494079 6
        // 30.309483124206825 5
        // 36.96437267934931 4
        // 38.42057394991909 3
        // 30.07780441549877 2
        System.out.println((36.64707954494079
                + 30.309483124206825
                + 36.96437267934931
                + 38.42057394991909
                + 30.07780441549877) / 5);
        // 34.48386274278296
    }
}
