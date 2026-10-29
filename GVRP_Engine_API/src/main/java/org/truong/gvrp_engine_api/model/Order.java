package org.truong.gvrp_engine_api.model;

import lombok.*;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private Set<String> requiredSkills = new LinkedHashSet<>();
    /**
     * Skills compiled by Entry from the branch's low-emission zones (LEZ-001).
     * Absent or empty leaves the problem exactly as before.
     */
    private Set<String> lezSkills = new LinkedHashSet<>();
}
