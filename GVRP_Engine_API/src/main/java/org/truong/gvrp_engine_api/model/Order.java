package org.truong.gvrp_engine_api.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Order {
    private Long id;
    private String orderCode;
    private Double latitude;
    private Double longitude;
    private Double demand;
    private String timeWindowStart;
    private String timeWindowEnd;
    private Integer serviceTime;
    private Integer priority;
}
