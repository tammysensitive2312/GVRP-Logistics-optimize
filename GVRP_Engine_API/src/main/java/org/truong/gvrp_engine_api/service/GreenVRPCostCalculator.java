package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import lombok.extern.slf4j.Slf4j;
import org.truong.gvrp_engine_api.distance_matrix.CostMatrix;
import org.truong.gvrp_engine_api.distance_matrix.MatrixBasedTransportCosts;
import org.truong.gvrp_engine_api.distance_matrix.MatrixMemory;
import org.truong.gvrp_engine_api.model.VehicleType;

import java.util.List;

import static org.truong.gvrp_engine_api.utils.AppConstant.CARBON_PRICE_PER_TON;
import static org.truong.gvrp_engine_api.utils.AppConstant.DEMAND_SCALE;

/**
 * GREEN VRP Cost Calculator
 * <p>
 * CRITICAL PRINCIPLES:
 * 1. Cost matrix contains ONLY physical distance/time (meters, seconds)
 * 2. CO2 cost is embedded in VehicleType.costPerDistance (VND/meter)
 * 3. All monetary costs in same unit (VND)
 * 4. CO2 cost is vehicle-dependent (diesel vs EV)
 * <p>
 * WHY THIS DESIGN:
 * - Jsprit solver only understands single scalar cost
 * - We map multi-objective (cost + CO2) → single objective via weighted sum
 * - Weights allow business to tune trade-off
 *
 * @author Truong
 * @version 2.0 (Refactored - Bug-free)
 */
@Slf4j
public class GreenVRPCostCalculator {

    /**
     * Build physical cost matrix - ONLY distance and time
     * <p>
     * WHY: Jsprit uses this matrix to calculate route cost via:
     * cost = distance × vehicleType.costPerDistance + time × vehicleType.costPerTime
     * <p>
     * IMPORTANT: Do NOT put money calculation here!
     * Money calculation is in vehicleType.costPerDistance
     *
     * @param costs     Ma trận đã dựng (block-diagonal hoặc dày) — chỉ giữ tham chiếu
     * @param locations List of all locations (depots + orders)
     * @return Jsprit cost matrix (physical only)
     */
    public static VehicleRoutingTransportCosts buildPhysicalCostMatrix(
            CostMatrix costs,
            List<Location> locations
    ) {
        // KHÔNG nạp n² cặp vào VehicleRoutingTransportCostsMatrix (n=6186 → 38M
        // HashMap entry, build() treo / GC-thrash). Adapter đọc thẳng ma trận đã có.
        log.info("✅ Transport costs adapter ready: {} locations | layout={} | {} (zero-copy, index-based)",
                locations.size(), costs.layout(),
                MatrixMemory.humanBytes(costs.allocatedBytes()));
        return new MatrixBasedTransportCosts(costs);
    }

