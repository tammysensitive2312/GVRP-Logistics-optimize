package org.truong.gvrp_entry_api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Acknowledge the template's current version for a COPIED zone. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ZoneAcknowledgeRequest {
    private Integer version;
}
