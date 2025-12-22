package org.truong.gvrp_engine_api.model;

import com.graphhopper.jsprit.core.problem.Location;

import java.util.List;

public record DistanceTimeMatrix(
        double[][] distanceMatrix,
        double[][] timeMatrix,
        List<Location> locations
) {}