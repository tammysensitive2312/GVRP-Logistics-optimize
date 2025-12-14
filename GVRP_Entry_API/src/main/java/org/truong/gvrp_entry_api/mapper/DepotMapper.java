package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.*;
import org.truong.gvrp_entry_api.dto.request.DepotInputDTO;
import org.truong.gvrp_entry_api.dto.request.EngineDepotDTO;
import org.truong.gvrp_entry_api.dto.response.DepotDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Depot;

import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = {GeometryMapper.class})
public interface DepotMapper {
    Depot toEntity(DepotDTO depotDTO);

    @Mapping(source = "branch.id", target = "branchId")
    @Mapping(source = "branch.name", target = "branchName")
    @Mapping(source = "location", target = "latitude", qualifiedByName = "pointToLatitude")
    @Mapping(source = "location", target = "longitude", qualifiedByName = "pointToLongitude")
    DepotDTO toDTO(Depot entity);

    List<DepotDTO> toDTOList(List<Depot> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "dto.name", target = "name")
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "location", source = "dto", qualifiedByName = "createPoint")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "vehiclesStartingHere", ignore = true)
    @Mapping(target = "vehiclesEndingHere", ignore = true)
    Depot toEntity(DepotInputDTO dto, Branch branch);

    @Mapping(source = "location", target = "latitude", qualifiedByName = "pointToLatitude")
    @Mapping(source = "location", target = "longitude", qualifiedByName = "pointToLongitude")
    EngineDepotDTO toEngineDTO(Depot entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "location", source = "dto", qualifiedByName = "createPoint")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "vehiclesStartingHere", ignore = true)
    @Mapping(target = "vehiclesEndingHere", ignore = true)
    void updateEntityFromDTO(DepotInputDTO dto, @MappingTarget Depot entity);
}