package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.OrderImportService;
import org.truong.gvrp_entry_api.service.OrderService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderImportService orderImportService;
    private final OrderService orderService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importOrders(
            @ModelAttribute @Valid OrderImportRequest request) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        ImportResultDTO result = orderImportService.createOrdersByImportRequest(request, branchId);

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(
            @RequestBody @Validated(OrderInputDTO.OnCreate.class)
            OrderInputDTO inputDTO
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        OrderDTO result = orderService.createOrder(inputDTO, branchId);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderDTO>> getListOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();

        PageResponse<OrderDTO> result = orderService.getAllOrdersPaginated(branchId, page, size);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDTO> getOrderById(
            @PathVariable Long orderId
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        OrderDTO result = orderService.getOrderById(orderId, branchId);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<OrderDTO> updateOrder(
            @PathVariable Long orderId,
            @RequestBody @Validated(OrderInputDTO.OnUpdate.class) OrderInputDTO inputDTO
    ) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        OrderDTO updatedOrder = orderService.updateOrdersById(
                orderId,
                branchId,
                inputDTO
        );

        return ResponseEntity.ok(updatedOrder);
    }
}
