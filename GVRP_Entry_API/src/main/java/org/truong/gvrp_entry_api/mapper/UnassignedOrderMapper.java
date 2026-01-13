package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.*;
import org.truong.gvrp_entry_api.dto.request.EngineCallbackRequest;
import org.truong.gvrp_entry_api.entity.UnassignedOrder;

@Mapper(config = BaseMapperConfig.class)
public interface UnassignedOrderMapper {
    UnassignedOrder toEntity(EngineCallbackRequest.UnassignedOrderData unassignedOrderData);

    EngineCallbackRequest.UnassignedOrderData toDto(UnassignedOrder unassignedOrder);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    UnassignedOrder partialUpdate(EngineCallbackRequest.UnassignedOrderData unassignedOrderData, @MappingTarget UnassignedOrder unassignedOrder);
}