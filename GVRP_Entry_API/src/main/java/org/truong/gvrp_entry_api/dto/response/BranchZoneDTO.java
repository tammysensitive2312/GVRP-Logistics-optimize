package org.truong.gvrp_entry_api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;

import java.util.List;

/**
 * A zone of the caller's branch. For LINKED, name, geometry and rules come
 * from the template's current version; for COPIED, from the branch's copy.
 * {@code warnings} is present only on responses to writes.
 */
@Getter
@Setter
@Builder
public class BranchZoneDTO {
    private Long id;
    private BranchZoneMode mode;
    private Long templateId;
    private String templateCode;
    private String name;
    private Boolean enabled;
    private Integer effectiveVersion;
    private Integer sourceTemplateVersion;
    private Integer acknowledgedTemplateVersion;
    private Integer templateVersion;
    private Boolean updateAvailable;
    private JsonNode geometry;
    private List<ZoneRuleDTO> rules;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ZoneWarningDTO> warnings;
}
