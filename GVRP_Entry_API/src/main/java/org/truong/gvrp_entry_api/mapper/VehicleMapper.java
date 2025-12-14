package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.truong.gvrp_entry_api.dto.request.EngineVehicleDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleInputDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleUpdateDTO;
import org.truong.gvrp_entry_api.dto.response.VehicleDTO;
import org.truong.gvrp_entry_api.entity.Vehicle;

import java.util.List;

@Mapper(config = BaseMapperConfig.class)
public interface VehicleMapper {

    @Mapping(source = "fleet.id", target = "fleetId")
    @Mapping(source = "startDepot.id", target = "startDepotId")
    @Mapping(source = "startDepot.name", target = "startDepotName")
    @Mapping(source = "endDepot.id", target = "endDepotId")
    @Mapping(source = "endDepot.name", target = "endDepotName")
    @Mapping(source = "vehicleType.id", target = "vehicleTypeId")
    @Mapping(source = "vehicleType.typeName", target = "vehicleTypeName")
    VehicleDTO toDTO(Vehicle entity);

    @Mapping(source = "startDepot.id", target = "startDepotId")
    @Mapping(source = "endDepot.id", target = "endDepotId")
    @Mapping(source = "vehicleType.id", target = "vehicleTypeId")
    EngineVehicleDTO toEngineDTO(Vehicle entity);

    List<VehicleDTO> toDTOList(List<Vehicle> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fleet", ignore = true)
    @Mapping(target = "startDepot", ignore = true)
    @Mapping(target = "endDepot", ignore = true)
    @Mapping(target = "vehicleType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "routes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Vehicle toEntity(VehicleInputDTO dto);

    List<Vehicle> toEntityList(List<VehicleInputDTO> dtoList);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fleet", ignore = true)
    @Mapping(target = "startDepot", ignore = true)
    @Mapping(target = "endDepot", ignore = true)
    @Mapping(target = "vehicleType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "routes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDTO(VehicleInputDTO dto, @MappingTarget Vehicle entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fleet", ignore = true)
    @Mapping(target = "startDepot", ignore = true)
    @Mapping(target = "endDepot", ignore = true)
    @Mapping(target = "vehicleType", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "routes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDTO(VehicleUpdateDTO dto, @MappingTarget Vehicle entity);
}
