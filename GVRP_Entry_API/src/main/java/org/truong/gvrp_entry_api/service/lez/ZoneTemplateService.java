package org.truong.gvrp_entry_api.service.lez;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.response.ZoneTemplateDTO;
import org.truong.gvrp_entry_api.entity.ZoneTemplate;
import org.truong.gvrp_entry_api.repository.ZoneRuleTemplateRepository;
import org.truong.gvrp_entry_api.repository.ZoneTemplateRepository;
import org.truong.gvrp_entry_api.util.AppConstant;

import java.util.List;

/**
 * Read-only access to system zone templates (LEZ-R01). Templates carry no
 * branch data, so every branch sees the same list; nothing here writes.
 */
@Service
@RequiredArgsConstructor
public class ZoneTemplateService {

    private final ZoneTemplateRepository templateRepository;
    private final ZoneRuleTemplateRepository ruleTemplateRepository;

    @Transactional(readOnly = true)
    public List<ZoneTemplateDTO> listTemplates(Long branchId) {
        LezSupport.requireBranch(branchId);
        return templateRepository.findAllByOrderByCodeAsc().stream()
                .map(template -> LezSupport.templateSummary(template).build())
                .toList();
    }

    /** Detail: GeoJSON geometry and the rules of the current version. */
    @Transactional(readOnly = true)
    public ZoneTemplateDTO getTemplate(Long id, Long branchId) {
        LezSupport.requireBranch(branchId);
        ZoneTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new LezNotFoundException(AppConstant.ZONE_TEMPLATE));
        return LezSupport.templateSummary(template)
                .geometry(GeoJsonGeometryParser.toGeoJson(template.getGeometry()))
                .rules(ruleTemplateRepository
                        .findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(template.getId(), template.getVersion())
                        .stream().map(LezSupport::toRuleDTO).toList())
                .build();
    }
}
