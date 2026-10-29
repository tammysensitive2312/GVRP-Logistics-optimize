package org.truong.gvrp_entry_api.service.lez;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.MultiPolygon;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.BranchZoneCreateRequest;
import org.truong.gvrp_entry_api.dto.request.BranchZoneUpdateRequest;
import org.truong.gvrp_entry_api.dto.request.ZoneAcknowledgeRequest;
import org.truong.gvrp_entry_api.dto.request.ZoneRuleInputDTO;
import org.truong.gvrp_entry_api.dto.response.BranchZoneDTO;
import org.truong.gvrp_entry_api.dto.response.ZoneRuleDTO;
import org.truong.gvrp_entry_api.dto.response.ZoneWarningDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.BranchZoneRule;
import org.truong.gvrp_entry_api.entity.Depot;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRepository;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.DepotRepository;
import org.truong.gvrp_entry_api.repository.VehiclePermitRepository;
import org.truong.gvrp_entry_api.repository.VehicleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.repository.ZoneTemplateRepository;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Branch zones: enable (LINKED) or copy (COPIED) a template, edit a copy,
 * acknowledge template updates, delete, and evaluate one vehicle (design §6, §7).
 *
 * <p>Tenant isolation (LEZ-R14, AGENTS.md §15): every method takes the branch
 * of the authenticated principal and reads branch data only through
 * branch-scoped queries. Another branch's zone or vehicle answers 404, exactly
 * like a missing one, before anything is written.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneService {

    /** P1 serves internal fleets only (request §1, 2026-10-25 23:24). */
    static final boolean BRANCH_APP_BASED = false;

    /** Length of {@code branch_zones.name}. */
    private static final int NAME_MAX_LENGTH = 200;

    private final BranchZoneRepository zoneRepository;
    private final BranchZoneRuleRepository zoneRuleRepository;
    private final ZoneTemplateRepository templateRepository;
    private final ZoneRuleTemplateRepository ruleTemplateRepository;
    private final VehiclePermitRepository permitRepository;
    private final VehicleRepository vehicleRepository;
    private final DepotRepository depotRepository;
    private final BranchRepository branchRepository;

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<BranchZoneDTO> listZones(Long branchId) {
        LezSupport.requireBranch(branchId);
        return zoneRepository.findByBranchIdOrderByIdAsc(branchId).stream()
                .map(zone -> toDTO(zone, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchZoneDTO getZone(Long id, Long branchId) {
        return toDTO(findZone(id, branchId), null);
    }

    // ---------- writes ----------

    @Transactional
    public BranchZoneDTO createZone(BranchZoneCreateRequest request, Long branchId) {
        LezSupport.requireBranch(branchId);
        if (request == null || request.getTemplateId() == null || request.getMode() == null) {
            throw LezSupport.invalid("templateId and mode are required");
        }
        ZoneTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new LezNotFoundException(AppConstant.ZONE_TEMPLATE));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new LezNotFoundException(AppConstant.BRANCH));

        BranchZone saved;
        if (request.getMode() == BranchZoneMode.LINKED) {
            // MySQL has no partial unique index; this check is the primary guard and
            // the generated-column unique key of the migration is the backstop.
            if (zoneRepository.existsByBranchIdAndSourceTemplateIdAndMode(
                    branchId, template.getId(), BranchZoneMode.LINKED)) {
                throw new LezConflictException("Template " + template.getId() + " is already linked to this branch");
            }
            BranchZone zone = BranchZone.builder()
                    .branch(branch)
                    .mode(BranchZoneMode.LINKED)
                    .sourceTemplate(template)
                    .enabled(Boolean.TRUE)
                    .build();
            try {
                saved = zoneRepository.saveAndFlush(zone);
            } catch (DataIntegrityViolationException e) {
                throw new LezConflictException("Template " + template.getId() + " is already linked to this branch");
            }
        } else {
            BranchZone zone = BranchZone.builder()
                    .branch(branch)
                    .mode(BranchZoneMode.COPIED)
                    .sourceTemplate(template)
                    .sourceTemplateVersion(template.getVersion())
                    .name(uniqueName(template.getName(), branchId))
                    .geometry((MultiPolygon) template.getGeometry().copy())
                    .enabled(Boolean.TRUE)
                    .build();
            saved = zoneRepository.save(zone);
            List<BranchZoneRule> copies = new ArrayList<>();
            for (ZoneRuleTemplate rule : currentTemplateRules(template)) {
                copies.add(BranchZoneRule.builder()
                        .branch(branch)
                        .branchZone(saved)
                        .templateVersion(rule.getTemplateVersion())
                        .selector(rule.getSelector())
                        .schedule(rule.getSchedule())
                        .effectiveFrom(rule.getEffectiveFrom())
                        .effectiveTo(rule.getEffectiveTo())
                        .legalBasis(rule.getLegalBasis())
                        .note(rule.getNote())
                        .build());
            }
            zoneRuleRepository.saveAll(copies);
        }
        log.info("Branch {} added zone {} ({}) from template {}", branchId, saved.getId(), saved.getMode(),
                template.getId());
        return toDTO(saved, depotWarnings(saved, branchId));
    }

    @Transactional
    public BranchZoneDTO updateZone(Long id, BranchZoneUpdateRequest request, Long branchId) {
        BranchZone zone = findZone(id, branchId);
        if (request == null || (request.getEnabled() == null && request.getName() == null)) {
            throw LezSupport.invalid("enabled or name is required");
        }
        String name = null;
        if (request.getName() != null) {
            if (zone.getMode() == BranchZoneMode.LINKED) {
                throw new LezConflictException("Zone " + zone.getId() + " is LINKED to its template and cannot be renamed");
            }
            name = request.getName().trim();
            if (name.isEmpty()) {
                throw LezSupport.invalid("name must not be blank");
            }
            if (name.length() > NAME_MAX_LENGTH) {
                throw LezSupport.invalid("name must be at most " + NAME_MAX_LENGTH + " characters");
            }
            String key = nameKey(name);
            boolean taken = zoneRepository.findByBranchIdOrderByIdAsc(branchId).stream()
                    .filter(other -> !other.getId().equals(zone.getId()))
                    .map(ZoneService::effectiveName)
                    .anyMatch(other -> other != null && nameKey(other).equals(key));
            if (taken) {
                throw LezSupport.invalid("Another zone of this branch is already named " + name);
            }
        }
        if (request.getEnabled() != null) {
            zone.setEnabled(request.getEnabled());
        }
        if (name != null) {
            zone.setName(name);
        }
        BranchZone saved = zoneRepository.save(zone);
        return toDTO(saved, Boolean.TRUE.equals(saved.getEnabled()) ? depotWarnings(saved, branchId) : List.of());
    }

    @Transactional
    public BranchZoneDTO replaceGeometry(Long id, JsonNode geoJson, Long branchId) {
        BranchZone zone = findZone(id, branchId);
        requireCopied(zone);
        zone.setGeometry(GeoJsonGeometryParser.parse(geoJson));
        BranchZone saved = zoneRepository.save(zone);
        return toDTO(saved, depotWarnings(saved, branchId));
    }

    /** Replaces every rule of a COPIED zone. All rules are validated before anything is written. */
    @Transactional
    public BranchZoneDTO replaceRules(Long id, List<ZoneRuleInputDTO> inputs, Long branchId) {
        BranchZone zone = findZone(id, branchId);
        requireCopied(zone);
        if (inputs == null) {
            throw LezSupport.invalid("A list of rules is required");
        }
        LezSupport.validated(() -> {
            RuleValidator.validateRuleCount(inputs.size());
            return null;
        });

        List<BranchZoneRule> rules = new ArrayList<>();
        for (ZoneRuleInputDTO input : inputs) {
            if (input == null) {
                throw LezSupport.invalid("A rule must not be null");
            }
            RuleSelector selector = LezSupport.validated(() -> RuleValidator.parseSelector(input.getSelector()));
            RuleSchedule schedule = LezSupport.validated(() -> RuleValidator.parseSchedule(input.getSchedule()));
            LezSupport.validated(() -> {
                RuleValidator.validateEffectiveRange(input.getEffectiveFrom(), input.getEffectiveTo());
                return null;
            });
            rules.add(BranchZoneRule.builder()
                    .branch(zone.getBranch())
                    .branchZone(zone)
                    .selector(selector)
                    .schedule(schedule)
                    .effectiveFrom(input.getEffectiveFrom())
                    .effectiveTo(input.getEffectiveTo())
                    .legalBasis(input.getLegalBasis())
                    .note(input.getNote())
                    .build());
        }

        zoneRuleRepository.deleteByZoneAndBranch(zone.getId(), branchId);
        zoneRuleRepository.saveAll(rules);
        return toDTO(zone, null);
    }

    /** Records that the planner saw the template's current version. Geometry and rules stay unchanged. */
    @Transactional
    public BranchZoneDTO acknowledge(Long id, ZoneAcknowledgeRequest request, Long branchId) {
        BranchZone zone = findZone(id, branchId);
        requireCopied(zone);
        Integer current = zone.getSourceTemplate().getVersion();
        if (request == null || request.getVersion() == null || !request.getVersion().equals(current)) {
            throw LezSupport.invalid("version must equal the template's current version " + current);
        }
        zone.setAcknowledgedTemplateVersion(current);
        return toDTO(zoneRepository.save(zone), null);
    }

    @Transactional
    public void deleteZone(Long id, Long branchId) {
        BranchZone zone = findZone(id, branchId);
        if (permitRepository.existsByBranchZoneIdAndBranchId(zone.getId(), branchId)) {
            throw new LezConflictException("Zone " + id + " is referenced by a vehicle permit");
        }
        zoneRuleRepository.deleteByZoneAndBranch(zone.getId(), branchId);
        zoneRepository.delete(zone);
    }

    // ---------- evaluation ----------

    /** Verdict for one vehicle of the caller's branch in one zone on one date. */
    @Transactional(readOnly = true)
    public ZoneVerdict verdict(Long id, Long vehicleId, LocalDate date, Long branchId) {
        BranchZone zone = findZone(id, branchId);
        if (vehicleId == null || date == null) {
            throw LezSupport.invalid("vehicleId and date are required");
        }
        Vehicle vehicle = vehicleRepository.findByIdAndFleetBranchId(vehicleId, branchId)
                .orElseThrow(() -> new LezNotFoundException(AppConstant.VEHICLE));
        List<PermitView> permits = permitRepository
                .findByVehicleIdAndBranchZoneIdAndBranchId(vehicle.getId(), zone.getId(), branchId)
                .stream().map(LezSupport::toPermitView).toList();
        return RuleEvaluator.evaluate(VehicleFacts.of(vehicle.getVehicleType()), ruleViews(zone, branchId),
                permits, date, BRANCH_APP_BASED);
    }

    // ---------- helpers ----------

    private BranchZone findZone(Long id, Long branchId) {
        LezSupport.requireBranch(branchId);
        return zoneRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new LezNotFoundException(AppConstant.BRANCH_ZONE));
    }

    /**
     * Name of a new COPIED zone that no other zone of the branch uses (BROWSER-FIX-001, E7):
     * the template name when free, else {@code "<base> (copy)"}, {@code "<base> (copy 2)"}, ...
     * The base is shortened so the result fits the column.
     */
    private String uniqueName(String base, Long branchId) {
        Set<String> taken = zoneRepository.findByBranchIdOrderByIdAsc(branchId).stream()
                .map(ZoneService::effectiveName)
                .filter(Objects::nonNull)
                .map(ZoneService::nameKey)
                .collect(Collectors.toSet());
        String safeBase = base == null ? "" : base;
        for (int n = 1; ; n++) {
            String suffix = n == 1 ? "" : n == 2 ? " (copy)" : " (copy " + (n - 1) + ")";
            String head = safeBase.length() + suffix.length() > NAME_MAX_LENGTH
                    ? safeBase.substring(0, NAME_MAX_LENGTH - suffix.length())
                    : safeBase;
            String candidate = head + suffix;
            if (!taken.contains(nameKey(candidate))) {
                return candidate;
            }
        }
    }

    /** The name a zone shows: LINKED from its template, COPIED its own. */
    private static String effectiveName(BranchZone zone) {
        if (zone.getMode() == BranchZoneMode.LINKED) {
            return zone.getSourceTemplate() != null ? zone.getSourceTemplate().getName() : null;
        }
        return zone.getName();
    }

    /** Names are compared trimmed and case-insensitive. */
    private static String nameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireCopied(BranchZone zone) {
        if (zone.getMode() != BranchZoneMode.COPIED) {
            throw new LezConflictException("Zone " + zone.getId() + " is LINKED to its template and cannot be edited");
        }
    }

    private List<ZoneRuleTemplate> currentTemplateRules(ZoneTemplate template) {
        return ZoneRuleLoader.currentTemplateRules(template, ruleTemplateRepository);
    }

    private List<RuleView> ruleViews(BranchZone zone, Long branchId) {
        return ZoneRuleLoader.ruleViews(zone, branchId, ruleTemplateRepository, zoneRuleRepository);
    }

    private static MultiPolygon effectiveGeometry(BranchZone zone) {
        return ZoneRuleLoader.effectiveGeometry(zone);
    }

    /** One {@code DEPOT_IN_ZONE} warning per depot of the branch covered by the zone (LEZ-R09). */
    private List<ZoneWarningDTO> depotWarnings(BranchZone zone, Long branchId) {
        MultiPolygon geometry = effectiveGeometry(zone);
        List<ZoneWarningDTO> warnings = new ArrayList<>();
        for (Depot depot : depotRepository.findByBranchId(branchId)) {
            if (GeoJsonGeometryParser.covers(geometry, depot.getLocation())) {
                warnings.add(ZoneWarningDTO.builder()
                        .code(ZoneWarningDTO.DEPOT_IN_ZONE)
                        .depotId(depot.getId())
                        .build());
            }
        }
        return warnings;
    }

    private BranchZoneDTO toDTO(BranchZone zone, List<ZoneWarningDTO> warnings) {
        ZoneTemplate template = zone.getSourceTemplate();
        boolean linked = zone.getMode() == BranchZoneMode.LINKED;
        List<ZoneRuleDTO> rules = linked
                ? currentTemplateRules(template).stream().map(LezSupport::toRuleDTO).toList()
                : zoneRuleRepository.findByBranchZoneIdAndBranchIdOrderByIdAsc(zone.getId(), zone.getBranch().getId())
                        .stream().map(LezSupport::toRuleDTO).toList();
        return BranchZoneDTO.builder()
                .id(zone.getId())
                .mode(zone.getMode())
                .templateId(template.getId())
                .templateCode(template.getCode())
                .name(linked ? template.getName() : zone.getName())
                .enabled(zone.getEnabled())
                .effectiveVersion(linked ? template.getVersion() : zone.getSourceTemplateVersion())
                .sourceTemplateVersion(zone.getSourceTemplateVersion())
                .acknowledgedTemplateVersion(zone.getAcknowledgedTemplateVersion())
                .templateVersion(template.getVersion())
                .updateAvailable(updateAvailable(zone))
                .geometry(GeoJsonGeometryParser.toGeoJson(effectiveGeometry(zone)))
                .rules(rules)
                .warnings(warnings)
                .build();
    }

    /** COPIED and the template moved past both the copied and the acknowledged version. */
    static boolean updateAvailable(BranchZone zone) {
        if (zone.getMode() != BranchZoneMode.COPIED) {
            return false;
        }
        int copied = zone.getSourceTemplateVersion() != null ? zone.getSourceTemplateVersion() : 0;
        int acknowledged = zone.getAcknowledgedTemplateVersion() != null ? zone.getAcknowledgedTemplateVersion() : 0;
        return zone.getSourceTemplate().getVersion() > Math.max(copied, acknowledged);
    }
}
