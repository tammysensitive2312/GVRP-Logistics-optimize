package org.truong.gvrp_entry_api.service;

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
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.mapper.OrderMapper;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.OrderRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        // 1. Tạo đối tượng Pageable (có thể thêm Sort nếu muốn, ví dụ sort theo ID giảm dần)
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by("id").descending());

        // 2. Gọi Repository
        Page<Order> orderPage = orderRepository.findByBranchIdOrderByCreatedAtDesc(branchId, pageable);

        // 3. Map Entity sang DTO
        // Lưu ý: orderPage.getContent() trả về List<Order>
        List<OrderDTO> content = orderMapper.toDTOList(orderPage.getContent());

        // 4. Build PageResponse
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

        Order order = orderRepository.findByIdAndBranchId(orderId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Resources not found.", "order"));

        order = orderMapper.updateEntityFromDTO(inputDTO, order);
        orderRepository.save(order);
        OrderDTO orderDTO = orderMapper.toDTO(order);
        return orderDTO;
    }

    public OrderDTO createOrder(
            OrderInputDTO input,
            Long branchId
    ) {
        Branch branch = branchRepository.findById(branchId).orElseThrow(
                () -> new ResourceNotFoundException("Resources not found.", "branch")
        );

        Order order = orderMapper.toEntity(input, branch, input.getDeliveryDate());
        Order saved = orderRepository.save(order);
        return orderMapper.toDTO(saved);
    }

    public OrderDTO getOrderById(Long orderId, Long branchId) {
        Order order = orderRepository.findByIdAndBranchId(orderId, branchId).orElseThrow(
                () -> new ResourceNotFoundException("Resources not found.", "order")
        );

        return orderMapper.toDTO(order);
    }
}
