package org.truong.gvrp_entry_api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchZoneUpdateRequest {
    private Boolean enabled;
}
