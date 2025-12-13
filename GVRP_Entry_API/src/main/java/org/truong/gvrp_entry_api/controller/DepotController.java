package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.DepotInputDTO;
import org.truong.gvrp_entry_api.dto.response.DepotDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.DepotService;

import java.util.List;

import static org.springframework.web.servlet.function.ServerResponse.status;

@RestController
@RequestMapping("/api/v1/depots")
@RequiredArgsConstructor
public class DepotController {
    private final DepotService depotService;

    @PostMapping
    public ResponseEntity<DepotDTO> createDepot(@RequestBody @Valid DepotInputDTO depotDTO) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        DepotDTO createdDepot = depotService.createDepot(depotDTO, branchId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDepot);
    }

    @GetMapping
    public ResponseEntity<List<DepotDTO>> getListDepot() {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        List<DepotDTO> dtos = depotService.getListDepots(branchId);
        return ResponseEntity.status(HttpStatus.OK).body(dtos);
    }
}
