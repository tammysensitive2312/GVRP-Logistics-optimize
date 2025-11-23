package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.OrderImportRequest;
import org.truong.gvrp_entry_api.dto.request.OrderInputDTO;
import org.truong.gvrp_entry_api.dto.response.ImportResultDTO;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.OrderImportService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderImportService orderImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultDTO> importOrders(
            @ModelAttribute @Valid OrderImportRequest request) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        ImportResultDTO result = orderImportService.createOrdersByImportRequest(request, branchId);

        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<PageResponse<OrderDTO>> getListOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();

        PageResponse<OrderDTO> result = orderImportService.getAllOrdersPaginated(branchId, page, size);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{orderId}")
    public ResponseEntity<OrderDTO> updateOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestBody @Valid OrderInputDTO inputDTO) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();

        OrderDTO updatedOrder = orderImportService.updateOrdersById(
                orderId,
                branchId,
                deliveryDate,
                inputDTO
        );

        return ResponseEntity.ok(updatedOrder);
    }
}
