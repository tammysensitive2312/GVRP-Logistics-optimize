package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.algorithm.box.Jsprit;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleTypeImpl;
import com.graphhopper.jsprit.core.util.Coordinate;
import com.graphhopper.jsprit.core.util.Solutions;
import com.graphhopper.jsprit.core.util.VehicleRoutingTransportCostsMatrix;
import org.junit.jupiter.api.Test;
import org.truong.gvrp_engine_api.model.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.truong.gvrp_engine_api.utils.AppConstant.DEMAND_SCALE;

class SolutionMetricsCalculatorTest {

    /**
     * Kịch bản tối thiểu để phơi bày bug DEMAND_SCALE:
     * - TimeTerminationBugTest.java vehicle, capacity = 100 kg
     * - 2 orders: demand 30kg và 50kg → tổng 80kg
     * - Kỳ vọng: avgLoadUtilization = 80.0%
     * - Bug hiện tại sẽ trả về: 800.0% (phóng đại đúng DEMAND_SCALE = 10 lần)
     */
    @Test
    void calculate_loadUtilization_shouldNotBeScaledByDemandScale() {

        // ===== ARRANGE =====

        // TimeTerminationBugTest.java. Depot tại (0,0), hai order tại (0,TimeTerminationBugTest.java) và (0,2) — đơn giản hóa khoảng cách
        // setIndex BẮT BUỘC: SolutionMetricsCalculator tra ma trận qua Location.getIndex()
        // (O(1)), index phải khớp thứ tự trong allLocations bên dưới.
        Location depotLoc = Location.Builder.newInstance()
                .setId("depot-TimeTerminationBugTest.java")
                .setIndex(0)
                .setCoordinate(Coordinate.newInstance(0, 0))
                .build();

        Location order1Loc = Location.Builder.newInstance()
                .setId("order-TimeTerminationBugTest.java")
                .setIndex(1)
                .setCoordinate(Coordinate.newInstance(0, 1))
                .build();

        Location order2Loc = Location.Builder.newInstance()
                .setId("order-2")
                .setIndex(2)
                .setCoordinate(Coordinate.newInstance(0, 2))
                .build();

        List<Location> allLocations = List.of(depotLoc, order1Loc, order2Loc);

        // 2. Vehicle type — capacity gốc (KHÔNG scale) = 100kg
        int rawCapacity = 100;
        VehicleTypeImpl vehicleType = VehicleTypeImpl.Builder.newInstance("type-TimeTerminationBugTest.java")
                .addCapacityDimension(0, rawCapacity * DEMAND_SCALE) // Jsprit cần scaled
                .setCostPerDistance(1.0)
                .build();

        VehicleImpl vehicle = VehicleImpl.Builder.newInstance("vehicle-TimeTerminationBugTest.java")
                .setStartLocation(depotLoc)
                .setEndLocation(depotLoc)
                .setType(vehicleType)
                .build();

        // 3. Hai order — demand gốc 30kg và 50kg
        double rawDemand1 = 30.0;
        double rawDemand2 = 50.0;

        Service order1 = Service.Builder.newInstance("order-TimeTerminationBugTest.java")
                .setLocation(order1Loc)
                .addSizeDimension(0, (int) Math.round(rawDemand1 * DEMAND_SCALE))
                .build();

        Service order2 = Service.Builder.newInstance("order-2")
                .setLocation(order2Loc)
                .addSizeDimension(0, (int) Math.round(rawDemand2 * DEMAND_SCALE))
                .build();

        // 4. Build VRP với cost matrix đơn giản (mọi cạnh = 1000m để dễ tính tay)
        VehicleRoutingTransportCostsMatrix.Builder matrixBuilder =
                VehicleRoutingTransportCostsMatrix.Builder.newInstance(true);
        for (Location from : allLocations) {
            for (Location to : allLocations) {
                matrixBuilder.addTransportDistance(from.getId(), to.getId(), 1000.0);
                matrixBuilder.addTransportTime(from.getId(), to.getId(), 100.0);
            }
        }

        VehicleRoutingProblem vrp = VehicleRoutingProblem.Builder.newInstance()
                .addVehicle(vehicle)
                .addJob(order1)
                .addJob(order2)
                .setRoutingCost(matrixBuilder.build())
                .setFleetSize(VehicleRoutingProblem.FleetSize.FINITE)
                .build();

        VehicleRoutingAlgorithm algorithm = Jsprit.Builder.newInstance(vrp).buildAlgorithm();
        Collection<VehicleRoutingProblemSolution> solutions = algorithm.searchSolutions();
        VehicleRoutingProblemSolution bestSolution = Solutions.bestOf(solutions);

        // 5. Dựng OptimizationContext + DistanceTimeMatrix khớp với DTO gốc (chưa scale)
        Order orderDTO1 = new Order();
        orderDTO1.setId(1L);
        orderDTO1.setOrderCode("order-TimeTerminationBugTest.java");
        orderDTO1.setDemand(rawDemand1);

        Order orderDTO2 = new Order();
        orderDTO2.setId(2L);
        orderDTO2.setOrderCode("order-2");
        orderDTO2.setDemand(rawDemand2);

        Vehicle vehicleDTO = new Vehicle();
        vehicleDTO.setId(1L);
        vehicleDTO.setVehicleTypeId(1L);
        vehicleDTO.setStartDepotId(1L);
        vehicleDTO.setEndDepotId(1L);

        VehicleType vehicleTypeDTO = new VehicleType();
        vehicleTypeDTO.setId(1L);
        vehicleTypeDTO.setCapacity(rawCapacity); // ← capacity GỐC, chưa scale
        vehicleTypeDTO.setCostPerKm(5.0);
        vehicleTypeDTO.setCostPerHour(20000.0);
        vehicleTypeDTO.setFixedCost(50000.0);
        vehicleTypeDTO.setEmissionFactor(200.0);
        vehicleTypeDTO.setMaxDuration(12.0);

        Depot depotDTO = new Depot();
        depotDTO.setId(1L);
        depotDTO.setName("Main Depot");
        depotDTO.setLatitude(0.0);
        depotDTO.setLongitude(0.0);

        OptimizationContext context = new OptimizationContext(
                allLocations,
                Map.of(1L, depotDTO),
                Map.of(1L, orderDTO1, 2L, orderDTO2),
                Map.of(1L, vehicleTypeDTO),
                Map.of(1L, vehicleDTO)
        );

        int n = allLocations.size();
        double[][] distanceMatrix = new double[n][n];
        double[][] timeMatrix = new double[n][n];
        for (double[] row : distanceMatrix) Arrays.fill(row, 1000.0);
        for (double[] row : timeMatrix) Arrays.fill(row, 100.0);

        DistanceTimeMatrix matrix = new DistanceTimeMatrix(distanceMatrix, timeMatrix, allLocations);

        // ===== ACT =====
        SolutionMetrics metrics = SolutionMetricsCalculator.calculate(bestSolution, context, matrix);

        // ===== ASSERT =====
        // Tổng demand thực = 30 + 50 = 80kg, capacity = 100kg → utilization phải là 80%
        double expectedLoadUtilization = 80.0;

        assertEquals(
                expectedLoadUtilization,
                metrics.getAvgLoadUtilization(),
                0.01,
                "LoadUtilization phải tính trên đơn vị kg thực (80%), " +
                        "không phải đơn vị Jsprit đã nhân DEMAND_SCALE (sẽ ra 800% nếu có bug)"
        );
    }
}