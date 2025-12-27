package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.truong.gvrp_entry_api.dto.response.RouteDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.Route;

import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = {RouteStopMapper.class})
public interface RouteMapper {

    @Mapping(source = "vehicle.id", target = "vehicleId")
    @Mapping(source = "vehicle.vehicleLicensePlate", target = "vehicleLicensePlate")
    @Mapping(source = "segments", target = "stops")
    // Sử dụng custom expression để gọi các business method trong Entity của bạn
    @Mapping(target = "startTime", expression = "java(entity.getEstimatedStartTime())")
    @Mapping(target = "endTime", expression = "java(entity.getEstimatedEndTime())")
    RouteDetailResponseDTO toDTO(Route entity);

    List<RouteDetailResponseDTO> toDTOList(List<Route> entities);
}
