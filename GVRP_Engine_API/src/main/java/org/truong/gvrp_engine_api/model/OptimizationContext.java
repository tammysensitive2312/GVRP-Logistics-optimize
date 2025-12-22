package org.truong.gvrp_engine_api.model;

import com.graphhopper.jsprit.core.problem.Location;

import java.util.List;
import java.util.Map;

public record OptimizationContext(
        List<Location> allLocations,
        Map<Long, Depot> depotDTOs,
        Map<Long, Order> orderDTOs,
        Map<Long, VehicleType> vehicleTypeDTOs,
        Map<Long, Vehicle> vehicleDTOs
) {}