package org.truong.gvrp_entry_api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchZoneUpdateRequest {
    private Boolean enabled;
    /** New name of a COPIED zone; null keeps the current name (BROWSER-FIX-001, E7). */
    private String name;

    public BranchZoneUpdateRequest(Boolean enabled) {
        this.enabled = enabled;
    }
}
