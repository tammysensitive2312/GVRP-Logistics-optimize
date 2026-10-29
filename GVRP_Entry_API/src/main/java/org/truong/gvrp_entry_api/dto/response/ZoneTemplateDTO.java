package org.truong.gvrp_entry_api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.truong.gvrp_entry_api.entity.enums.BoundaryConfidence;

import java.util.List;

/**
 * Zone template. The list returns summaries ({@code geometry} and {@code rules}
 * omitted); the detail adds the GeoJSON geometry and the current version's rules.
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZoneTemplateDTO {
    private Long id;
    private String code;
    private String name;
    private String jurisdiction;
    private Integer version;
    private BoundaryConfidence boundaryConfidence;
    private String boundarySource;
    private String legalBasis;
    private JsonNode geometry;
    private List<ZoneRuleDTO> rules;
}
