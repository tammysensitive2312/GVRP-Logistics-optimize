package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.VehicleTypeInputDTO;
import org.truong.gvrp_entry_api.dto.response.VehicleTypeDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.VehicleTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicle-types")
@RequiredArgsConstructor
public class VehicleTypeController {
    private final VehicleTypeService typeService;

    @PostMapping
    ResponseEntity<VehicleTypeDTO> createVehicle(
            @Valid @RequestBody VehicleTypeInputDTO input) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        VehicleTypeDTO vehicleType = typeService.createVehicleType(input, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(vehicleType);
    }

    @GetMapping
    ResponseEntity<List<VehicleTypeDTO>> getVehicleTypesByBranchId() {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        var vehicleTypes = typeService.getVehicleTypesByBranchId(branchId);
        return ResponseEntity.ok(vehicleTypes);
    }

    @PutMapping("/{id}")
    ResponseEntity<VehicleTypeDTO> updateVehicleType(
            @PathVariable Long id,
            @Valid @RequestBody VehicleTypeInputDTO input) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        VehicleTypeDTO updatedVehicleType = typeService.updateVehicleType(id, input, branchId);
        return ResponseEntity.ok(updatedVehicleType);
    }
}
