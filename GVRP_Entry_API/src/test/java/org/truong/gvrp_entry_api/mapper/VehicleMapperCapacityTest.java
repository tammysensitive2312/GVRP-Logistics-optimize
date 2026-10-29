package org.truong.gvrp_entry_api.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.truong.gvrp_entry_api.dto.response.VehicleDTO;
import org.truong.gvrp_entry_api.entity.Vehicle;
import org.truong.gvrp_entry_api.entity.VehicleType;

import static org.assertj.core.api.Assertions.assertThat;

/** BF-T3 (BROWSER-FIX-001, E3): the vehicle list carries the type's capacity. */
class VehicleMapperCapacityTest {

    private final VehicleMapper mapper = Mappers.getMapper(VehicleMapper.class);

    @Test
    void bfT3_capacityComesFromTheVehicleType() {
        Vehicle vehicle = Vehicle.builder()
                .id(7L)
                .vehicleType(VehicleType.builder().id(1L).typeName("Truck").capacity(1000).build())
                .build();

        VehicleDTO dto = mapper.toDTO(vehicle);

        assertThat(dto.getCapacity()).isEqualTo(1000);
        assertThat(dto.getVehicleTypeName()).isEqualTo("Truck");
    }

    @Test
    void bfT3_noVehicleType_givesNullCapacityWithoutException() {
        VehicleDTO dto = mapper.toDTO(Vehicle.builder().id(7L).build());

        assertThat(dto.getCapacity()).isNull();
    }
}
