package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineDepotDTO {
    private Long id;
    private String name;
    private String address;

    private Double latitude;
    private Double longitude;

}
