package org.truong.gvrp_engine_api.distance_matrix;

import java.math.BigDecimal;

public record Distance(BigDecimal meters) {

    public static final Distance ZERO = new Distance(BigDecimal.ZERO);

    public static Distance ofMeters(double value) {
        return new Distance(BigDecimal.valueOf(value));
    }

    public double metersDouble() {
        return meters.doubleValue();
    }
}
