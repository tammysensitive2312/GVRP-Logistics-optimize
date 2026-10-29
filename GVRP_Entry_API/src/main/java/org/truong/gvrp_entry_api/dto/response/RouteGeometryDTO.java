package org.truong.gvrp_entry_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Geometry of one leg between two consecutive stops of a route (LEZ-001, N8).
 * {@code fromSeq}/{@code toSeq} are the stored stop sequence numbers, which the
 * client uses to attach the polyline to the right pair of markers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteGeometryDTO {

    private Long routeId;

    private List<Leg> legs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Leg {
        private Integer fromSeq;
        private Integer toSeq;
        /** Encoded polyline (precision 1e5, {@code [lat, lon]}); null when not routed yet. */
        private String polyline;
        private List<RouteLegInstructionDTO> instructions;
    }
}
