package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EngineOrderDTO {
    private Long id;
    private String orderCode;

    private Double latitude;
    private Double longitude;

    private BigDecimal demand;

    private LocalTime timeWindowStart;
    private LocalTime timeWindowEnd;

    private Integer serviceTime;

    private Integer priority;
}
