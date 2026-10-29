package org.truong.gvrp_engine_api.model;

import lombok.*;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Vehicle {
    private Long id;
    private String vehicleLicensePlate;
    private Long vehicleTypeId;
    private Long startDepotId;
    private Long endDepotId;
    /**
     * Skills compiled by Entry from the branch's low-emission zones (LEZ-001).
     * A vehicle is ALL_DAY-restricted in a zone exactly when it lacks that zone's skill.
     */
    private Set<String> lezSkills = new LinkedHashSet<>();
}
