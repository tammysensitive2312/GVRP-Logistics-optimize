package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxDistanceConstraintTest {

    // Layout tọa độ 1D để tính khoảng cách bằng tay, tránh sai số Haversine
    // Depot=0, A=1000m, B=2000m, C=2500m (dọc theo trục X, đơn vị mét)
    private Location depot, locA, locB, locC;
    private VehicleRoutingTransportCostsMatrix costs;
    private static final String VEHICLE_ID = "vehicle-TimeTerminationBugTest.java";

    @BeforeEach
    void setUp() {
        depot = loc("depot-TimeTerminationBugTest.java", 0);
        locA  = loc("order-A", 1000);
        locB  = loc("order-B", 2000);
        locC  = loc("order-C", 2500);

        // Ma trận khoảng cách Euclidean 1D — khớp chính xác |x_i - x_j|
        VehicleRoutingTransportCostsMatrix.Builder b =
                VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);

        Location[] all = {depot, locA, locB, locC};
        for (Location from : all) {
            for (Location to : all) {
                double dist = Math.abs(from.getCoordinate().getX() - to.getCoordinate().getX());
                b.addTransportDistance(from.getId(), to.getId(), dist);
                b.addTransportTime(from.getId(), to.getId(), dist / 10.0); // 10 m/s giả định
            }
        }
        costs = b.build();
    }

    private Location loc(String id, double x) {
        return Location.Builder.newInstance()
                .setId(id)
                .setCoordinate(Coordinate.newInstance(x, 0))
                .build();
    }

    // ============================================================
    // KỊCH BẢN A — Route rỗng, job cách xa depot
    // ============================================================
    @Test
    void fulfilled_emptyRoute_jobBeyondMaxDistance_shouldReject() {
        // maxDistance = 100m nhưng job C cách depot 2500m round-trip = 5000m
        double maxDistance = 100.0;

        VehicleRoute emptyRoute = buildRoute(VEHICLE_ID, depot, depot); // chưa có job nào

        Service jobC = Service.Builder.newInstance("order-C")
                .setLocation(locC)
                .build();

        JobInsertionContext ctx = new JobInsertionContext(emptyRoute, jobC, emptyRoute.getVehicle(), null, 0.0);

        OptimizationService.MaxDistanceConstraint constraint =
                new OptimizationService.MaxDistanceConstraint(costs, Map.of(VEHICLE_ID, maxDistance));

        // Code cũ (bug): route rỗng → totalDistance ≈ 0 → luôn true
        // Code đúng: phải tính chèn C vào route rỗng = depot→C→depot = 5000m > 100m → false
        assertFalse(constraint.fulfilled(ctx),
                "Route rỗng nhưng job cách xa vẫn phải bị từ chối nếu vượt maxDistance");
    }

    // ============================================================
    // KỊCH BẢN B — Route có sẵn, detour vượt giới hạn
    // ============================================================
    @Test
    void fulfilled_existingRoute_insertionExceedsMaxDistance_shouldReject() {
        // Route hiện tại: Depot(0) -> A(1000) -> Depot(0), tổng = 2000m
        // maxDistance = 2100m — route hiện tại OK (2000 ≤ 2100)
        // Nhưng nếu chèn B(2000) vào cuối: Depot->A->B->Depot
        //   = 1000 + 1000 + 2000 = 4000m  → VƯỢT giới hạn
        double maxDistance = 2100.0;

        VehicleRoute routeWithA = buildRouteWithJob(VEHICLE_ID, depot, depot, locA, "order-A");

        Service jobB = Service.Builder.newInstance("order-B")
                .setLocation(locB)
                .build();

        JobInsertionContext ctx = new JobInsertionContext(routeWithA, jobB, routeWithA.getVehicle(), null, 0.0);

        OptimizationService.MaxDistanceConstraint constraint =
                new OptimizationService.MaxDistanceConstraint(costs, Map.of(VEHICLE_ID, maxDistance));

        // Code cũ (bug): chỉ tính route hiện tại (2000m) ≤ 2100m → true (SAI)
        // Code đúng: phải tính 2000 + minDetour(B) và so sánh với 2100m → false
        assertFalse(constraint.fulfilled(ctx),
                "Chèn job B phải bị từ chối vì tổng khoảng cách sau khi chèn (4000m) vượt maxDistance (2100m), " +
                        "dù route hiện tại (2000m) vẫn đang hợp lệ");
    }

    // ============================================================
    // KỊCH BẢN C — Baseline: chèn job vẫn hợp lệ (không được quá chặt)
    // ============================================================
    @Test
    void fulfilled_existingRoute_insertionWithinMaxDistance_shouldAccept() {
        // Route hiện tại: Depot(0) -> A(1000) -> Depot(0) = 2000m
        // maxDistance = 5000m — đủ rộng để chèn thêm B mà vẫn hợp lệ
        double maxDistance = 5000.0;

        VehicleRoute routeWithA = buildRouteWithJob(VEHICLE_ID, depot, depot, locA, "order-A");

        Service jobB = Service.Builder.newInstance("order-B")
                .setLocation(locB)
                .build();

        JobInsertionContext ctx = new JobInsertionContext(routeWithA, jobB, routeWithA.getVehicle(), null, 0.0);

        OptimizationService.MaxDistanceConstraint constraint =
                new OptimizationService.MaxDistanceConstraint(costs, Map.of(VEHICLE_ID, maxDistance));

        assertTrue(constraint.fulfilled(ctx),
                "Chèn job B phải được chấp nhận vì tổng khoảng cách (4000m) vẫn dưới maxDistance (5000m) — " +
                        "đảm bảo fix không làm constraint quá chặt (false negative)");
    }

    // ==================== HELPER METHODS ====================

    private VehicleRoute buildRoute(String vehicleId, Location start, Location end) {
        VehicleTypeImpl type = VehicleTypeImpl.Builder.newInstance("type-TimeTerminationBugTest.java").build();
        VehicleImpl vehicle = VehicleImpl.Builder.newInstance(vehicleId)
                .setStartLocation(start)
                .setEndLocation(end)
                .setType(type)
                .build();
        return VehicleRoute.Builder.newInstance(vehicle).build();
    }

    private VehicleRoute buildRouteWithJob(String vehicleId, Location start, Location end,
                                           Location jobLoc, String jobId) {
        VehicleTypeImpl type = VehicleTypeImpl.Builder.newInstance("type-TimeTerminationBugTest.java").build();
        VehicleImpl vehicle = VehicleImpl.Builder.newInstance(vehicleId)
                .setStartLocation(start)
                .setEndLocation(end)
                .setType(type)
                .build();

        Service job = Service.Builder.newInstance(jobId).setLocation(jobLoc).build();

        return VehicleRoute.Builder.newInstance(vehicle)
                .addService(job)
                .build();
    }
}