package org.truong.gvrp_entry_api.service.orders;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.Order;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;
import org.truong.gvrp_entry_api.service.GeocodingService;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final GeocodingService geocodingService;
    private final BranchRepository branchRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public PageResponse<OrderDTO> getAllOrdersPaginated(Long branchId, int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").descending());
        Page<Order> orderPage = orderRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable);
        List<OrderDTO> content = orderMapper.toDTOList(orderPage.getContent());

        return PageResponse.<OrderDTO>builder()
                .content(content)
                .pageNo(orderPage.getNumber())
                .pageSize(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .last(orderPage.isLast())
                .build();
    }

    public OrderDTO updateOrdersById(
            Long orderId,
            Long branchId,
            OrderInputDTO inputDTO) {

        validateBusinessLogic(inputDTO);

        Order order = orderRepository.findByIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new DataInvalidException(
                        List.of(
                                ErrorDetail.builder()
                                        .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                        .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                        .resource(AppConstant.ORDER)
                                        .build()
                        )
                ));

        order = orderMapper.updateEntityFromDTO(inputDTO, order);
        orderRepository.save(order);
        OrderDTO orderDTO = orderMapper.toDTO(order);
        return orderDTO;
    }

    public OrderDTO createOrder(
            OrderInputDTO input,
            Long branchId
    ) {
        validateBusinessLogic(input);
        Branch branch = branchRepository.findById(branchId).orElseThrow(() -> new DataInvalidException(
                List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.BRANCH)
                                .build()
                )
        ));

        Order order = orderMapper.toEntity(input, branch, input.getDeliveryDate());
        Order saved = orderRepository.save(order);
        return orderMapper.toDTO(saved);
    }

    public OrderDTO getOrderById(Long orderId, Long branchId) {
        Order order = orderRepository.findByIdAndBranchId(orderId, branchId).orElseThrow(() -> new DataInvalidException(
                List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.RESOURCE_NOT_FOUND.getCode())
                                .message(ErrorCode.RESOURCE_NOT_FOUND.getMessage())
                                .resource(AppConstant.ORDER)
                                .build()
                )
        ));

        return orderMapper.toDTO(order);
    }

    public void validateBusinessLogic(OrderInputDTO dto) {

        boolean hasCoordinates = dto.getLatitude() != null && dto.getLongitude() != null;
        boolean hasAddress = dto.getAddress() != null && !dto.getAddress().trim().isEmpty();

        if (!hasCoordinates && !hasAddress) {
            throw new DataInvalidException(
                    List.of(
                            ErrorDetail.builder()
                                    .code(ErrorCode.VALIDATION_ERROR.getCode())
                                    .message("You must provide coordinates (latitude/longitude) OR a detailed address.")
                                    .resource(AppConstant.ORDER)
                                    .build()
                    ));
        }

        if (dto.getTimeWindowStart() != null && dto.getTimeWindowEnd() != null) {
            if (dto.getTimeWindowStart().isAfter(dto.getTimeWindowEnd())) {
                throw new DataInvalidException(
                        List.of(
                                ErrorDetail.builder()
                                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                                        .message("Time Window start time cannot be after the end time.")
                                        .resource(AppConstant.ORDER)
                                        .build()
                        ));
            }
        } else if (dto.getTimeWindowStart() != null || dto.getTimeWindowEnd() != null) {
            List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message("Time Window must have both start and end times, or neither.")
                            .resource(AppConstant.ORDER)
                            .build()
            );
        }
    }
}
