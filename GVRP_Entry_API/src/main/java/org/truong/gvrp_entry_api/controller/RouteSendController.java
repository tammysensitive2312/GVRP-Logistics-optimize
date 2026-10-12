package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.request.SendRouteRequest;
import org.truong.gvrp_entry_api.dto.response.RouteSendDTO;
import org.truong.gvrp_entry_api.service.RouteSendService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteSendController {
    private final RouteSendService routeSendService;

    /** Dispatcher sends a route revision to a driver (PLANNER only). */
    @PostMapping("/{id}/send")
    public ResponseEntity<RouteSendDTO> sendRoute(
            @PathVariable Long id, @RequestBody SendRouteRequest request) {
        return ResponseEntity.ok(routeSendService.sendRoute(id, request));
    }

    /** Send history of one route, newest first (PLANNER only). */
    @GetMapping("/{id}/sends")
    public ResponseEntity<List<RouteSendDTO>> routeSends(@PathVariable Long id) {
        return ResponseEntity.ok(routeSendService.routeSends(id));
    }
}
