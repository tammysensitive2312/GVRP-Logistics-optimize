package org.truong.gvrp_entry_api.service.lez;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.truong.gvrp_entry_api.entity.enums.LocationType;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.allDay;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.compilation;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.encode;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.square;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.stop;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.zone;

/**
 * BF-T6 and BF-T7 (BROWSER-FIX-001, E6): user-facing LEZ texts name the zone,
 * codes and {@code zoneKey} keep the internal key.
 */
class LezZoneNameTextTest {

    private static final long VEHICLE = 7L;
    private static final MultiPolygon SQUARE = square(106.005, 9.995, 106.015, 10.005);

    private static List<ComplianceChecker.Violation> stopInside(CompiledRestrictions restrictions) {
        return ComplianceChecker.check(VEHICLE, List.of(
                stop(LocationType.ORDER, 11L, point(10.0, 106.010),
                        LocalTime.of(9, 0), LocalTime.of(9, 10), null)), restrictions);
    }

    // ---------- BF-T6 ----------

    @Test
    void bfT6_stopInsideAnAllDayZone_detailNamesTheZone() {
        CompiledRestrictions restrictions = compilation("h",
                zone("Z1", SQUARE, "Test zone"), allDay(VEHICLE, "Z1"));

        List<ComplianceChecker.Violation> violations = stopInside(restrictions);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.ZONE_ACCESS);
        assertThat(violations.get(0).zoneKey()).isEqualTo("Z1");
        assertThat(violations.get(0).detail())
                .isEqualTo("Stop is in zone \"Test zone\" and the vehicle is banned all day there")
                .doesNotContain("zone Z1");
    }

    @Test
    void bfT6_legCrossingAnAllDayZone_detailNamesTheZone() {
        CompiledRestrictions restrictions = compilation("h",
                zone("Z1", SQUARE, "Test zone"), allDay(VEHICLE, "Z1"));

        List<ComplianceChecker.Violation> violations = ComplianceChecker.check(VEHICLE, List.of(
                stop(LocationType.DEPOT, null, point(10.0, 106.0),
                        null, LocalTime.of(8, 0), encode(10.0, 106.0, 10.0, 106.020)),
                stop(LocationType.ORDER, 11L, point(10.0, 106.020),
                        LocalTime.of(8, 30), null, null)), restrictions);

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.ZONE_TRANSIT);
        assertThat(violations.get(0).zoneKey()).isEqualTo("Z1");
        assertThat(violations.get(0).detail())
                .contains("\"Test zone\"")
                .doesNotContain("zone Z1");
    }

    @Test
    void bfT6_blankZoneName_fallsBackToTheKey() {
        CompiledRestrictions restrictions = compilation("h",
                zone("Z1", SQUARE, "   "), allDay(VEHICLE, "Z1"));

        List<ComplianceChecker.Violation> violations = stopInside(restrictions);

        assertThat(violations.get(0).detail()).contains("\"Z1\"");
        assertThat(violations.get(0).zoneKey()).isEqualTo("Z1");
    }

    @Test
    void bfT6_label_trimsTheName() {
        assertThat(ComplianceChecker.label(zone("Z1", SQUARE, "  Hanoi LEZ  "))).isEqualTo("Hanoi LEZ");
        assertThat(ComplianceChecker.label(zone("Z1", SQUARE, null))).isEqualTo("Z1");
    }

    // ---------- BF-T7 ----------

    @Test
    void bfT7_reasonNamesEveryCoveringZone() {
        CompiledRestrictions restrictions = compilation("h", zone("Z1", SQUARE, "A"));
        restrictions.getZones().add(zone("Z2", SQUARE, "B"));
        restrictions.getOrderZones().put(11L, List.of("Z1", "Z2"));

        assertThat(RouteComplianceService.noCompliantVehicleReason(restrictions, 11L))
                .isEqualTo("No compliant vehicle for zone \"A\", \"B\"");
    }

    @Test
    void bfT7_keyWithoutACompiledZone_isPrintedAsTheKey() {
        CompiledRestrictions restrictions = compilation("h", zone("Z1", SQUARE, "A"));
        restrictions.getOrderZones().put(11L, List.of("Z1", "Z9"));

        assertThat(RouteComplianceService.noCompliantVehicleReason(restrictions, 11L))
                .isEqualTo("No compliant vehicle for zone \"A\", \"Z9\"");
    }
}
