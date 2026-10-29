package org.truong.gvrp_entry_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One turn-by-turn instruction of a leg, as the planner and driver clients see it
 * (LEZ-001, design §16.2-4). Deliberately independent of the engine DTO so the
 * public contract does not follow engine refactors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteLegInstructionDTO {
    /** Human text in the language the engine produced (Vietnamese when available). */
    private String text;
    private Double distanceM;
    private Long timeMs;
    /** GraphHopper turn sign; 0 for "continue". */
    private Integer sign;
    private String streetName;
}
