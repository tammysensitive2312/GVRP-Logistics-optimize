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
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.noZones;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.partial;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.point;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.square;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.stop;
import static org.truong.gvrp_entry_api.service.lez.LezFixtures.zone;

/**
 * {@link ComplianceChecker} on hand-built compilations (LEZ-001 P1b, CC-T1…CC-T5).
 * Pure: no Spring, no repository, no engine.
 *
 * <p>Zone under test is a square from lon 106.005 to 106.015 and lat 9.995 to
 * 10.005; the stops at lon 106.000 and 106.020 are outside it, the leg between
 * them crosses it.
 */
class ComplianceCheckerTest {

    private static final long VEHICLE = 7L;
    private static final String ZONE = "Z1";
    private static final MultiPolygon SQUARE = square(106.005, 9.995, 106.015, 10.005);

    private static String crossingLeg() {
        return encode(10.0, 106.0, 10.0, 106.020);
    }

    private static List<ComplianceChecker.Violation> check(CompiledRestrictions restrictions,
                                                           List<ComplianceChecker.StopView> stops) {
        return ComplianceChecker.check(VEHICLE, stops, restrictions);
    }

    // ---------- CC-T1 ----------

    @Test
    void ccT1_stopInsideAnAllDayZone_isZONE_ACCESS() {
        CompiledRestrictions restrictions = compilation("h", zone(ZONE, SQUARE), allDay(VEHICLE, ZONE));

        List<ComplianceChecker.Violation> violations = check(restrictions, List.of(
                stop(LocationType.ORDER, 11L, point(10.0, 106.010),
                        LocalTime.of(9, 0), LocalTime.of(9, 10), null)));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.ZONE_ACCESS);
        assertThat(violations.get(0).seq()).isZero();
        assertThat(violations.get(0).orderId()).isEqualTo(11L);
        assertThat(violations.get(0).zoneKey()).isEqualTo(ZONE);
        assertThat(ComplianceChecker.blocks(violations)).isTrue();
    }

    @Test
    void ccT1_stopOutsideTheZone_isClean() {
        CompiledRestrictions restrictions = compilation("h", zone(ZONE, SQUARE), allDay(VEHICLE, ZONE));

        assertThat(check(restrictions, List.of(
                stop(LocationType.ORDER, 11L, point(10.0, 106.0),
                        LocalTime.of(9, 0), LocalTime.of(9, 10), null)))).isEmpty();
    }

    // ---------- CC-T2 ----------

    @Test
    void ccT2_legCrossingAnAllDayZone_isZONE_TRANSIT() {
        CompiledRestrictions restrictions = compilation("h", zone(ZONE, SQUARE), allDay(VEHICLE, ZONE));

        List<ComplianceChecker.Violation> violations = check(restrictions, List.of(
                stop(LocationType.DEPOT, null, point(10.0, 106.0),
                        null, LocalTime.of(8, 0), crossingLeg()),
                stop(LocationType.ORDER, 11L, point(10.0, 106.020),
                        LocalTime.of(8, 30), null, null)));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.ZONE_TRANSIT);
        assertThat(violations.get(0).seq()).isZero();
    }

    @Test
    void ccT2_legOutsideTheZone_isClean() {
        CompiledRestrictions restrictions = compilation("h", zone(ZONE, SQUARE), allDay(VEHICLE, ZONE));
        String outside = encode(10.0, 106.020, 10.0, 106.040);

        assertThat(check(restrictions, List.of(
                stop(LocationType.DEPOT, null, point(10.0, 106.020),
                        null, LocalTime.of(8, 0), outside),
                stop(LocationType.ORDER, 11L, point(10.0, 106.040),
                        LocalTime.of(8, 30), null, null)))).isEmpty();
    }

    // ---------- CC-T3 ----------

    private static CompiledRestrictions peakRestrictions() {
        return compilation("h", zone(ZONE, SQUARE),
                partial(VEHICLE, ZONE,
                        new int[]{LezFixtures.PEAK_START, LezFixtures.PEAK_END},
                        new int[]{LezFixtures.EVENING_START, LezFixtures.EVENING_END}));
    }

    @Test
    void ccT3_stopAt0730InThePeak_isZONE_ACCESS() {
        List<ComplianceChecker.Violation> violations = check(peakRestrictions(), List.of(
                stop(LocationType.ORDER, 11L, point(10.0, 106.010),
                        LocalTime.of(7, 30), LocalTime.of(7, 45), null)));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.ZONE_ACCESS);
    }

    @Test
    void ccT3_stopAt1000OutsideEveryPeak_isClean() {
        assertThat(check(peakRestrictions(), List.of(
                stop(LocationType.ORDER, 11L, point(10.0, 106.010),
                        LocalTime.of(10, 0), LocalTime.of(10, 15), null)))).isEmpty();
    }

    @Test
    void ccT3_leg0850To0905InThePeak_isZONE_TRANSIT() {
        List<ComplianceChecker.Violation> violations = check(peakRestrictions(), List.of(
                stop(LocationType.DEPOT, null, point(10.0, 106.0),
                        null, LocalTime.of(8, 50), crossingLeg()),
                stop(LocationType.ORDER, 11L, point(10.0, 106.020),
                        LocalTime.of(9, 5), null, null)));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.ZONE_TRANSIT);
    }

    // ---------- CC-T4 ----------

    @Test
    void ccT4_missingVehicleAttribute_isLEZ_DATA_MISSING_andNamesIt() {
        CompiledRestrictions.VehicleRestriction restriction = partial(VEHICLE, ZONE,
                new int[]{LezFixtures.PEAK_START, LezFixtures.PEAK_END});
        restriction.getMissingAttributes().add(RuleEvaluator.ATTR_GROSS_WEIGHT);
        CompiledRestrictions restrictions = compilation("h", zone(ZONE, SQUARE), restriction);

        List<ComplianceChecker.Violation> violations = check(restrictions, List.of(
                stop(LocationType.ORDER, 11L, point(10.0, 106.010),
                        LocalTime.of(7, 30), LocalTime.of(7, 45), null)));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.LEZ_DATA_MISSING);
        assertThat(violations.get(0).detail()).contains(RuleEvaluator.ATTR_GROSS_WEIGHT);
    }

    // ---------- CC-T5 ----------

    @Test
    void ccT5_missingLegGeometry_isLEZ_GEOMETRY_MISSING() {
        CompiledRestrictions restrictions = compilation("h", zone(ZONE, SQUARE),
                LezFixtures.allowed(VEHICLE, ZONE));

        List<ComplianceChecker.Violation> violations = check(restrictions, List.of(
                stop(LocationType.DEPOT, null, point(10.0, 106.0), null, LocalTime.of(8, 0), null),
                stop(LocationType.ORDER, 11L, point(10.0, 106.020),
                        LocalTime.of(8, 30), null, null)));

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo(ComplianceChecker.LEZ_GEOMETRY_MISSING);
        assertThat(violations.get(0).seq()).isZero();
        assertThat(violations.get(0).zoneKey()).isNull();
    }

    @Test
    void ccT5_noZones_isAlwaysEmpty() {
        assertThat(check(noZones("h"), List.of(
                stop(LocationType.DEPOT, null, point(10.0, 106.0), null, LocalTime.of(8, 0), null),
                stop(LocationType.ORDER, 11L, point(10.0, 106.020),
                        LocalTime.of(8, 30), null, null)))).isEmpty();
    }

    // ---------- vehicle without a verdict / index type ----------

    @Test
    void noVerdictForThisVehicle_doesNotFailIt() {
        CompiledRestrictions restrictions = compilation("h", zone(ZONE, SQUARE), allDay(99L, ZONE));

        assertThat(check(restrictions, List.of(
                stop(LocationType.ORDER, 11L, point(10.0, 106.010),
                        LocalTime.of(9, 0), LocalTime.of(9, 10), null)))).isEmpty();
    }
}
