package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.VehicleInputDTO;
import org.truong.gvrp_entry_api.dto.request.VehicleUpdateDTO;
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
    public ResponseEntity<PageResponse<VehicleDTO>> getListVehicles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        PageResponse<VehicleDTO> result = vehicleService.getAllVehiclesPaginated(branchId, page, size);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<VehicleDTO> createVehicle(
            @Valid @RequestBody VehicleInputDTO input,
            @RequestParam Long fleetId) {

        VehicleDTO vehicle = vehicleService.createVehicle(input, fleetId);
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

        VehicleDTO vehicle = vehicleService.updateVehicle(id, update);
        return ResponseEntity.ok(vehicle);
    }
}
