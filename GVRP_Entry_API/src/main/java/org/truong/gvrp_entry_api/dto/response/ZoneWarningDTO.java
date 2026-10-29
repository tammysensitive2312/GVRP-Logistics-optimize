package org.truong.gvrp_entry_api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Configuration warning on a zone write, e.g. {@code DEPOT_IN_ZONE} (LEZ-R09). */
@Getter
@Setter
@Builder
public class ZoneWarningDTO {
    public static final String DEPOT_IN_ZONE = "DEPOT_IN_ZONE";

    private String code;
    private Long depotId;
}
