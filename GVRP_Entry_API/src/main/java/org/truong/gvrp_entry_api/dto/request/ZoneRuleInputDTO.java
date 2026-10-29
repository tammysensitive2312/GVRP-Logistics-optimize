package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * One rule of a COPIED zone. {@code selector} and {@code schedule} are taken
 * as raw JSON (camelCase, design §3.1–3.2) and parsed by {@code RuleValidator},
 * so a malformed rule is a 400, not a message-conversion failure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneRuleInputDTO {
    private JsonNode selector;
    private JsonNode schedule;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String legalBasis;
    private String note;
}
