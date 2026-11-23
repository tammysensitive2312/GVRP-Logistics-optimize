package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.VehicleInputDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleUpdateDTO;
import org.truong.gvrp_entry_api.dto.response.PageResponse;
import org.truong.gvrp_entry_api.dto.response.VehicleDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.VehicleService;
import org.truong.gvrp_entry_api.util.AppConstant;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Validated
public class VehicleController {
    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<PageResponse<VehicleDTO>> getListVehicles(
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page index must not be negative") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Page size must be between 1 and " + AppConstant.MAX_VEHICLE_PAGE_SIZE)
            @Max(value = AppConstant.MAX_VEHICLE_PAGE_SIZE,
                    message = "Page size must be between 1 and " + AppConstant.MAX_VEHICLE_PAGE_SIZE) int size
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        PageResponse<VehicleDTO> result = vehicleService.getAllVehiclesPaginated(branchId, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<VehicleDTO> createVehicle(
            @Valid @RequestBody VehicleInputDTO input,
            @RequestParam Long fleetId) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();
        VehicleDTO vehicle = vehicleService.createVehicle(input, fleetId, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicle);
    }

    /**
     * Update vehicle (can update features)
     * PUT /api/vehicles/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<VehicleDTO> updateVehicle(
            @PathVariable Long id,
            @Valid @RequestBody VehicleUpdateDTO update) {

        Long branchId = CurrentUserUtil.getCurrentBranchId();
        VehicleDTO vehicle = vehicleService.updateVehicle(id, update, branchId);
        return ResponseEntity.ok(vehicle);
    }
}
