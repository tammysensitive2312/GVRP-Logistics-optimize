package org.truong.gvrp_entry_api.mapper;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.dto.request.DepotInputDTO;

/**
 * Helper mapper for JTS geometry objects
 * Handles conversion between JTS Point and latitude/longitude
 */
@Component
public class GeometryMapper {

    private static final Logger log = LoggerFactory.getLogger(GeometryMapper.class);
    private final GeometryFactory geometryFactory;

    public GeometryMapper() {
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    }

    /**
     * Extract latitude from JTS Point
     */
    @Named("pointToLatitude")
    public Double pointToLatitude(Point point) {
        return point != null ? point.getY() : null;
    }

    /**
     * Extract longitude from JTS Point
     */
    @Named("pointToLongitude")
    public Double pointToLongitude(Point point) {
        return point != null ? point.getX() : null;
    }

    /**
     * Create JTS Point from DepotInputDTO
     */
    @Named("createPoint")
    public Point createPoint(DepotInputDTO dto) {
        if (dto == null || dto.getLatitude() == null || dto.getLongitude() == null) {
            return null;
        }

        validateCoordinates(dto.getLatitude(), dto.getLongitude());

        Coordinate coordinate = new Coordinate(dto.getLongitude(), dto.getLatitude());
        return geometryFactory.createPoint(coordinate);
    }

    /**
     * Create JTS Point from latitude and longitude
     */
    public Point createPoint(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            log.info("Đeo on roi");
            return null;
        }

//        validateCoordinates(latitude, longitude);

        Coordinate coordinate = new Coordinate(longitude, latitude);
        return geometryFactory.createPoint(coordinate);
    }

    /**
     * Validate coordinates
     */
    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }
}
