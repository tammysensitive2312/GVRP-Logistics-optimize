package org.truong.gvrp_engine_api.service;

import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.Skills;
import com.graphhopper.jsprit.core.problem.job.Service;
import com.graphhopper.jsprit.core.problem.vehicle.VehicleImpl;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EB-T1: {@code lezSkills} compiled by Entry must reach jsprit as plain offered and
 * required skills, and an absent field must leave the problem exactly as before.
 *
 * <p>jsprit keeps skill names case-insensitively and reports them lower-cased in
 * {@link Skills#values()}, so the assertions ask {@code containsSkill}, which is
 * the same comparison jsprit uses when it matches a job to a vehicle.
 */
class LezSkillMappingTest {

    private static VehicleImpl.Builder vehicleBuilder() {
        return VehicleImpl.Builder
                .newInstance("vehicle-1")
                .setStartLocation(Location.newInstance("depot-1"));
    }

    private static Service.Builder orderBuilder() {
        return Service.Builder
                .newInstance("order-1")
                .setLocation(Location.newInstance("order-1"));
    }

    private static void assertSkills(Skills skills, String... expected) {
        assertEquals(expected.length, skills.values().size(),
                "exactly these skills and nothing else: " + skills.values());
        for (String skill : expected) {
            assertTrue(skills.containsSkill(skill),
                    "missing skill " + skill + " in " + skills.values());
        }
    }

    @Test
    void lezSkillsBecomeOfferedVehicleSkillsTogetherWithVehicleTypeSkills() {
        VehicleImpl.Builder builder = vehicleBuilder();
        OptimizationService.addVehicleSkills(builder, Set.of("REFRIGERATED"));
        OptimizationService.addVehicleSkills(builder, Set.of("LEZ_Z1", " lez_z17 "));

        assertSkills(builder.build().getSkills(), "REFRIGERATED", "LEZ_Z1", "LEZ_Z17");
    }

    @Test
    void lezSkillsBecomeRequiredOrderSkillsTogetherWithRegularRequirements() {
        Service.Builder builder = orderBuilder();
        OptimizationService.addRequiredSkills(builder, Set.of("HAZMAT_CAPABLE"));
        OptimizationService.addRequiredSkills(builder, Set.of("LEZ_Z1"));

        assertSkills(builder.build().getRequiredSkills(), "HAZMAT_CAPABLE", "LEZ_Z1");
    }

    @Test
    void absentLezSkillsKeepTodaysBehaviour() {
        VehicleImpl.Builder legacyVehicle = vehicleBuilder();
        OptimizationService.addVehicleSkills(legacyVehicle, Set.of("REFRIGERATED"));
        OptimizationService.addVehicleSkills(legacyVehicle, null);
        OptimizationService.addVehicleSkills(legacyVehicle, new LinkedHashSet<>());

        Service.Builder legacyOrder = orderBuilder();
        OptimizationService.addRequiredSkills(legacyOrder, null);
        OptimizationService.addRequiredSkills(legacyOrder, Set.of());

        assertSkills(legacyVehicle.build().getSkills(), "REFRIGERATED");
        assertTrue(legacyOrder.build().getRequiredSkills().values().isEmpty(),
                "no lezSkills must add no requirement at all");
    }

    @Test
    void differentZonesAreIndependentSkills() {
        VehicleImpl.Builder builder = vehicleBuilder();
        OptimizationService.addVehicleSkills(builder, List.of("LEZ_Z1", "LEZ_Z17").stream()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));

        assertTrue(builder.build().getSkills().containsSkill("LEZ_Z1"));
        assertTrue(builder.build().getSkills().containsSkill("LEZ_Z17"));
        assertEquals(2, builder.build().getSkills().values().size(),
                "one skill per zone, no prefix collapse");
    }
}
