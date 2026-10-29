package org.truong.gvrp_engine_api.service;

import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;
import org.truong.gvrp_engine_api.distance_matrix.RoutePathProvider;
import org.truong.gvrp_engine_api.model.LegPath;
import org.truong.gvrp_engine_api.model.RouteLegsRequest;
import org.truong.gvrp_engine_api.model.RouteLegsResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the per-leg geometry and instructions of a route (design §16.2-4).
 *
 * <p>The optimisation result carries only encoded polylines; Entry calls this at
 * send time, and only when a route still lacks geometry or instructions.
 *
 * <p>Locale: GraphHopper 11 serves {@code vi_VN} (registered under both
 * {@code vi} and {@code vi_VN}); an unknown locale falls back to English, and the
 * response echoes the locale actually used, as the language tag only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegsService {

    /** Minimum and maximum points of one request (2 points = 1 leg). */
    public static final int MIN_POINTS = 2;
    public static final int MAX_POINTS = 1001;

    public static final String DEFAULT_LOCALE = "vi";
    public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";

    private static final TranslationMap TRANSLATIONS = new TranslationMap().doImport();

    private final RoutePathProvider routePathProvider;

    public RouteLegsResponse build(RouteLegsRequest request) {
        List<RouteLegsRequest.Point> points = validate(request);
        Locale locale = resolveLocale(request.getLocale());

        List<RouteLegsResponse.RouteLeg> legs = new ArrayList<>(points.size() - 1);
        for (int i = 0; i + 1 < points.size(); i++) {
            RouteLegsRequest.Point from = points.get(i);
            RouteLegsRequest.Point to = points.get(i + 1);
            try {
                LegPath path = routePathProvider.path(coordinates(from), coordinates(to), locale);
                legs.add(new RouteLegsResponse.RouteLeg(
                        path.getPolyline(), path.getDistanceM(), path.getTimeMs(),
                        path.getInstructions(), null));
            } catch (RuntimeException e) {
                // No coordinates in the log (AGENTS.md §15); the request still succeeds.
                log.warn("Leg {} could not be routed ({})", i, e.getClass().getSimpleName());
                legs.add(new RouteLegsResponse.RouteLeg(null, null, null, null, ROUTE_NOT_FOUND));
            }
        }

        return new RouteLegsResponse(locale.getLanguage(), legs);
    }

    /** Resolves a requested tag to a translation the router really has. */
    public static Locale resolveLocale(String requested) {
        String tag = requested == null || requested.isBlank() ? DEFAULT_LOCALE : requested.trim();
        Locale locale = Locale.forLanguageTag(tag);
        if (locale.getLanguage().isEmpty()) {
            locale = Locale.forLanguageTag(DEFAULT_LOCALE);
        }
        Translation translation = TRANSLATIONS.getWithFallBack(locale);
        return translation != null ? translation.getLocale() : Locale.ENGLISH;
    }

    private static List<RouteLegsRequest.Point> validate(RouteLegsRequest request) {
        if (request == null || request.getPoints() == null) {
            throw new IllegalArgumentException("Points are required");
        }
        List<RouteLegsRequest.Point> points = request.getPoints();
        if (points.size() < MIN_POINTS) {
            throw new IllegalArgumentException("At least " + MIN_POINTS + " points are required");
        }
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("At most " + MAX_POINTS + " points are allowed");
        }
        for (int i = 0; i < points.size(); i++) {
            RouteLegsRequest.Point point = points.get(i);
            if (point == null || point.getLatitude() == null || point.getLongitude() == null) {
                throw new IllegalArgumentException("Point " + i + " is missing coordinates");
            }
            double lat = point.getLatitude();
            double lon = point.getLongitude();
            if (!Double.isFinite(lat) || lat < -90 || lat > 90
                    || !Double.isFinite(lon) || lon < -180 || lon > 180) {
                throw new IllegalArgumentException("Point " + i + " is outside the latitude/longitude range");
            }
        }
        return points;
    }

    private static OptCoordinates coordinates(RouteLegsRequest.Point point) {
        return new OptCoordinates(
                BigDecimal.valueOf(point.getLatitude()),
                BigDecimal.valueOf(point.getLongitude()));
    }
}
