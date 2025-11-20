package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.Mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.truong.gvrp_entry_api.dto.request.FleetInputDTO;
import org.truong.gvrp_entry_api.dto.response.FleetDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Fleet;

import java.util.List;

/**
 * Mapper cho việc chuyển đổi giữa Fleet entity và các DTOs.
 *
 * Giả định rằng bạn đã có:
 * 1. Interface 'BaseMapperConfig' được định nghĩa ở đâu đó.
 * 2. Interface 'VehicleMapper' (được khai báo trong 'uses')
 * để xử lý việc map 'List<Vehicle>' <-> 'List<VehicleDTO>'
 * và 'List<VehicleInputDTO>' -> 'List<Vehicle>'.
 * 3. Class 'Branch' entity (được tham chiếu trong toEntity).
 */
@Mapper(config = BaseMapperConfig.class, uses = {VehicleMapper.class})
public interface FleetMapper {

    /**
     * Chuyển đổi Fleet (Entity) sang FleetDTO (Response).
     *
     * - 'branch.id' được map sang 'branchId'.
     * - 'totalCapacity' và 'vehicleCount' được map tự động
     * từ các phương thức get...() trong Entity.
     * - 'vehicles' được map bằng VehicleMapper.
     */
    @Mapping(source = "branch.id", target = "branchId")
    FleetDTO toDTO(Fleet entity);

    /**
     * Chuyển đổi một List Fleet (Entities) sang List FleetDTO (Responses).
     */
    List<FleetDTO> toDTOList(List<Fleet> entities);


    @Mapping(target = "id", ignore = true)
    @Mapping(source = "branch", target = "branch")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Fleet toEntity(FleetInputDTO dto, Branch branch);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDTO(FleetInputDTO dto, @MappingTarget Fleet entity);


    @AfterMapping
    default void linkVehicles(@MappingTarget Fleet fleet) {
        if (fleet.getVehicles() != null) {
            fleet.getVehicles().forEach(vehicle -> vehicle.setFleet(fleet));
        }
    }
}
