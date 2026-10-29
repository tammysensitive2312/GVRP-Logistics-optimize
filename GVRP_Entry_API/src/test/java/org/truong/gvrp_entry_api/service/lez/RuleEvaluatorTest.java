package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.truong.gvrp_entry_api.entity.enums.EnergyType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.truong.gvrp_entry_api.entity.enums.EnergyType.DIESEL;
import static org.truong.gvrp_entry_api.entity.enums.EnergyType.ELECTRIC;
import static org.truong.gvrp_entry_api.entity.enums.EnergyType.PETROL;
import static org.truong.gvrp_entry_api.entity.enums.LegalClass.CAR;
import static org.truong.gvrp_entry_api.entity.enums.LegalClass.MOTORBIKE;
import static org.truong.gvrp_entry_api.entity.enums.LegalClass.TRUCK;

/**
 * {@link RuleEvaluator}, {@link ScheduleNormalizer} and {@link RuleValidator}
 * (LEZ-001 P1a, RE-T1…RE-T11). Pure unit tests; the rules are test fixtures,
 * not seed data.
 */
class RuleEvaluatorTest {

    /** A Wednesday; the guard test below checks it. */
    private static final LocalDate WEDNESDAY = LocalDate.of(2027, 3, 3);
    private static final LocalDate SATURDAY = LocalDate.of(2027, 3, 6);

    private static final RuleSelector ANY_VEHICLE = new RuleSelector(null, null, null, null, null, null);
    private static final RuleSchedule ALWAYS = new RuleSchedule(RuleSchedule.Type.ALWAYS_FORBIDDEN, null, null);

    private static final VehicleFacts PETROL_TRUCK_4000 = new VehicleFacts(TRUCK, PETROL, 4000, 1500, 5);

    // ---------- fixtures ----------

    private static RuleView rule(long id, RuleSelector selector, RuleSchedule schedule,
                                 LocalDate from, LocalDate to) {
        return new RuleView(id, selector, schedule, from, to);
    }

    private static RuleView rule(RuleSelector selector, RuleSchedule schedule) {
        return rule(1L, selector, schedule, LocalDate.of(2027, 1, 1), null);
    }

    private static RuleSchedule during(String... times) {
        return new RuleSchedule(RuleSchedule.Type.FORBIDDEN_DURING, null, pairs(times));
    }

    private static RuleSchedule allowedOnly(String... times) {
        return new RuleSchedule(RuleSchedule.Type.ALLOWED_ONLY, null, pairs(times));
    }

    private static List<List<String>> pairs(String... times) {
        List<List<String>> pairs = new java.util.ArrayList<>();
        for (int i = 0; i < times.length; i += 2) {
            pairs.add(List.of(times[i], times[i + 1]));
        }
        return pairs;
    }

    private static RuleSelector selector(List<LegalClass> classes, List<EnergyType> energy,
                                         RuleSelector.WeightMeasure measure, RuleSelector.WeightBounds weight,
                                         Integer emissionBelow, Boolean appBased) {
        return new RuleSelector(classes, energy, measure, weight, emissionBelow, appBased);
    }

    private static RuleSelector.WeightBounds kg(Integer gt, Integer gte, Integer lt, Integer lte) {
        return new RuleSelector.WeightBounds(gt, gte, lt, lte);
    }

    private static ZoneVerdict evaluate(VehicleFacts vehicle, List<RuleView> rules, List<PermitView> permits,
                                        LocalDate date) {
        return RuleEvaluator.evaluate(vehicle, rules, permits, date, false);
    }

    private static MinuteInterval iv(int start, int end) {
        return new MinuteInterval(start, end);
    }

