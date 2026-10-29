package org.truong.gvrp_entry_api.service.lez;

import org.locationtech.jts.geom.MultiPolygon;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;
import org.truong.gvrp_entry_api.repository.BranchZoneRuleRepository;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;

import java.util.List;

/**
 * The single place that decides which rules and which geometry a branch zone
 * currently has (LEZ-001, design §3.3, §6).
 *
 * <p>A {@code LINKED} zone reads the template's rules at the template's current
 * version; a {@code COPIED} zone reads its own {@code branch_zone_rule} rows and
 * is never overwritten. Extracted from {@code ZoneService} unchanged so the
 * P1a behaviour and its tests stay identical, and so {@code RestrictionCompiler}
 * cannot drift from the verdict endpoint.
 *
 * <p>Package-private, takes the repositories as parameters: no new dependency in
 * the existing service, whose tests construct it with its historical constructor.
 */
final class ZoneRuleLoader {

    private ZoneRuleLoader() {
    }

    static List<ZoneRuleTemplate> currentTemplateRules(ZoneTemplate template,
                                                       ZoneRuleTemplateRepository ruleTemplateRepository) {
        return ruleTemplateRepository.findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(
                template.getId(), template.getVersion());
    }

    static List<RuleView> ruleViews(BranchZone zone,
                                    Long branchId,
                                    ZoneRuleTemplateRepository ruleTemplateRepository,
                                    BranchZoneRuleRepository zoneRuleRepository) {
        if (zone.getMode() == BranchZoneMode.LINKED) {
            return currentTemplateRules(zone.getSourceTemplate(), ruleTemplateRepository).stream()
                    .map(LezSupport::toRuleView)
                    .toList();
        }
        return zoneRuleRepository.findByBranchZoneIdAndBranchIdOrderByIdAsc(zone.getId(), branchId).stream()
                .map(LezSupport::toRuleView)
                .toList();
    }

    static MultiPolygon effectiveGeometry(BranchZone zone) {
        return zone.getMode() == BranchZoneMode.LINKED ? zone.getSourceTemplate().getGeometry() : zone.getGeometry();
    }
}
