package org.truong.gvrp_engine_api.distance_matrix;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.cost.AbstractForwardVehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;

/**
 * Adapter cho Jsprit đọc THẲNG từ double[][] của ta — KHÔNG dựng bản sao ma trận.
 * <p>
 * VÌ SAO: VehicleRoutingTransportCostsMatrix lưu HashMap&lt;RelationKey,Double&gt; với
 * n² entry (n=6186 → 38M entry + 38M key object) → build() treo / GC-thrash.
 * FastVehicleRoutingTransportCostsMatrix cũng cấp phát new double[n][n][2] (38M
 * mảng con). Adapter này chỉ giữ 2 tham chiếu tới mảng ĐÃ CÓ → 0 byte thêm.
 * <p>
 * YÊU CẦU: mọi Location phải được set index qua Location.Builder.setIndex(),
 * trùng với index hàng/cột trong ma trận (xem OptimizationService.prepareContext).
 * Nếu thiếu index → ném IllegalStateException (fail-loud, không sai âm thầm).
 * <p>
 * Công thức cost tái tạo ĐÚNG như VehicleRoutingTransportCostsMatrix của Jsprit:
 * perDistanceUnit × mét + perTransportTimeUnit × giây (và trả distance khi vehicle == null).
 * <p>
 * Thread-safety: hoàn toàn chỉ-đọc sau khi khởi tạo → an toàn khi Jsprit chạy đa luồng.
 */
public class MatrixBasedTransportCosts extends AbstractForwardVehicleRoutingTransportCosts {

    private final CostMatrix costs;

    public MatrixBasedTransportCosts(CostMatrix costs) {
        this.costs = costs;
    }

    /** Tương thích ngược cho test dựng ma trận dày thủ công. */
    public MatrixBasedTransportCosts(double[][] distanceMeters, double[][] timeSeconds) {
        this(new DenseCostMatrix(distanceMeters, timeSeconds));
    }

    private static int idx(Location loc) {
        int i = loc.getIndex();
        if (i < 0) {
            throw new IllegalStateException("Location " + loc.getId()
                    + " chưa set index — bắt buộc setIndex() để tra ma trận O(1)");
        }
        return i;
    }

    @Override
    public double getDistance(Location from, Location to, double departureTime, Vehicle vehicle) {
        return costs.distanceMeters(idx(from), idx(to));
    }

    @Override
    public double getTransportTime(Location from, Location to, double departureTime,
                                   Driver driver, Vehicle vehicle) {
        return costs.timeSeconds(idx(from), idx(to));
    }

    @Override
    public double getTransportCost(Location from, Location to, double departureTime,
                                   Driver driver, Vehicle vehicle) {
        int i = idx(from), j = idx(to);
        double d = costs.distanceMeters(i, j);
        if (vehicle == null) return d;
        VehicleTypeImpl.VehicleCostParams p = vehicle.getType().getVehicleCostParams();
        return p.perDistanceUnit * d + p.perTransportTimeUnit * costs.timeSeconds(i, j);
    }
}
