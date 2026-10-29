package org.truong.gvrp_entry_api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.BranchZoneCreateRequest;
import org.truong.gvrp_entry_api.dto.request.BranchZoneUpdateRequest;
import org.truong.gvrp_entry_api.dto.request.ZoneAcknowledgeRequest;
import org.truong.gvrp_entry_api.dto.request.ZoneRuleInputDTO;
import org.truong.gvrp_entry_api.dto.response.BranchZoneDTO;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetails;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.lez.ZoneService;
import org.truong.gvrp_entry_api.service.lez.ZoneVerdict;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The caller's branch zones (design §6, §7). The branch always comes from the
 * authenticated principal; PLANNER only through SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
public class BranchZoneController {
    private final ZoneService zoneService;

    @GetMapping
    public ResponseEntity<List<BranchZoneDTO>> listZones() {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneService.listZones(branchId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchZoneDTO> getZone(@PathVariable Long id) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneService.getZone(id, branchId));
    }

    @PostMapping
    public ResponseEntity<BranchZoneDTO> createZone(@RequestBody BranchZoneCreateRequest request) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.createZone(request, branchId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BranchZoneDTO> updateZone(@PathVariable Long id,
                                                    @RequestBody BranchZoneUpdateRequest request) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneService.updateZone(id, request, branchId));
    }

    /** GeoJSON Polygon, MultiPolygon or Feature; COPIED zones only. */
    @PutMapping("/{id}/geometry")
    public ResponseEntity<BranchZoneDTO> replaceGeometry(@PathVariable Long id, @RequestBody JsonNode geoJson) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneService.replaceGeometry(id, geoJson, branchId));
    }

    /** Replaces every rule of a COPIED zone. */
    @PutMapping("/{id}/rules")
    public ResponseEntity<BranchZoneDTO> replaceRules(@PathVariable Long id,
                                                      @RequestBody List<ZoneRuleInputDTO> rules) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneService.replaceRules(id, rules, branchId));
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<BranchZoneDTO> acknowledge(@PathVariable Long id,
                                                     @RequestBody ZoneAcknowledgeRequest request) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneService.acknowledge(id, request, branchId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable Long id) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        zoneService.deleteZone(id, branchId);
        return ResponseEntity.noContent().build();
    }

    /** Rule verdict for one vehicle on one date (ISO {@code yyyy-MM-dd}). */
    @GetMapping("/{id}/verdict")
    public ResponseEntity<ZoneVerdict> verdict(@PathVariable Long id,
                                               @RequestParam(required = false) Long vehicleId,
                                               @RequestParam(required = false) String date) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneService.verdict(id, vehicleId, parseDate(date), branchId));
    }

    private static LocalDate parseDate(String date) {
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new DataInvalidException(List.of(ErrorDetails.validation("date must be yyyy-MM-dd")));
        }
    }
}
