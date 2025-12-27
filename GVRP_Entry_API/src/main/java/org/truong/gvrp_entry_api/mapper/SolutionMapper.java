package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.truong.gvrp_entry_api.dto.response.SolutionDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Solution;

@Mapper(config = BaseMapperConfig.class, uses = {RouteMapper.class})
public interface SolutionMapper {

    @Mapping(source = "job.id", target = "jobId")
    @Mapping(source = "branch.id", target = "branchId")
    @Mapping(source = "routes", target = "routes")
    SolutionDetailResponseDTO toDTO(Solution entity);
}