    @Test
    void fixtureDates_haveTheExpectedWeekdays() {
        assertThat(WEDNESDAY.getDayOfWeek()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(SATURDAY.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
    }

    // ---------- RE-T1 effective range ----------

    @Test
    void reT1_onlyTheRuleCoveringTheDateApplies() {
        LocalDate date = LocalDate.of(2027, 6, 15);
        RuleView before = rule(1L, ANY_VEHICLE, during("06:00", "07:00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        RuleView covering = rule(2L, ANY_VEHICLE, during("08:00", "09:00"),
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        RuleView after = rule(3L, ANY_VEHICLE, during("10:00", "11:00"),
                LocalDate.of(2028, 1, 1), null);

        ZoneVerdict verdict = evaluate(PETROL_TRUCK_4000, List.of(before, covering, after), List.of(), date);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.PARTIAL);
        assertThat(verdict.forbidden()).containsExactly(iv(480, 540));
    }

    @Test
    void reT1_nullEffectiveToIsOpenEnded_andBoundsAreInclusive() {
        RuleView open = rule(1L, ANY_VEHICLE, ALWAYS, LocalDate.of(2028, 1, 1), null);

        assertThat(evaluate(PETROL_TRUCK_4000, List.of(open), List.of(), LocalDate.of(2027, 12, 31)).kind())
                .isEqualTo(ZoneVerdict.Kind.ALLOWED);
        assertThat(evaluate(PETROL_TRUCK_4000, List.of(open), List.of(), LocalDate.of(2028, 1, 1)).kind())
                .isEqualTo(ZoneVerdict.Kind.ALL_DAY);
        assertThat(evaluate(PETROL_TRUCK_4000, List.of(open), List.of(), LocalDate.of(2099, 1, 1)).kind())
                .isEqualTo(ZoneVerdict.Kind.ALL_DAY);

        RuleView closed = rule(2L, ANY_VEHICLE, ALWAYS, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31));
        assertThat(evaluate(PETROL_TRUCK_4000, List.of(closed), List.of(), LocalDate.of(2027, 1, 31)).kind())
                .isEqualTo(ZoneVerdict.Kind.ALL_DAY);
        assertThat(evaluate(PETROL_TRUCK_4000, List.of(closed), List.of(), LocalDate.of(2027, 2, 1)).kind())
                .isEqualTo(ZoneVerdict.Kind.ALLOWED);
    }

    // ---------- RE-T2 weekday filter ----------

    @Test
    void reT2_weekendRule_notAppliedOnWednesday_appliedOnSaturday() {
        RuleSchedule weekend = new RuleSchedule(RuleSchedule.Type.ALWAYS_FORBIDDEN,
                List.of(RuleSchedule.Day.SAT, RuleSchedule.Day.SUN), null);
        List<RuleView> rules = List.of(rule(ANY_VEHICLE, weekend));

        assertThat(evaluate(PETROL_TRUCK_4000, rules, List.of(), WEDNESDAY).kind())
                .isEqualTo(ZoneVerdict.Kind.ALLOWED);
        assertThat(evaluate(PETROL_TRUCK_4000, rules, List.of(), SATURDAY).kind())
                .isEqualTo(ZoneVerdict.Kind.ALL_DAY);
    }

    // ---------- RE-T3 AND semantics ----------

    @Test
    void reT3_oneNonMatchingFieldMeansNoMatch() {
        RuleSelector truckPetrolHeavyButElectricOnly = selector(List.of(TRUCK), List.of(ELECTRIC),
                RuleSelector.WeightMeasure.GROSS, kg(3500, null, null, null), null, null);

        ZoneVerdict verdict = evaluate(PETROL_TRUCK_4000,
                List.of(rule(truckPetrolHeavyButElectricOnly, ALWAYS)), List.of(), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALLOWED);
        assertThat(verdict.forbidden()).isEmpty();

        RuleSelector allMatching = selector(List.of(TRUCK), List.of(PETROL, DIESEL),
                RuleSelector.WeightMeasure.GROSS, kg(3500, null, null, null), 6, null);
        assertThat(evaluate(PETROL_TRUCK_4000, List.of(rule(allMatching, ALWAYS)), List.of(), WEDNESDAY).kind())
                .isEqualTo(ZoneVerdict.Kind.ALL_DAY);
    }

    // ---------- RE-T4 missing attribute ----------

    @Test
    void reT4_missingGrossWeightCountsAsMatch_andIsReported() {
        VehicleFacts noGrossWeight = new VehicleFacts(TRUCK, DIESEL, null, 1000, 5);
        RuleSelector heavyTrucks = selector(List.of(TRUCK), null,
                RuleSelector.WeightMeasure.GROSS, kg(3500, null, null, null), null, null);

        ZoneVerdict verdict = evaluate(noGrossWeight, List.of(rule(heavyTrucks, ALWAYS)), List.of(), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALL_DAY);
        assertThat(verdict.missingAttributes()).containsExactly("grossWeightKg");
    }

    @Test
    void reT4_missingAttributeOfANonMatchingRuleIsNotReported() {
        VehicleFacts carNoGross = new VehicleFacts(CAR, PETROL, null, 500, 5);
        RuleSelector heavyTrucks = selector(List.of(TRUCK), null,
                RuleSelector.WeightMeasure.GROSS, kg(3500, null, null, null), null, null);

        ZoneVerdict verdict = evaluate(carNoGross, List.of(rule(heavyTrucks, ALWAYS)), List.of(), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALLOWED);
        assertThat(verdict.missingAttributes()).isEmpty();
    }

    // ---------- RE-T5 / RE-T6 / RE-T7 schedules ----------

    @Test
    void reT5_allowedOnlyAcrossMidnight_isTheComplement() {
        assertThat(ScheduleNormalizer.forbidden(allowedOnly("21:00", "06:00")))
                .containsExactly(iv(360, 1260));
    }

    @Test
    void reT6_forbiddenDuringAcrossMidnight_isSplit() {
        assertThat(ScheduleNormalizer.forbidden(during("22:00", "02:00")))
                .containsExactly(iv(0, 120), iv(1320, 1440));
    }

    @Test
    void reT6_endOfDayAndMidnightEnds() {
        assertThat(ScheduleNormalizer.forbidden(during("22:00", "24:00"))).containsExactly(iv(1320, 1440));
        assertThat(ScheduleNormalizer.forbidden(during("22:00", "00:00"))).containsExactly(iv(1320, 1440));
        assertThat(ScheduleNormalizer.forbidden(during("00:00", "24:00"))).containsExactly(iv(0, 1440));
    }

    @Test
    void reT7_overlappingRulesAreUnitedAndMerged() {
        List<RuleView> rules = List.of(
                rule(1L, ANY_VEHICLE, during("06:00", "09:00"), LocalDate.of(2027, 1, 1), null),
                rule(2L, ANY_VEHICLE, during("08:00", "10:00", "16:00", "19:30"), LocalDate.of(2027, 1, 1), null));

        ZoneVerdict verdict = evaluate(PETROL_TRUCK_4000, rules, List.of(), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.PARTIAL);
        assertThat(verdict.forbidden()).containsExactly(iv(360, 600), iv(960, 1170));
    }

    @Test
    void reT7_rulesCoveringTheWholeDayTogetherGiveAllDay() {
        List<RuleView> rules = List.of(
                rule(1L, ANY_VEHICLE, during("00:00", "12:00"), LocalDate.of(2027, 1, 1), null),
                rule(2L, ANY_VEHICLE, during("12:00", "24:00"), LocalDate.of(2027, 1, 1), null));

        assertThat(evaluate(PETROL_TRUCK_4000, rules, List.of(), WEDNESDAY).kind())
                .isEqualTo(ZoneVerdict.Kind.ALL_DAY);
    }

    // ---------- RE-T8 permits ----------

    @Test
    void reT8_wholeDayPermit_lifts_everything() {
        PermitView wholeDay = new PermitView(41L, null, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        ZoneVerdict verdict = evaluate(PETROL_TRUCK_4000, List.of(rule(ANY_VEHICLE, ALWAYS)),
                List.of(wholeDay), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALLOWED);
        assertThat(verdict.forbidden()).isEmpty();
        assertThat(verdict.appliedPermitIds()).containsExactly(41L);
    }

    @Test
    void reT8_nightPermitOnAlwaysForbidden_leavesTheDayForbidden() {
        PermitView night = new PermitView(42L, allowedOnly("21:00", "06:00"),
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        ZoneVerdict verdict = evaluate(PETROL_TRUCK_4000, List.of(rule(ANY_VEHICLE, ALWAYS)),
                List.of(night), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.PARTIAL);
        assertThat(verdict.forbidden()).containsExactly(iv(360, 1260));
        assertThat(verdict.appliedPermitIds()).containsExactly(42L);
    }

    @Test
    void reT8_expiredPermitIsIgnored() {
        PermitView expired = new PermitView(43L, null, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 3, 2));

        ZoneVerdict verdict = evaluate(PETROL_TRUCK_4000, List.of(rule(ANY_VEHICLE, ALWAYS)),
                List.of(expired), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALL_DAY);
        assertThat(verdict.appliedPermitIds()).isEmpty();
    }

    @Test
    void reT8_permitValidityBoundsAreInclusive() {
        PermitView oneDay = new PermitView(44L, null, WEDNESDAY, WEDNESDAY);

        assertThat(evaluate(PETROL_TRUCK_4000, List.of(rule(ANY_VEHICLE, ALWAYS)), List.of(oneDay), WEDNESDAY)
                .kind()).isEqualTo(ZoneVerdict.Kind.ALLOWED);
    }

    // ---------- RE-T9 Hanoi-like rules (design §3.5) as fixtures ----------

    private static List<RuleView> hanoiLikeRules() {
        LocalDate from = LocalDate.of(2027, 1, 1);
        List<EnergyType> fossil = List.of(PETROL, DIESEL);
        return List.of(
                rule(1L, selector(List.of(MOTORBIKE), fossil, null, null, null, true), ALWAYS, from, null),
                rule(2L, selector(List.of(MOTORBIKE), fossil, null, null, 3, null), ALWAYS, from, null),
                rule(3L, selector(List.of(CAR), fossil, null, null, 4, null), ALWAYS, from, null),
                rule(4L, selector(List.of(TRUCK), null, RuleSelector.WeightMeasure.GROSS,
                        kg(3500, null, null, null), null, null), ALWAYS, from, null),
                rule(5L, selector(List.of(TRUCK), null, RuleSelector.WeightMeasure.GROSS,
                        kg(null, 2000, null, 3500), null, null), allowedOnly("21:00", "06:00"), from, null),
                rule(6L, selector(List.of(TRUCK), null, RuleSelector.WeightMeasure.GROSS,
                        kg(null, null, 2000, null), null, null),
                        during("06:00", "09:00", "16:00", "19:30"), from, null));
    }

    @Test
    void reT9_petrolTruckGross4000_isBannedAllDay() {
        assertThat(evaluate(PETROL_TRUCK_4000, hanoiLikeRules(), List.of(), WEDNESDAY).kind())
                .isEqualTo(ZoneVerdict.Kind.ALL_DAY);
    }

    @Test
    void reT9_truckGross1800_isBannedInPeakHours() {
        VehicleFacts smallTruck = new VehicleFacts(TRUCK, DIESEL, 1800, 800, 5);

        ZoneVerdict verdict = evaluate(smallTruck, hanoiLikeRules(), List.of(), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.PARTIAL);
        assertThat(verdict.forbidden()).containsExactly(iv(360, 540), iv(960, 1170));
        assertThat(verdict.missingAttributes()).isEmpty();
    }

    @Test
    void reT9_truckGross3000_isAllowedOnlyAtNight() {
        VehicleFacts midTruck = new VehicleFacts(TRUCK, DIESEL, 3000, 1500, 5);

        assertThat(evaluate(midTruck, hanoiLikeRules(), List.of(), WEDNESDAY).forbidden())
                .containsExactly(iv(360, 1260));
    }

    @Test
    void reT9_electricMotorbike_isAllowed() {
        VehicleFacts electricMotorbike = new VehicleFacts(MOTORBIKE, ELECTRIC, 150, 30, null);

        ZoneVerdict verdict = evaluate(electricMotorbike, hanoiLikeRules(), List.of(), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALLOWED);
        assertThat(verdict.missingAttributes()).isEmpty();
    }

    @Test
    void reT9_petrolMotorbikeWithoutEmissionStandard_isRestrictedAndReported() {
        VehicleFacts petrolMotorbike = new VehicleFacts(MOTORBIKE, PETROL, 150, 30, null);

        ZoneVerdict verdict = evaluate(petrolMotorbike, hanoiLikeRules(), List.of(), WEDNESDAY);

        assertThat(verdict.kind()).isEqualTo(ZoneVerdict.Kind.ALL_DAY);
        assertThat(verdict.missingAttributes()).containsExactly("emissionStandard");
    }

    @Test
    void reT9_appBasedRuleIsIgnoredForAnInternalFleet() {
        VehicleFacts compliantPetrolMotorbike = new VehicleFacts(MOTORBIKE, PETROL, 150, 30, 4);

        assertThat(RuleEvaluator.evaluate(compliantPetrolMotorbike, hanoiLikeRules(), List.of(), WEDNESDAY, false)
                .kind()).isEqualTo(ZoneVerdict.Kind.ALLOWED);
        // Control: the same vehicle under an app-based branch is caught by that rule.
        assertThat(RuleEvaluator.evaluate(compliantPetrolMotorbike, hanoiLikeRules(), List.of(), WEDNESDAY, true)
                .kind()).isEqualTo(ZoneVerdict.Kind.ALL_DAY);
    }

    @Test
    void reT9_rulesAreIgnoredBeforeTheirEffectiveDate() {
        assertThat(evaluate(PETROL_TRUCK_4000, hanoiLikeRules(), List.of(), LocalDate.of(2026, 12, 31)).kind())
                .isEqualTo(ZoneVerdict.Kind.ALLOWED);
    }

    // ---------- RE-T10 PAYLOAD ----------

    @Test
    void reT10_payloadReadsCapacityNotGrossWeight() {
        RuleSelector payloadOver1000 = selector(null, null, RuleSelector.WeightMeasure.PAYLOAD,
                kg(1000, null, null, null), null, null);
        List<RuleView> rules = List.of(rule(payloadOver1000, ALWAYS));

        VehicleFacts bigPayloadNoGross = new VehicleFacts(TRUCK, DIESEL, null, 1500, 5);
        ZoneVerdict matched = evaluate(bigPayloadNoGross, rules, List.of(), WEDNESDAY);
        assertThat(matched.kind()).isEqualTo(ZoneVerdict.Kind.ALL_DAY);
        assertThat(matched.missingAttributes()).isEmpty();

        VehicleFacts smallPayloadHeavyGross = new VehicleFacts(TRUCK, DIESEL, 9000, 500, 5);
        assertThat(evaluate(smallPayloadHeavyGross, rules, List.of(), WEDNESDAY).kind())
                .isEqualTo(ZoneVerdict.Kind.ALLOWED);

        VehicleFacts noCapacity = new VehicleFacts(TRUCK, DIESEL, 9000, null, 5);
        assertThat(evaluate(noCapacity, rules, List.of(), WEDNESDAY).missingAttributes())
                .containsExactly("capacity");
    }

    // ---------- RE-T11 validation ----------

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String text) throws Exception {
        return JSON.readTree(text);
    }

    @Test
    void reT11_startEqualsEnd_isRejected() {
        assertThatThrownBy(() -> ScheduleNormalizer.validate(during("08:00", "08:00")))
                .isInstanceOf(RuleValidationException.class);
    }

    @Test
    void reT11_badTimes_areRejected() {
        assertThatThrownBy(() -> ScheduleNormalizer.validate(during("25:00", "08:00")))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> ScheduleNormalizer.validate(during("7:00", "08:00")))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> ScheduleNormalizer.validate(during("06:60", "08:00")))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> ScheduleNormalizer.validate(during("24:00", "08:00")))
                .isInstanceOf(RuleValidationException.class);
    }

    @Test
    void reT11_missingIntervals_areRejected() {
        assertThatThrownBy(() -> ScheduleNormalizer.validate(
                new RuleSchedule(RuleSchedule.Type.FORBIDDEN_DURING, null, null)))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> ScheduleNormalizer.validate(
                new RuleSchedule(RuleSchedule.Type.ALLOWED_ONLY, null, List.of())))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> ScheduleNormalizer.validate(new RuleSchedule(null, null, null)))
                .isInstanceOf(RuleValidationException.class);
    }

    @Test
    void reT11_malformedJson_isRejected() throws Exception {
        assertThatThrownBy(() -> RuleValidator.parseSchedule(json("{\"type\":\"SOMETIMES\"}")))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> RuleValidator.parseSchedule(
                json("{\"type\":\"FORBIDDEN_DURING\",\"intervals\":[[\"06:00\"]]}")))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> RuleValidator.parseSelector(json("{\"legalClass\":[\"TRUCK\"]}")))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> RuleValidator.parseSelector(json("{\"weightKg\":{\"gt\":3500}}")))
                .isInstanceOf(RuleValidationException.class);
    }

    @Test
    void reT11_wellFormedJson_parsesToTheDesignShape() throws Exception {
        RuleSelector selector = RuleValidator.parseSelector(json("""
                {"legalClasses":["TRUCK"],"energyTypes":["PETROL","DIESEL"],
                 "weightMeasure":"GROSS","weightKg":{"gt":3500},"emissionStandardBelow":4,"appBased":true}"""));
        RuleSchedule schedule = RuleValidator.parseSchedule(json("""
                {"type":"FORBIDDEN_DURING","daysOfWeek":["MON","FRI"],
                 "intervals":[["06:00","09:00"],["16:00","19:30"]]}"""));

        assertThat(selector.legalClasses()).containsExactly(TRUCK);
        assertThat(selector.weightKg().gt()).isEqualTo(3500);
        assertThat(schedule.daysOfWeek()).containsExactly(RuleSchedule.Day.MON, RuleSchedule.Day.FRI);
        assertThat(ScheduleNormalizer.forbidden(schedule)).containsExactly(iv(360, 540), iv(960, 1170));
    }

    @Test
    void reT11_effectiveRangeAndRuleCount_areChecked() {
        assertThatThrownBy(() -> RuleValidator.validateEffectiveRange(
                LocalDate.of(2027, 2, 1), LocalDate.of(2027, 1, 31)))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> RuleValidator.validateEffectiveRange(null, null))
                .isInstanceOf(RuleValidationException.class);
        RuleValidator.validateEffectiveRange(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1));

        RuleValidator.validateRuleCount(RuleValidator.MAX_RULES_PER_ZONE);
        assertThatThrownBy(() -> RuleValidator.validateRuleCount(RuleValidator.MAX_RULES_PER_ZONE + 1))
                .isInstanceOf(RuleValidationException.class);
    }

    @Test
    void jsonFormat_staysCamelCaseUnderTheSnakeCaseWebMapper() throws Exception {
        ObjectMapper snake = new ObjectMapper()
                .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
        RuleSelector selector = selector(List.of(TRUCK), List.of(PETROL), RuleSelector.WeightMeasure.GROSS,
                kg(3500, null, null, null), 4, null);
        RuleSchedule schedule = new RuleSchedule(RuleSchedule.Type.FORBIDDEN_DURING,
                List.of(RuleSchedule.Day.MON), pairs("06:00", "09:00"));

        String selectorJson = snake.writeValueAsString(selector);
        String scheduleJson = snake.writeValueAsString(schedule);

        assertThat(selectorJson).contains("\"legalClasses\"", "\"weightMeasure\"", "\"emissionStandardBelow\"")
                .doesNotContain("legal_classes", "appBased");
        assertThat(scheduleJson).contains("\"daysOfWeek\"");
        assertThat(snake.readValue(selectorJson, RuleSelector.class)).isEqualTo(selector);
        assertThat(snake.readValue(scheduleJson, RuleSchedule.class)).isEqualTo(schedule);
        assertThat(snake.writeValueAsString(new MinuteInterval(360, 540))).isEqualTo("[360,540]");
    }

    @Test
    void reT11_alwaysForbiddenPermitSchedule_isRejected() throws Exception {
        assertThatThrownBy(() -> RuleValidator.parsePermitSchedule(json("{\"type\":\"ALWAYS_FORBIDDEN\"}")))
                .isInstanceOf(RuleValidationException.class);
        assertThat(RuleValidator.parsePermitSchedule(null)).isNull();
    }
}
