package org.truong.gvrp_entry_api.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.dto.response.DriverDTO;
import org.truong.gvrp_entry_api.dto.response.DriverRouteDTO;
import org.truong.gvrp_entry_api.dto.response.RouteSendDTO;
import org.truong.gvrp_entry_api.service.RouteSendService;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DriverController {
    private final RouteSendService routeSendService;

    /** Branch driver directory (PLANNER only). */
    @GetMapping("/api/v1/drivers")
    public ResponseEntity<List<DriverDTO>> listDrivers() {
        return ResponseEntity.ok(routeSendService.listDrivers());
    }

    /** Driver's own sends, CURRENT first (DRIVER only). */
    @GetMapping("/api/v1/driver/routes")
    public ResponseEntity<List<RouteSendDTO>> myRoutes() {
        return ResponseEntity.ok(routeSendService.mySends());
    }

    /** Full detail of one of the driver's own sends (DRIVER only). */
    @GetMapping("/api/v1/driver/routes/{sendId}")
    public ResponseEntity<DriverRouteDTO> routeDetail(@PathVariable Long sendId) {
        return ResponseEntity.ok(routeSendService.sendDetail(sendId));
    }
}
