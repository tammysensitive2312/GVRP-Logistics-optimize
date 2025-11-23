package org.truong.gvrp_entry_api.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;

import java.time.LocalDate;
import java.util.List;

@Mapper(config = BaseMapperConfig.class, uses = {GeometryMapper.class})
public interface OrderMapper {

    /**
     * Chuyển đổi OrderInputDTO sang Order Entity.
     *
     * @param dto: Dữ liệu từ file import/text input.
     * @param branch: Branch entity (lấy từ DB theo branchId).
     * @param deliveryDate: Ngày giao hàng (lấy từ UI "Planning date").
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", source = "branch")
    @Mapping(target = "deliveryDate", source = "deliveryDate")
    // Sử dụng expression java để gọi phương thức createPoint của GeometryMapper
    @Mapping(target = "location", expression = "java(geometryMapper.createPoint(dto.getLatitude(), dto.getLongitude()))")
    @Mapping(target = "status", ignore = true) // Sẽ dùng @Builder.Default (SCHEDULED)
    @Mapping(target = "routeSegments", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Order toEntity(OrderInputDTO dto, Branch branch, LocalDate deliveryDate);


    // ========================================================================
    // E N T I T Y -> D T O
    // ========================================================================
    @Mapping(source = "branch.id", target = "branchId")
    @Mapping(source = "location", target = "latitude", qualifiedByName = "pointToLatitude")
    @Mapping(source = "location", target = "longitude", qualifiedByName = "pointToLongitude")
    OrderDTO toDTO(Order entity);

    List<OrderDTO> toDTOList(List<Order> entities);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "branch", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "routeSegments", ignore = true)
    @Mapping(target = "deliveryDate", source = "deliveryDate")
    @Mapping(target = "location", expression = "java(geometryMapper.createPoint(dto.getLatitude(), dto.getLongitude()))")
    Order updateEntityFromDTO(OrderInputDTO dto, @MappingTarget Order entity, LocalDate deliveryDate);
}
