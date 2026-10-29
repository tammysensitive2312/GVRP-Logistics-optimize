package org.truong.gvrp_engine_api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One turn-by-turn instruction of a routed leg (design §16.2-4).
 *
 * <p>Produced only by the legs endpoint; optimisation results carry the encoded
 * polyline alone to keep the callback payload small.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LegInstruction {
    private String text;
    private Double distanceM;
    private Long timeMs;
    private Integer sign;
    private String streetName;
}
