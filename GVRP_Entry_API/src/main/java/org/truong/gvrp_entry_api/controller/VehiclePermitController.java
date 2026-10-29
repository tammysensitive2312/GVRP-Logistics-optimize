package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.VehiclePermitInputDTO;
import org.truong.gvrp_entry_api.dto.response.VehiclePermitDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.lez.VehiclePermitService;

import java.util.List;

/** Permits of one vehicle of the caller's branch (LEZ-R05). PLANNER only through SecurityConfig. */
@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}/permits")
@RequiredArgsConstructor
public class VehiclePermitController {
    private final VehiclePermitService permitService;

    @GetMapping
    public ResponseEntity<List<VehiclePermitDTO>> listPermits(@PathVariable Long vehicleId) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(permitService.listPermits(vehicleId, branchId));
    }

    @PostMapping
    public ResponseEntity<VehiclePermitDTO> createPermit(@PathVariable Long vehicleId,
                                                         @RequestBody VehiclePermitInputDTO input) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.status(HttpStatus.CREATED).body(permitService.createPermit(vehicleId, input, branchId));
    }

    @PutMapping("/{permitId}")
    public ResponseEntity<VehiclePermitDTO> updatePermit(@PathVariable Long vehicleId,
                                                         @PathVariable Long permitId,
                                                         @RequestBody VehiclePermitInputDTO input) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(permitService.updatePermit(vehicleId, permitId, input, branchId));
    }

    @DeleteMapping("/{permitId}")
    public ResponseEntity<Void> deletePermit(@PathVariable Long vehicleId, @PathVariable Long permitId) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        permitService.deletePermit(vehicleId, permitId, branchId);
        return ResponseEntity.noContent().build();
    }
}
