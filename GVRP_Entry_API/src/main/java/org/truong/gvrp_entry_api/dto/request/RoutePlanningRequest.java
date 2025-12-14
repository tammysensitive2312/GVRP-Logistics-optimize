package org.truong.gvrp_entry_api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutePlanningRequest {

    @NotEmpty(message = "Order IDs cannot be empty")
    @Size(min = 1, message = "At least 1 order required")
    private List<Long> orderIds;

    @NotEmpty(message = "Vehicle IDs cannot be empty")
    @Size(min = 1, message = "At least 1 vehicle required")
    private List<Long> vehicleIds;

    private OptimizationPreferences preferences;

    @Data
    public static class OptimizationPreferences {

        /**
         * Optimization goal: Chọn mục tiêu optimization
         * - MINIMIZE_COST: Tối ưu chi phí (default)
         * - MINIMIZE_DISTANCE: Tối ưu quãng đường
         * - MINIMIZE_CO2: Tối ưu khí thải CO2 (green)
         * - BALANCED: Cân bằng giữa cost, distance, CO2
         */
        private OptimizationGoal goal = OptimizationGoal.MINIMIZE_COST;

        /**
         * Optimization speed: Tốc độ vs Quality trade-off
         * - FAST: 2-3 phút, chất lượng acceptable (500 iterations)
         * - NORMAL: 5-8 phút, chất lượng tốt (2000 iterations) - DEFAULT
         * - HIGH_QUALITY: 10-15 phút, chất lượng cao nhất (5000 iterations)
         */
        private OptimizationSpeed speed = OptimizationSpeed.NORMAL;

        /**
         * Allow unassigned orders?
         * - true: Cho phép bỏ qua orders không thể assign (penalty thấp)
         * - false: Cố gắng assign tất cả orders (penalty cao) - DEFAULT
         */
        private Boolean allowUnassignedOrders = false;

        /**
         * Time window strictness
         * - STRICT: Phải tuân thủ 100% time windows (hard constraint)
         * - FLEXIBLE: Cho phép vi phạm time window nhưng có penalty (soft constraint)
         */
        private TimeWindowMode timeWindowMode = TimeWindowMode.STRICT;
    }

    /**
     * Optimization goals - Business-level
     */
    public enum OptimizationGoal {
        MINIMIZE_COST,      // Tối ưu chi phí
        MINIMIZE_DISTANCE,  // Tối ưu quãng đường
        MINIMIZE_CO2,       // Tối ưu CO2 (green)
        BALANCED            // Cân bằng
    }

    /**
     * Optimization speed/quality trade-off
     */
    public enum OptimizationSpeed {
        FAST,          // 2-3 phút
        NORMAL,        // 5-8 phút (default)
        HIGH_QUALITY   // 10-15 phút
    }

    /**
     * Time window constraint mode
     */
    public enum TimeWindowMode {
        STRICT,    // Hard constraint
        FLEXIBLE   // Soft constraint with penalty
    }
}
