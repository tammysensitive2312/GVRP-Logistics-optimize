package org.truong.gvrp_engine_api.service;

import java.util.ArrayList;
import java.util.List;

import static org.truong.gvrp_engine_api.utils.AppConstant.EPSILON;

/**
 * Sinh danh sách trọng số (costWeight, co2Weight) cho Pareto multi-run,
 * dùng phân bố luy thua thay vi 4 diem co dinh cua ObjectivePreset cu.
 *
 * Cong thuc: w_c^(k) = (k/N)^p, k = 0..N
 * p > 1 lam day diem sample o vung w_c nho, noi quan he giua trong so
 * va vi tri tren Pareto frontier la phi tuyen (xem GreenVRPCostCalculator).
 */
public class ParetoWeightSampler {

    public record WeightPoint(String label, double costWeight, double co2Weight) {}

    /**
     * @param n so khoang chia (so diem = n+1)
     * @param p he so luy thua, p=1 la tuyen tinh deu, p>1 tap trung ve phia w_c nho
     */
    public static List<WeightPoint> generate(int n, double p) {
        if (n < 1) {
            throw new IllegalArgumentException("N phai >= 1");
        }
        if (p <= 0) {
            throw new IllegalArgumentException("p phai > 0");
        }

        List<WeightPoint> points = new ArrayList<>();

        for (int k = 0; k <= n; k++) {
            double ratio = (double) k / n;
            double costWeight = Math.pow(ratio, p);
            double co2Weight = 1.0 - costWeight;

            if (costWeight == 0.0) {
                costWeight = EPSILON;
                co2Weight = 1.0 - EPSILON;
            }

            String label = String.format("PARETO_K%d_OF_%d", k, n);
            points.add(new WeightPoint(label, costWeight, co2Weight));
        }

        return points;
    }
}
