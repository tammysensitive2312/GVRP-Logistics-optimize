package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.truong.gvrp_entry_api.dto.request.EngineVehicleTypeDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleTypeInputDTO;
import org.truong.gvrp_entry_api.dto.response.VehicleTypeDTO;
import org.truong.gvrp_entry_api.entity.VehicleType;
import org.truong.gvrp_entry_api.entity.enums.LegalClass;
import org.truong.gvrp_entry_api.service.VehicleFeaturesService;

import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = {VehicleFeaturesService.class})
public interface VehicleTypeMapper {

    @Mapping(target = "name", source = "typeName")
    @Mapping(target = "vehicleFeatures", source = "entity", qualifiedByName = "getFeatures")
    @Mapping(target = "suggestedLegalClass", ignore = true)
    VehicleTypeDTO toDTO(VehicleType entity);

    /** Suggestion only (design §3.4); legalClass is never filled from it. */
    @AfterMapping
    default void suggestLegalClass(@MappingTarget VehicleTypeDTO dto) {
        if (dto.getVehicleFeatures() != null) {
            dto.setSuggestedLegalClass(LegalClass.suggestFor(dto.getVehicleFeatures().getCategory()));
        }
    }


    @Mapping(target = "emissionFactor", source = "entity", qualifiedByName = "getEmissionFactor")
    @Mapping(target = "skills", source = "entity", qualifiedByName = "getSkills")
    EngineVehicleTypeDTO toEngineDTO(VehicleType entity);

    List<VehicleTypeDTO> toDTOList(List<VehicleType> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "vehicleFeatures", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    VehicleType toEntity(VehicleTypeInputDTO dto);
}
