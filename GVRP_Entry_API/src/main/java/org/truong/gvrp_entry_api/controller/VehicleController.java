package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.dto.response.VehicleDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.VehicleService;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {
    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<PageResponse<VehicleDTO>> getListOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        PageResponse<VehicleDTO> result = vehicleService.getAllVehiclesPaginated(branchId, page, size);
        return ResponseEntity.ok(result);
    }
}
