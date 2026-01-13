package org.truong.gvrp_engine_api.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UnassignedOrder {
    private Long orderId;
    private String orderCode;
    private String reason;
}
