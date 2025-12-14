package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.truong.gvrp_entry_api.dto.request.EngineVehicleTypeDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleTypeInputDTO;
import org.truong.gvrp_entry_api.dto.response.VehicleTypeDTO;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.service.VehicleFeaturesService;

import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = {VehicleFeaturesService.class})
public interface VehicleTypeMapper {

    @Mapping(target = "name", source = "typeName")
    VehicleTypeDTO toDTO(VehicleType entity);


    @Mapping(target = "emissionFactor", source = "entity", qualifiedByName = "getEmissionFactor")
    EngineVehicleTypeDTO toEngineDTO(VehicleType entity);

    List<VehicleTypeDTO> toDTOList(List<VehicleType> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "vehicleFeatures", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    VehicleType toEntity(VehicleTypeInputDTO dto);
}
