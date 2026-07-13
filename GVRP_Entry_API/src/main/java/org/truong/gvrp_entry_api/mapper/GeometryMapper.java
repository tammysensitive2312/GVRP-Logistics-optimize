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
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.ArrayList;
import java.util.List;

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
            return null;
        }
        Coordinate coordinate = new Coordinate(longitude, latitude);
        return geometryFactory.createPoint(coordinate);
    }

    /**
     * Validate coordinates
     */
    private void validateCoordinates(Double latitude, Double longitude) {
        List<ErrorDetail> errorsInfo = new ArrayList<>();
        if (latitude < -90 || latitude > 90) {
            errorsInfo.add(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message(ErrorCode.VALIDATION_ERROR.getMessage())
                            .field(AppConstant.LATITUDE)
                            .build()
            );
        }
        if (longitude < -180 || longitude > 180) {
            errorsInfo.add(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message(ErrorCode.VALIDATION_ERROR.getMessage())
                            .field(AppConstant.LONGITUDE)
                            .build()
            );
        }

        throw new DataInvalidException(errorsInfo);
    }
}
