package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.truong.gvrp_entry_api.service.lez.RuleSchedule;
import org.truong.gvrp_entry_api.service.lez.RuleSelector;

import java.time.LocalDate;

/** A template rule or a branch copy rule. Selector and schedule keep their camelCase JSON. */
@Getter
@Setter
@Builder
public class ZoneRuleDTO {
    private Long id;
    private Integer templateVersion;
    private RuleSelector selector;
    private RuleSchedule schedule;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String legalBasis;
    private String note;
}
