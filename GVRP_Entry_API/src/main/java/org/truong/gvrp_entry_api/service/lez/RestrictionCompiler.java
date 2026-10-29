package org.truong.gvrp_entry_api.service.lez;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compiles the branch's enabled low-emission zones for one service date into
 * {@link CompiledRestrictions} (LEZ-001, design §4, §16.2-6).
 *
 * <p>Read-only and branch-scoped: the branch comes from the caller (the
 * authenticated principal). Rules are the same ones the verdict endpoint uses,
 * through {@link ZoneRuleLoader}; polygons are tested in Java with JTS, so no
 * MySQL geographic predicate is involved.
 *
 * <p>The hash covers the compiled restrictions <em>and</em> the data they were
 * derived from (zone geometry and rules, vehicle legal facts, permits), so a
 * template, rule, permit, attribute or {@code enabled} change makes previously
 * certified routes {@code STALE}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestrictionCompiler {

    /** P1 serves internal fleets only, like {@code ZoneService}. */
    static final boolean BRANCH_APP_BASED = false;

    private final BranchZoneRepository zoneRepository;
    private final BranchZoneRuleRepository zoneRuleRepository;
    private final ZoneRuleTemplateRepository ruleTemplateRepository;
    private final VehiclePermitRepository permitRepository;

    /**
     * @param branchId    branch of the authenticated principal
     * @param serviceDate the job's single delivery date (D4); may be null on legacy rows
     * @param vehicles    vehicles of the job; null means none
     * @param orders      orders of the job; null means none
     */
    @Transactional(readOnly = true)
    public CompiledRestrictions compile(Long branchId, LocalDate serviceDate,
                                        List<Vehicle> vehicles, List<Order> orders) {
        LezSupport.requireBranch(branchId);
        List<Vehicle> vehicleList = vehicles != null ? vehicles : List.of();
        List<Order> orderList = orders != null ? orders : List.of();

        CompiledRestrictions result = new CompiledRestrictions();
        result.setServiceDate(serviceDate);
        result.setCompilerVersion(CompiledRestrictions.COMPILER_VERSION);

        List<BranchZone> zones = zoneRepository.findByBranchIdOrderByIdAsc(branchId).stream()
                .filter(zone -> Boolean.TRUE.equals(zone.getEnabled()))
                .toList();

        if (zones.isEmpty()) {
            // Branch without enabled zones: empty compilation, hash of empty (design §16.2-7).
            result.setHash(hash(List.of("date=" + serviceDate)));
            return result;
        }

        List<String> canonical = new ArrayList<>();
        canonical.add("date=" + serviceDate);
        canonical.add("compiler=" + CompiledRestrictions.COMPILER_VERSION);

        // ---------- zones ----------
        Map<Long, MultiPolygon> geometryById = new LinkedHashMap<>();
        Map<Long, List<RuleView>> rulesById = new HashMap<>();
        for (BranchZone zone : zones) {
            MultiPolygon geometry = ZoneRuleLoader.effectiveGeometry(zone);
            if (geometry == null) {
                // Cannot test membership without a polygon; a COPIED zone always has one.
                log.warn("Zone {} has no effective geometry; skipped", zone.getId());
                continue;
            }
            String key = "Z" + zone.getId();
            List<RuleView> rules = ZoneRuleLoader.ruleViews(
                    zone, branchId, ruleTemplateRepository, zoneRuleRepository);
            geometryById.put(zone.getId(), geometry);
            rulesById.put(zone.getId(), rules);

            CompiledRestrictions.CompiledZone compiled = new CompiledRestrictions.CompiledZone();
            compiled.setKey(key);
            compiled.setBranchZoneId(zone.getId());
            compiled.setMode(zone.getMode());
            compiled.setName(zone.getMode() == org.truong.gvrp_entry_api.entity.enums.BranchZoneMode.LINKED
                    ? zone.getSourceTemplate().getName() : zone.getName());
            compiled.setTemplateId(zone.getSourceTemplate().getId());
            compiled.setTemplateVersion(zone.getMode() == org.truong.gvrp_entry_api.entity.enums.BranchZoneMode.LINKED
                    ? zone.getSourceTemplate().getVersion() : zone.getSourceTemplateVersion());
            compiled.setGeometry(GeoJsonGeometryParser.toGeoJson(geometry).toString());
            result.getZones().add(compiled);

            canonical.add("zone=" + key
                    + "|" + zone.getId()
                    + "|" + zone.getMode()
                    + "|" + compiled.getTemplateId()
                    + "|" + compiled.getTemplateVersion()
                    + "|" + compiled.getGeometry());
            for (RuleView rule : rules) {
                canonical.add("rule=" + key
                        + "|" + rule.selector()
                        + "|" + rule.schedule()
                        + "|" + rule.effectiveFrom()
                        + "|" + rule.effectiveTo());
            }
        }

        if (!geometryById.isEmpty()) {
            // ---------- per vehicle and zone ----------
            Set<String> warnings = new TreeSet<>();
            for (Vehicle vehicle : sortedVehicles(vehicleList)) {
                VehicleFacts facts = VehicleFacts.of(vehicle.getVehicleType());
                Long typeId = vehicle.getVehicleType() != null ? vehicle.getVehicleType().getId() : null;
                canonical.add("facts=" + vehicle.getId() + "|" + facts);

                boolean unusable = false;
                for (BranchZone zone : zones) {
                    MultiPolygon geometry = geometryById.get(zone.getId());
                    if (geometry == null) {
                        continue;
                    }
                    String key = "Z" + zone.getId();
                    List<PermitView> permits = permitsOf(vehicle, zone, branchId);
                    for (PermitView permit : permits) {
                        canonical.add("permit=" + key + "|" + vehicle.getId()
                                + "|" + permit.id()
                                + "|" + permit.schedule()
                                + "|" + permit.validFrom()
                                + "|" + permit.validTo());
                    }

                    ZoneVerdict verdict = RuleEvaluator.evaluate(
                            facts, rulesById.get(zone.getId()), permits, serviceDate, BRANCH_APP_BASED);

                    CompiledRestrictions.VehicleRestriction restriction =
                            new CompiledRestrictions.VehicleRestriction();
                    restriction.setVehicleId(vehicle.getId());
                    restriction.setZoneKey(key);
                    restriction.setKind(verdict.kind().name());
                    for (MinuteInterval interval : verdict.forbidden()) {
                        restriction.getForbidden().add(new int[]{interval.start(), interval.end()});
                    }
                    restriction.setMissingAttributes(new ArrayList<>(verdict.missingAttributes()));
                    restriction.setAppliedPermitIds(new ArrayList<>(verdict.appliedPermitIds()));
                    result.getVehicleRestrictions().add(restriction);

                    canonical.add("vr=" + vehicle.getId() + "|" + key + "|" + verdict);
                    for (String attribute : verdict.missingAttributes()) {
                        if (typeId != null) {
                            warnings.add(typeId + "|" + CompiledRestrictions.WARNING_MISSING_ATTRIBUTE
                                    + "|" + attribute);
                        }
                    }

                    if (!unusable && verdict.kind() == ZoneVerdict.Kind.ALL_DAY
                            && (covers(geometry, depotPoint(vehicle.getStartDepot()))
                            || covers(geometry, depotPoint(vehicle.getEndDepot())))) {
                        CompiledRestrictions.UnusableVehicle entry = new CompiledRestrictions.UnusableVehicle();
                        entry.setVehicleId(vehicle.getId());
                        entry.setReason(CompiledRestrictions.REASON_DEPOT_IN_ZONE_ALL_DAY);
                        entry.setZoneKey(key);
                        result.getUnusableVehicles().add(entry);
                        unusable = true;
                    }
                }
            }

            for (String warning : warnings) {
                String[] parts = warning.split("\\|", -1);
                CompiledRestrictions.CompilerWarning entry = new CompiledRestrictions.CompilerWarning();
                entry.setVehicleTypeId(Long.valueOf(parts[0]));
                entry.setCode(parts[1]);
                entry.setAttribute(parts[2]);
                result.getWarnings().add(entry);
            }

            // ---------- membership ----------
            for (Order order : orderList) {
                List<String> keys = keysCovering(geometryById, order.getLocation());
                if (!keys.isEmpty()) {
                    result.getOrderZones().put(order.getId(), keys);
                    canonical.add("order=" + order.getId() + "|" + keys);
                }
            }
            Map<Long, Depot> depots = new TreeMap<>();
            for (Vehicle vehicle : vehicleList) {
                putDepot(depots, vehicle.getStartDepot());
                putDepot(depots, vehicle.getEndDepot());
            }
            for (Depot depot : depots.values()) {
                List<String> keys = keysCovering(geometryById, depot.getLocation());
                if (!keys.isEmpty()) {
                    result.getDepotZones().put(depot.getId(), keys);
                    canonical.add("depot=" + depot.getId() + "|" + keys);
                }
            }
            for (CompiledRestrictions.UnusableVehicle entry : result.getUnusableVehicles()) {
                canonical.add("unusable=" + entry.getVehicleId() + "|" + entry.getZoneKey() + "|" + entry.getReason());
            }
        }

        for (CompiledRestrictions.VehicleRestriction restriction : result.getVehicleRestrictions()) {
            // Already appended during the loop; keep the list deterministic for the hash.
            restriction.setForbidden(sortedIntervals(restriction.getForbidden()));
        }
        result.getVehicleRestrictions().sort(Comparator
                .comparing(CompiledRestrictions.VehicleRestriction::getVehicleId,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(CompiledRestrictions.VehicleRestriction::getZoneKey,
                        Comparator.nullsFirst(Comparator.naturalOrder())));

        List<String> sortedCanonical = new ArrayList<>(canonical);
        sortedCanonical.sort(Comparator.naturalOrder());
        result.setHash(hash(sortedCanonical));
        return result;
    }

    /** Convenience for callers that already hold the date but no vehicle yet. */
    public CompiledRestrictions compileEmpty(Long branchId, LocalDate serviceDate) {
        return compile(branchId, serviceDate, List.of(), List.of());
    }

    // ---------- helpers ----------

    private List<PermitView> permitsOf(Vehicle vehicle, BranchZone zone, Long branchId) {
        return permitRepository.findByVehicleIdAndBranchZoneIdAndBranchId(
                        vehicle.getId(), zone.getId(), branchId).stream()
                .map(LezSupport::toPermitView)
                .toList();
    }

    private static List<Vehicle> sortedVehicles(List<Vehicle> vehicles) {
        List<Vehicle> sorted = new ArrayList<>(vehicles);
        sorted.sort(Comparator.comparing(Vehicle::getId,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return sorted;
    }

    private static List<String> keysCovering(Map<Long, MultiPolygon> geometryById, Point point) {
        if (point == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (Map.Entry<Long, MultiPolygon> entry : geometryById.entrySet()) {
            if (GeoJsonGeometryParser.covers(entry.getValue(), point)) {
                keys.add("Z" + entry.getKey());
            }
        }
        keys.sort(Comparator.naturalOrder());
        return keys;
    }

    private static void putDepot(Map<Long, Depot> depots, Depot depot) {
        if (depot != null && depot.getId() != null) {
            depots.putIfAbsent(depot.getId(), depot);
        }
    }

    private static Point depotPoint(Depot depot) {
        return depot != null ? depot.getLocation() : null;
    }

    private static boolean covers(MultiPolygon geometry, Point point) {
        return GeoJsonGeometryParser.covers(geometry, point);
    }

    private static List<int[]> sortedIntervals(List<int[]> intervals) {
        List<int[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.<int[]>comparingInt(a -> a[0]).thenComparingInt(a -> a[1]));
        return sorted;
    }

    static String hash(List<String> canonicalLines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String line : canonicalLines) {
                digest.update(line.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // Every JRE is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** Canonical form of the zones only, for tests and diagnostics. */
    static List<String> zoneLines(CompiledRestrictions restrictions) {
        List<String> lines = new ArrayList<>();
        for (CompiledRestrictions.CompiledZone zone : restrictions.getZones()) {
            lines.add("zone=" + zone.getKey() + "|" + zone.getBranchZoneId() + "|" + zone.getMode()
                    + "|" + zone.getTemplateId() + "|" + zone.getTemplateVersion() + "|" + zone.getGeometry());
        }
        return lines;
    }
}
