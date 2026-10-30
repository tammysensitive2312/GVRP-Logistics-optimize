package org.truong.gvrp_entry_api.mapper;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.truong.gvrp_entry_api.dto.request.DepotInputDTO;
import org.truong.gvrp_entry_api.exception.DataInvalidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryMapperTest {

    private final GeometryMapper mapper = new GeometryMapper();

    @Test
    void createPoint_validCoordinates_returnsPointWithSrid4326() {
        DepotInputDTO dto = new DepotInputDTO("Depot A", "1 Trang Tien, Ha Noi", 21.02, 105.85);

        Point point = mapper.createPoint(dto);

        assertThat(point).isNotNull();
        assertThat(point.getY()).isEqualTo(21.02);
        assertThat(point.getX()).isEqualTo(105.85);
        assertThat(point.getSRID()).isEqualTo(4326);
    }

    @Test
    void createPoint_latitudeOutOfRange_throwsWithOneLatitudeDetail() {
        DepotInputDTO dto = new DepotInputDTO("Depot A", "1 Trang Tien, Ha Noi", 95.0, 105.85);

        assertThatThrownBy(() -> mapper.createPoint(dto))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> {
                    DataInvalidException die = (DataInvalidException) ex;
                    assertThat(die.getErrors()).hasSize(1);
                    assertThat(die.getErrors().get(0).getField()).isEqualTo("lat");
                });
    }

    @Test
    void createPoint_longitudeOutOfRange_throwsWithOneLongitudeDetail() {
        DepotInputDTO dto = new DepotInputDTO("Depot A", "1 Trang Tien, Ha Noi", 21.02, -181.0);

        assertThatThrownBy(() -> mapper.createPoint(dto))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> {
                    DataInvalidException die = (DataInvalidException) ex;
                    assertThat(die.getErrors()).hasSize(1);
                    assertThat(die.getErrors().get(0).getField()).isEqualTo("long");
                });
    }

    @Test
    void createPoint_bothOutOfRange_throwsWithTwoDetails() {
        DepotInputDTO dto = new DepotInputDTO("Depot A", "1 Trang Tien, Ha Noi", 95.0, -181.0);

        assertThatThrownBy(() -> mapper.createPoint(dto))
                .isInstanceOf(DataInvalidException.class)
                .satisfies(ex -> {
                    DataInvalidException die = (DataInvalidException) ex;
                    assertThat(die.getErrors()).hasSize(2);
                    assertThat(die.getErrors()).extracting("field")
                            .containsExactly("lat", "long");
                });
    }

    @Test
    void createPoint_nullDto_returnsNull() {
        assertThat(mapper.createPoint((DepotInputDTO) null)).isNull();
    }

    @Test
    void createPoint_nullCoordinates_returnsNull() {
        DepotInputDTO dto = new DepotInputDTO("Depot A", "1 Trang Tien, Ha Noi", null, null);
        assertThat(mapper.createPoint(dto)).isNull();
    }
}
