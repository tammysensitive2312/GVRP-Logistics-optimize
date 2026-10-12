package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.BulkUpdateOrderStatusRequest;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.BulkUpdateOrderStatusResponse;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.entity.enums.OrderStatus;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

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
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        PageResponse<OrderDTO> result = orderService.getAllOrdersPaginated(
                branchId, page, size, parseDate(date), parseStatus(status), search);
        return ResponseEntity.ok(result);
    }

    private java.time.LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(date.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message("Invalid date, expected yyyy-MM-dd: " + date)
                            .field("date")
                            .build()));
        }
    }

    private org.truong.gvrp_entry_api.entity.enums.OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return org.truong.gvrp_entry_api.entity.enums.OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message("Unsupported order status: " + status)
                            .field("status")
                            .build()));
        }
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

    @PatchMapping("/status")
    public ResponseEntity<BulkUpdateOrderStatusResponse> bulkUpdateStatus(
            @RequestBody @Valid BulkUpdateOrderStatusRequest request
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DataInvalidException(List.of(
                    ErrorDetail.builder()
                            .code(ErrorCode.VALIDATION_ERROR.getCode())
                            .message("Unsupported order status: " + request.getStatus())
                            .field("status")
                            .build()));
        }

        return ResponseEntity.ok(orderService.bulkUpdateStatus(branchId, request.getIds(), newStatus));
    }
}
