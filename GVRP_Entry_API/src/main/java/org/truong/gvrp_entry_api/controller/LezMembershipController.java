package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.response.OrderZoneMembershipDTO;
import org.truong.gvrp_entry_api.security.CurrentUserUtil;
import org.truong.gvrp_entry_api.service.lez.ZoneMembershipService;

import java.util.List;

/**
 * Read-only zone membership of orders for the order table badge (LEZ-001 P1c,
 * design-p1c §2.1). PLANNER only through the existing {@code /api/v1/**} rule;
 * the branch always comes from the authenticated principal.
 */
@RestController
@RequestMapping("/api/v1/lez")
@RequiredArgsConstructor
public class LezMembershipController {

    private final ZoneMembershipService zoneMembershipService;

    @GetMapping("/order-zones")
    public ResponseEntity<List<OrderZoneMembershipDTO>> orderZones(
            @RequestParam(required = false) String orderIds) {
        Long branchId = CurrentUserUtil.getCurrentBranchId();
        return ResponseEntity.ok(zoneMembershipService.memberships(orderIds, branchId));
    }
}
