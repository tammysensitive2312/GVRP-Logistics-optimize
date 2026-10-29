package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.ZoneRuleTemplate;

import java.util.List;

public interface ZoneRuleTemplateRepository extends JpaRepository<ZoneRuleTemplate, Long> {

    /** Rules of one template version. */
    List<ZoneRuleTemplate> findByZoneTemplateIdAndTemplateVersionOrderByIdAsc(Long zoneTemplateId,
                                                                              Integer templateVersion);
}
