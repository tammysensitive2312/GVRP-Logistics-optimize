package org.truong.gvrp_entry_api.service.lez;

import org.truong.gvrp_entry_api.dto.response.VehiclePermitDTO;
import org.truong.gvrp_entry_api.dto.response.ZoneRuleDTO;
import org.truong.gvrp_entry_api.dto.response.ZoneTemplateDTO;
import org.truong.gvrp_entry_api.entity.BranchZoneRule;
import org.truong.gvrp_entry_api.entity.VehiclePermit;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetails;
import org.truong.gvrp_entry_api.exception.ForbiddenException;

import java.util.List;

/** Shared guards and entity → DTO/view mapping for the LEZ services. */
final class LezSupport {

    private LezSupport() {
    }

    /** The branch comes only from the authenticated principal; none means no access. */
    static Long requireBranch(Long branchId) {
        if (branchId == null) {
            throw new ForbiddenException("No branch context");
        }
        return branchId;
    }

    static DataInvalidException invalid(String message) {
        return new DataInvalidException(List.of(ErrorDetails.validation(message)));
    }

    /** Runs a parse/validate step and turns a {@link RuleValidationException} into a 400. */
    static <T> T validated(java.util.function.Supplier<T> step) {
        try {
            return step.get();
        } catch (RuleValidationException e) {
            throw invalid(e.getMessage());
        }
    }

    static ZoneTemplateDTO.ZoneTemplateDTOBuilder templateSummary(ZoneTemplate template) {
        return ZoneTemplateDTO.builder()
                .id(template.getId())
                .code(template.getCode())
                .name(template.getName())
                .jurisdiction(template.getJurisdiction())
                .version(template.getVersion())
                .boundaryConfidence(template.getBoundaryConfidence())
                .boundarySource(template.getBoundarySource())
                .legalBasis(template.getLegalBasis());
    }

    static ZoneRuleDTO toRuleDTO(ZoneRuleTemplate rule) {
        return ZoneRuleDTO.builder()
                .id(rule.getId())
                .templateVersion(rule.getTemplateVersion())
                .selector(rule.getSelector())
                .schedule(rule.getSchedule())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .legalBasis(rule.getLegalBasis())
                .note(rule.getNote())
                .build();
    }

    static ZoneRuleDTO toRuleDTO(BranchZoneRule rule) {
        return ZoneRuleDTO.builder()
                .id(rule.getId())
                .templateVersion(rule.getTemplateVersion())
                .selector(rule.getSelector())
                .schedule(rule.getSchedule())
                .effectiveFrom(rule.getEffectiveFrom())
                .effectiveTo(rule.getEffectiveTo())
                .legalBasis(rule.getLegalBasis())
                .note(rule.getNote())
                .build();
    }

    static RuleView toRuleView(ZoneRuleTemplate rule) {
        return new RuleView(rule.getId(), rule.getSelector(), rule.getSchedule(),
                rule.getEffectiveFrom(), rule.getEffectiveTo());
    }

    static RuleView toRuleView(BranchZoneRule rule) {
        return new RuleView(rule.getId(), rule.getSelector(), rule.getSchedule(),
                rule.getEffectiveFrom(), rule.getEffectiveTo());
    }

    static PermitView toPermitView(VehiclePermit permit) {
        return new PermitView(permit.getId(), permit.getSchedule(), permit.getValidFrom(), permit.getValidTo());
    }

    static VehiclePermitDTO toPermitDTO(VehiclePermit permit) {
        return VehiclePermitDTO.builder()
                .id(permit.getId())
                .vehicleId(permit.getVehicle() != null ? permit.getVehicle().getId() : null)
                .branchZoneId(permit.getBranchZone() != null ? permit.getBranchZone().getId() : null)
                .permitNo(permit.getPermitNo())
                .schedule(permit.getSchedule())
                .validFrom(permit.getValidFrom())
                .validTo(permit.getValidTo())
                .note(permit.getNote())
                .build();
    }
}
