package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationSkillMappingTest {

    @Test
    void normalizesSkillsDeterministically() {
        Set<String> input = new LinkedHashSet<>(List.of(
                " refrigerated ", "HAZMAT_CAPABLE", "REFRIGERATED"));

        assertEquals(
                List.of("HAZMAT_CAPABLE", "REFRIGERATED"),
                OptimizationService.normalizedSkills(input));
    }

    @Test
    void mapsVehicleAndOrderSkillsToJsprit() {
        VehicleImpl.Builder vehicleBuilder = VehicleImpl.Builder
                .newInstance("vehicle-1")
                .setStartLocation(Location.newInstance("depot-1"));
        OptimizationService.addVehicleSkills(vehicleBuilder, Set.of("REFRIGERATED"));

        Service.Builder orderBuilder = Service.Builder
                .newInstance("order-1")
                .setLocation(Location.newInstance("order-1"));
        OptimizationService.addRequiredSkills(orderBuilder, Set.of("REFRIGERATED"));

        assertTrue(vehicleBuilder.build().getSkills().containsSkill("REFRIGERATED"));
        assertTrue(orderBuilder.build().getRequiredSkills().containsSkill("REFRIGERATED"));
    }

    @Test
    void emptySkillsKeepLegacyRequestsUnrestricted() {
        assertTrue(OptimizationService.normalizedSkills(null).isEmpty());
        assertTrue(OptimizationService.normalizedSkills(Set.of()).isEmpty());
    }
}
