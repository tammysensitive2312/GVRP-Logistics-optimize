package org.truong.gvrp_entry_api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.truong.gvrp_entry_api.dto.request.FleetInputDTO;
import org.truong.gvrp_entry_api.dto.response.FleetDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.FleetService;

@RestController
@RequestMapping("/api/v1/fleets") // Đặt URL base
@RequiredArgsConstructor
public class FleetController {

    private final FleetService fleetService;

    /**
     * API để tạo một Fleet mới cho một Branch.
     * @param fleetInputDTO Dữ liệu của Fleet (và các Vehicles), lấy từ Request Body.
     * @return FleetDTO vừa được tạo.
     */
    @PostMapping
    public ResponseEntity<FleetDTO> createFleet(
            @Valid @RequestBody FleetInputDTO fleetInputDTO) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        FleetDTO createdFleet = fleetService.createFleet(fleetInputDTO, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdFleet);
    }

    @GetMapping
    public ResponseEntity<FleetDTO> getFleet() {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        FleetDTO fleetDTO = fleetService.getFleetByBranchId(branchId);
        return ResponseEntity.ok(fleetDTO);
    }
}
