package org.truong.gvrp_engine_api.utils;

public class AppConstant {
    // 1 solver unit = 0.1 kg (or 0.1 pallet)
    public static final int DEMAND_SCALE = 10;

    /**
     * Carbon pricing (VND per kg CO2)
     * Can be adjusted based on:
     * - Government carbon tax
     * - Company ESG policy
     * - International carbon market price
     */

    public static final double CARBON_PRICE_PER_KG = 100000.0; // Example: 10,000 VND/kg CO2
}
