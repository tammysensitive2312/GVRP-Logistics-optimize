package org.truong.gvrp_entry_api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;

/** Enable (LINKED) or copy (COPIED) a zone template for the caller's branch. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchZoneCreateRequest {
    private Long templateId;
    private BranchZoneMode mode;
}
