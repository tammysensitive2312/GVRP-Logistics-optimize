package org.truong.gvrp_engine_api.distance_matrix;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public record OptCoordinates(BigDecimal lat, BigDecimal lon) {

    public String key() {
        return String.format(Locale.ENGLISH, "%s,%s", lat, lon);
    }

    public double latDouble() {
        return lat.doubleValue();
    }

    public double lonDouble() {
        return lon.doubleValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptCoordinates that = (OptCoordinates) o;
        return lat.compareTo(that.lat) == 0 && lon.compareTo(that.lon) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(lat, lon);
    }
}