package org.truong.gvrp_entry_api.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Permit of a vehicle for a branch zone; {@code schedule} null = the whole day. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehiclePermitInputDTO {
    private Long branchZoneId;
    private String permitNo;
    private JsonNode schedule;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String note;
}
