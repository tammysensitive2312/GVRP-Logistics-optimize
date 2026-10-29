package org.truong.gvrp_engine_api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_engine_api.distance_matrix.RoutePathProvider;
import org.truong.gvrp_engine_api.model.LegInstruction;
import org.truong.gvrp_engine_api.model.LegPath;
import org.truong.gvrp_engine_api.model.RouteLegsRequest;
import org.truong.gvrp_engine_api.model.RouteLegsResponse;
import org.truong.gvrp_engine_api.service.LegsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * EB-T5/EB-T6: the {@code /legs} endpoint validates the point list and returns one
 * leg per consecutive pair. The path provider is a fake; no GraphHopper graph and
 * no Spring context.
 */
class LegsEndpointTest {

    private static final RoutePathProvider FAKE = (from, to, locale) -> new LegPath(
            "poly-" + from.latDouble() + "-" + to.latDouble(),
            250.0,
            12_000L,
            List.of(new LegInstruction("Turn right", 250.0, 12_000L, 2, "Le Loi")));

    private static OptimizationController controller(RoutePathProvider provider) {
        OptimizationController controller = new OptimizationController(null, null, null, null, null);
        ReflectionTestUtils.setField(controller, "legsService", new LegsService(provider));
        return controller;
    }

    private static RouteLegsRequest request(String locale, double... latLonPairs) {
        List<RouteLegsRequest.Point> points = new ArrayList<>();
        for (int i = 0; i + 1 < latLonPairs.length; i += 2) {
            points.add(new RouteLegsRequest.Point(latLonPairs[i], latLonPairs[i + 1]));
        }
        return new RouteLegsRequest(points, locale);
    }

    @Test
    void returnsOneLegPerConsecutivePairWithInstructions() {
        ResponseEntity<?> response = controller(FAKE).legs(
                request("vi", 21.0, 105.0, 21.1, 105.1, 21.2, 105.2));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RouteLegsResponse body = (RouteLegsResponse) response.getBody();
        assertNotNull(body);
        assertEquals(2, body.getLegs().size());
        assertEquals("vi", body.getLocale());
        assertEquals("poly-21.0-21.1", body.getLegs().get(0).getPolyline());
        assertEquals("Turn right", body.getLegs().get(0).getInstructions().get(0).getText());
        assertNull(body.getLegs().get(0).getError());
    }

    @Test
    void unknownLocaleFallsBackToEnglish() {
        ResponseEntity<?> response = controller(FAKE).legs(request("zz", 21.0, 105.0, 21.1, 105.1));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("en", ((RouteLegsResponse) response.getBody()).getLocale());
    }

    @Test
    void rejectsFewerThanTwoPoints() {
        ResponseEntity<?> response = controller(FAKE).legs(request("vi", 21.0, 105.0));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("REJECTED", ((Map<?, ?>) response.getBody()).get("status"));
    }

    @Test
    void rejectsMoreThanOneThousandAndOnePoints() {
        double[] pairs = new double[1_002 * 2];
        for (int i = 0; i < 1_002; i++) {
            pairs[i * 2] = 21.0 + i * 0.000_1;
            pairs[i * 2 + 1] = 105.0;
        }

        ResponseEntity<?> response = controller(FAKE).legs(request("vi", pairs));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void rejectsOutOfRangeLatitude() {
        ResponseEntity<?> response = controller(FAKE).legs(request("vi", 91.0, 105.0, 21.0, 105.0));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void unroutableLegKeepsTheRequestSuccessful() {
        RoutePathProvider failing = (from, to, locale) -> {
            throw new IllegalStateException("no route");
        };

        ResponseEntity<?> response = controller(failing).legs(request("vi", 21.0, 105.0, 21.1, 105.1));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        RouteLegsResponse.RouteLeg leg = ((RouteLegsResponse) response.getBody()).getLegs().get(0);
        assertNull(leg.getPolyline());
        assertEquals(LegsService.ROUTE_NOT_FOUND, leg.getError());
    }

    @Test
    void defaultsToVietnameseWhenNoLocaleIsSent() {
        assertEquals("vi", LegsService.resolveLocale(null).getLanguage());
        assertEquals("vi", LegsService.resolveLocale("vi").getLanguage());
    }
}
