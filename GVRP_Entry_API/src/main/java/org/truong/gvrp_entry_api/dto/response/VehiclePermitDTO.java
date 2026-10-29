package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.truong.gvrp_entry_api.service.lez.RuleSchedule;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class VehiclePermitDTO {
    private Long id;
    private Long vehicleId;
    private Long branchZoneId;
    private String permitNo;
    private RuleSchedule schedule;
    private LocalDate validFrom;
    private LocalDate validTo;
    private String note;
}
