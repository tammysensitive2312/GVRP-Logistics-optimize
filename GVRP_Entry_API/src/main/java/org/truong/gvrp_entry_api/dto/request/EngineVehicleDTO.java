package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineVehicleDTO {
    private Long id;
    private String vehicleLicensePlate;

    private Long vehicleTypeId;
    private Long startDepotId;
    private Long endDepotId;

    /**
     * LEZ skills compiled by Entry ({@code LEZ_<zoneKey>}): one per enabled zone
     * the vehicle is <em>not</em> {@code ALL_DAY} restricted in. Omitted when
     * empty, so jobs without zones send exactly the previous payload.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Set<String> lezSkills = new LinkedHashSet<>();
}
