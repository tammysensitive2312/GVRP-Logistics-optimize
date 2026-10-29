package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the solver and the compliance checker need about the branch's
 * enabled low-emission zones on one service date (LEZ-001, design §4, §16.2-6).
 *
 * <p>Produced by {@link RestrictionCompiler} and stored verbatim in
 * {@code optimization_jobs.lez_snapshot}. It carries no law and no behaviour by
 * itself: {@code vehicleRestrictions} is exactly the {@link ZoneVerdict}
 * (permits included) of every vehicle against every enabled zone, so the checker
 * never re-reads rules.
 *
 * <p>Intervals are minute pairs of the day {@code [start, end)} and are kept as
 * {@code int[]} so the snapshot round-trips through Jackson without a custom
 * creator for {@link MinuteInterval}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompiledRestrictions {

    public static final int COMPILER_VERSION = 1;

    public static final String REASON_DEPOT_IN_ZONE_ALL_DAY = "DEPOT_IN_ZONE_ALL_DAY";
    public static final String WARNING_MISSING_ATTRIBUTE = "MISSING_ATTRIBUTE";

    private LocalDate serviceDate;

    private Integer compilerVersion = COMPILER_VERSION;

    /** Only enabled zones, ascending by branch zone id. */
    private List<CompiledZone> zones = new ArrayList<>();

    /** One entry per vehicle × enabled zone, including {@code ALLOWED}. */
    private List<VehicleRestriction> vehicleRestrictions = new ArrayList<>();

    private Map<Long, List<String>> orderZones = new LinkedHashMap<>();

    private Map<Long, List<String>> depotZones = new LinkedHashMap<>();

    private List<UnusableVehicle> unusableVehicles = new ArrayList<>();

    private List<CompilerWarning> warnings = new ArrayList<>();

    /** SHA-256 over the canonical restrictions; {@code STALE} is derived by comparing it. */
    private String hash;

    @JsonIgnore
    public boolean hasZones() {
        return zones != null && !zones.isEmpty();
    }

    /** Zone keys covering an order; empty when the order is outside every enabled zone. */
    public List<String> orderZoneKeys(Long orderId) {
        return orderZones != null && orderId != null
                ? orderZones.getOrDefault(orderId, List.of()) : List.of();
    }

    public List<String> depotZoneKeys(Long depotId) {
        return depotZones != null && depotId != null
                ? depotZones.getOrDefault(depotId, List.of()) : List.of();
    }

    /** The verdict of one vehicle in one zone, or null when either is not compiled. */
    public VehicleRestriction restriction(Long vehicleId, String zoneKey) {
        if (vehicleRestrictions == null || vehicleId == null || zoneKey == null) {
            return null;
        }
        for (VehicleRestriction restriction : vehicleRestrictions) {
            if (vehicleId.equals(restriction.getVehicleId()) && zoneKey.equals(restriction.getZoneKey())) {
                return restriction;
            }
        }
        return null;
    }

    public CompiledZone zone(String zoneKey) {
        if (zones == null || zoneKey == null) {
            return null;
        }
        for (CompiledZone zone : zones) {
            if (zoneKey.equals(zone.getKey())) {
                return zone;
            }
        }
        return null;
    }

    @JsonIgnore
    public Set<Long> unusableVehicleIds() {
        Set<Long> ids = new LinkedHashSet<>();
        if (unusableVehicles != null) {
            for (UnusableVehicle vehicle : unusableVehicles) {
                if (vehicle.getVehicleId() != null) {
                    ids.add(vehicle.getVehicleId());
                }
            }
        }
        return ids;
    }

    /** Skill Entry adds for a zone key ({@code LEZ_<key>}), design §5.1. */
    public static String skillOf(String zoneKey) {
        return "LEZ_" + zoneKey;
    }

    /** One enabled zone as compiled: identity, effective version and geometry. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompiledZone {
        /** Stable key {@code Z<branchZoneId>}; the suffix of the engine skill. */
        private String key;
        private Long branchZoneId;
        private BranchZoneMode mode;
        private String name;
        private Long templateId;
        /** LINKED: the template's current version; COPIED: the version it was copied from. */
        private Integer templateVersion;
        /** GeoJSON {@code MultiPolygon}, x = longitude, y = latitude. */
        private String geometry;
    }

    /**
     * Verdict of one vehicle in one zone, flattened from {@link ZoneVerdict}.
     * {@code kind} is {@code ALLOWED}, {@code ALL_DAY} or {@code PARTIAL}.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehicleRestriction {
        private Long vehicleId;
        private String zoneKey;
        private String kind;
        /** Forbidden {@code [start, end)} minute pairs; the whole day for {@code ALL_DAY}. */
        private List<int[]> forbidden = new ArrayList<>();
        private List<String> missingAttributes = new ArrayList<>();
        private List<Long> appliedPermitIds = new ArrayList<>();

        @JsonIgnore
        public boolean isAllDay() {
            return ZoneVerdict.Kind.ALL_DAY.name().equals(kind);
        }
    }

    /** A vehicle that cannot run on the service date because of where it is parked. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UnusableVehicle {
        private Long vehicleId;
        private String reason;
        private String zoneKey;
    }

    /** Non-blocking data gap that makes a rule apply: a missing legal attribute. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompilerWarning {
        private Long vehicleTypeId;
        private String code;
        private String attribute;
    }
}
