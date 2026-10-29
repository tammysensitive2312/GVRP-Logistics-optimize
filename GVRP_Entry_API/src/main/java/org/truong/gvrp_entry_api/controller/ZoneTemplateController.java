package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.response.ZoneTemplateDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.lez.ZoneTemplateService;

import java.util.List;

/** Read-only system zone templates (LEZ-R01). PLANNER only through SecurityConfig. */
@RestController
@RequestMapping("/api/v1/zone-templates")
@RequiredArgsConstructor
public class ZoneTemplateController {
    private final ZoneTemplateService templateService;

    @GetMapping
    public ResponseEntity<List<ZoneTemplateDTO>> listTemplates() {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(templateService.listTemplates(branchId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneTemplateDTO> getTemplate(@PathVariable Long id) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(templateService.getTemplate(id, branchId));
    }
}
