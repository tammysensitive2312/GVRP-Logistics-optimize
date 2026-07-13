package org.truong.gvrp_engine_api.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.truong.gvrp_engine_api.distance_matrix.OptCoordinates;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class GeoUtilsTest {

    @Test
    void shouldReturnZeroDistanceForSamePoint() {

        OptCoordinates point = new OptCoordinates(
                BigDecimal.valueOf(21.028511),
                BigDecimal.valueOf(105.804817));

        double distance = GeoUtils.haversine(point, point);

        assertEquals(0.0, distance, 0.001);
    }

    @Test
    void shouldBeSymmetric() {

        OptCoordinates a = new OptCoordinates(
                BigDecimal.valueOf(21.028511),
                BigDecimal.valueOf(105.804817));

        OptCoordinates b = new OptCoordinates(
                BigDecimal.valueOf(21.033333),
                BigDecimal.valueOf(105.850000));

        double ab = GeoUtils.haversine(a, b);
        double ba = GeoUtils.haversine(b, a);

        assertEquals(ab, ba, 0.000001);
    }

    @Test
    void shouldCalculateReasonableDistanceBetweenHoGuomAndHoChiMinhMausoleum() {

        OptCoordinates hoGuom = new OptCoordinates(
                BigDecimal.valueOf(21.028511),
                BigDecimal.valueOf(105.852020));

        OptCoordinates langBac = new OptCoordinates(
                BigDecimal.valueOf(21.036871),
                BigDecimal.valueOf(105.834160));

        double distance = GeoUtils.haversine(hoGuom, langBac);

        assertTrue(distance > 1800);
        assertTrue(distance < 2200);
    }

}