    /**
     * Build GREEN vehicle type with embedded multi-objective cost
     * <p>
     * THIS IS THE KEY METHOD - WHERE GREEN VRP MAGIC HAPPENS
     * <p>
     * Formula:
     * totalCostPerMeter = (fuelCost × costWeight) + (co2Cost × co2Weight)
     * <p>
     * Example:
     * Diesel truck:
     * - Fuel: 5 VND/m
     * - CO2: 2.5 VND/m (250 g/km × 10,000 VND/kg)
     * - With weights (0.7, 0.3): 5×0.7 + 2.5×0.3 = 4.25 VND/m
     * <p>
     * Electric truck:
     * - Energy: 2 VND/m
     * - CO2: 0.5 VND/m (50 g/km × 10,000 VND/kg)
     * - With weights (0.7, 0.3): 2×0.7 + 0.5×0.3 = 1.55 VND/m
     * <p>
     * → Solver will prefer EV because 1.55 < 4.25!
     *
     * @param vehicleTypeDTO Vehicle type from database
     * @param costWeight     Weight for monetary cost (0-1)
     * @param co2Weight      Weight for CO2 cost (0-1)
     * @return Jsprit vehicle type with GREEN cost
     */
    public static VehicleTypeImpl buildGreenVehicleType(
            VehicleType vehicleTypeDTO,
            double costWeight,
            double co2Weight
    ) {
        // ========== STEP 1: Extract base costs from DTO ==========

        // Fuel/Energy cost (VND per km) → convert to VND per meter
        double fuelCostPerKm = vehicleTypeDTO.getCostPerKm();
        double fuelCostPerMeter = fuelCostPerKm / 1000.0;

        // Time cost (VND per hour) → convert to VND per second
        double timeCostPerHour = vehicleTypeDTO.getCostPerHour();
        double timeCostPerSecond = timeCostPerHour / 3600.0;

        // Fixed cost per trip (VND)
        double fixedCost = vehicleTypeDTO.getFixedCost();
        // ========== STEP 2: Calculate CO2 cost ==========

        // Emission factor (g CO2 per km)
        double emissionFactorGramPerKm = vehicleTypeDTO.getEmissionFactor();
        // g/km → tấn/km (÷1e6) → VND/km (×giá) → VND/m (÷1000) = tổng ÷1e9
        double co2CostPerMeter = emissionFactorGramPerKm * CARBON_PRICE_PER_TON / 1_000_000_000.0;

        // ========== STEP 3: Weighted multi-objective cost ==========

        // Total cost per meter = weighted sum (all in VND/meter)
        // This is what Jsprit will use to calculate route cost
        double totalCostPerMeter = (fuelCostPerMeter * costWeight) + (co2CostPerMeter * co2Weight);

        // ========== STEP 4: Build Jsprit vehicle type ==========
        double weightedTimeCost = timeCostPerSecond * costWeight;
        double weightedFixedCost = fixedCost * costWeight;
        int scaledCapacity = Math.round(vehicleTypeDTO.getCapacity() * DEMAND_SCALE);

        VehicleTypeImpl.Builder typeBuilder = VehicleTypeImpl.Builder
                .newInstance("type-" + vehicleTypeDTO.getId())
                .addCapacityDimension(0, scaledCapacity)
                .setCostPerDistance(totalCostPerMeter)
                .setCostPerTransportTime(weightedTimeCost)
                .setCostPerWaitingTime(weightedTimeCost)
                .setFixedCost(weightedFixedCost);

        return typeBuilder.build();
    }

    /**
     * Calculate CO2 emissions for a route (for reporting)
     * <p>
     * Used after optimization to report actual CO2 emissions
     *
     * @param distanceKm  Route distance in kilometers
     * @param vehicleType Vehicle type with emission factor
     * @return CO2 emissions in kg
     */
    public static double calculateRouteCO2(
            double distanceKm,
            VehicleType vehicleType) {

        double emissionFactor = vehicleType.getEmissionFactor() != null
                ? vehicleType.getEmissionFactor()
                : 200.0;

        // CO2 (kg) = distance (km) × emission (g/km) ÷ 1000
        return (distanceKm * emissionFactor) / 1000.0;
    }

    /**
     * Validate and normalize optimization weights
     * <p>
     * Ensures weights are positive and sum to 1.0
     *
     * @param costWeight Original cost weight
     * @param co2Weight Original CO2 weight
     * @return Array [normalizedCostWeight, normalizedCo2Weight]
     */
    public static double[] normalizeWeights(double costWeight, double co2Weight) {
        if (costWeight < 0 || co2Weight < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }

        double total = costWeight + co2Weight;
        if (total == 0) {
            throw new IllegalArgumentException("At least one weight must be > 0");
        }

        double normalizedCost = costWeight / total;
        double normalizedCo2 = co2Weight / total;

        log.debug("Normalized weights: cost={}, CO2={}", normalizedCost, normalizedCo2);

        return new double[]{normalizedCost, normalizedCo2};
    }

    /**
     * Get current carbon price
     */
    public static double getCarbonPrice() {
        return CARBON_PRICE_PER_TON;
    }

}
