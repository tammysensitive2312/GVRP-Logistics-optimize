package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.truong.gvrp_entry_api.dto.response.StopDetailResponseDTO;
import org.truong.gvrp_entry_api.entity.RouteStop;

import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = {GeometryMapper.class})
public interface RouteStopMapper {

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "location", target = "latitude", qualifiedByName = "pointToLatitude")
    @Mapping(source = "location", target = "longitude", qualifiedByName = "pointToLongitude")
    StopDetailResponseDTO toDTO(RouteStop entity);

    List<StopDetailResponseDTO> toDTOList(List<RouteStop> entities);
}
