package org.truong.gvrp_engine_api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One road-network leg between two consecutive stops.
 *
 * <p>{@code polyline} is a Google encoded polyline (precision 1e5, {@code [lat, lon]}
 * order, see {@code utils.PolylineEncoder}); {@code distanceM} is metres and
 * {@code timeMs} milliseconds.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LegPath {
    private String polyline;
    private Double distanceM;
    private Long timeMs;
    private List<LegInstruction> instructions;
}
