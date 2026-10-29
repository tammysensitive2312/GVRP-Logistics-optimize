package org.truong.gvrp_engine_api.distance_matrix;

import org.truong.gvrp_engine_api.model.LegPath;

import java.util.Locale;

/**
 * Road-network path between two points, with geometry and instructions.
 *
 * <p>Separate from {@link DistanceProvider}, which only answers distance and time
 * for matrix building. The GraphHopper implementation reuses the same router and
 * profile so a drawn leg agrees with the matrix costs (design §5.2).
 */
public interface RoutePathProvider {

    /**
     * Route one leg.
     *
     * @param locale requested instruction language; the implementation may fall
     *               back to English when the router has no translation
     * @throws IllegalStateException when the leg cannot be routed
     */
    LegPath path(OptCoordinates from, OptCoordinates to, Locale locale);
}
