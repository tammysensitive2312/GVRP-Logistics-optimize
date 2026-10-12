package org.truong.gvrp_entry_api.dto.request;

import lombok.Data;

@Data
public class SendRouteRequest {
    /** Receiving driver (users.id, role DRIVER, same branch). */
    private Long driverUserId;
    /** Solution revision being sent; must own the route. */
    private Long solutionId;
}
