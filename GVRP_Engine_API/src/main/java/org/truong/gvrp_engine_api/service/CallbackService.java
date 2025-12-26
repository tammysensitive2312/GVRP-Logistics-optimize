package org.truong.gvrp_engine_api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.truong.gvrp_engine_api.model.OptimizationResult;
import org.truong.gvrp_engine_api.model.Stop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.optimization.entry.url}")
    private String entryApiBaseUrl;

    @Value("${entry.api-key}")
    private String apiKey;

    public void sendCompletionCallback(Long jobId, OptimizationResult result) {
        String url = entryApiBaseUrl + "/solutions/callbacks/complete";

        log.info("📤 Sending completion callback for job #{} to {}", jobId, url);

        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("solution", convertToSolutionData(result));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            try {
                String jsonPayload = objectMapper.writeValueAsString(payload);
                log.info("Payload JSON for job #{}:\n{}", jobId, jsonPayload);
            } catch (Exception jsonEx) {
                log.error("Failed to serialize payload for logging: {}", jsonEx.getMessage());
            }

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Completion callback sent successfully for job #{}", jobId);
            } else {
                log.warn("⚠️  Unexpected callback response: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Failed to send completion callback for job #{}: {}",
                    jobId, e.getMessage(), e);
            // Don't throw - callback failure shouldn't break optimization
        }
    }

    /**
     * Send error callback to Entry API
     */
    public void sendFailureCallback(Long jobId, String errorMessage) {
        String url = entryApiBaseUrl + "/solutions/callbacks/failed";

        log.info("📤 Sending failure callback for job #{} to {}", jobId, url);

        try {
            HttpHeaders headers = createHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("external_job_id", "engine-" + jobId);
            payload.put("error_message", errorMessage);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    request,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Failure callback sent successfully for job #{}", jobId);
            } else {
                log.warn("⚠️  Unexpected callback response: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Failed to send failure callback for job #{}: {}",
                    jobId, e.getMessage(), e);
        }
    }

    private Map<String, Object> convertToSolutionData(OptimizationResult result) {
        Map<String, Object> solutionData = new HashMap<>();

        solutionData.put("total_distance", result.getTotalDistance());
        solutionData.put("total_cost", result.getTotalCost());
        solutionData.put("total_co2", result.getTotalCO2());
        solutionData.put("total_time", result.getTotalTime());
        solutionData.put("total_vehicles_used", result.getTotalVehiclesUsed());
        solutionData.put("served_orders", result.getTotalOrdersServed());
        solutionData.put("unserved_orders", result.getTotalOrdersUnassigned());
        solutionData.put("routes", convertRoutes(result));

        return solutionData;
    }

    private List<Map<String, Object>> convertRoutes(OptimizationResult result) {
        List<Map<String, Object>> routes = new ArrayList<>();

        int routeOrder = 0;
        for (var routeDetail : result.getRoutes()) {
            routeOrder++;

            Map<String, Object> route = new HashMap<>();
            route.put("vehicle_id", routeDetail.getVehicleId());
            route.put("route_order", routeOrder);
            route.put("distance", routeDetail.getTotalDistance());
            route.put("duration", routeDetail.getTotalTime());
            route.put("co2_emission", routeDetail.getTotalCO2());
            route.put("order_count", routeDetail.getOrderCount());
            route.put("load_utilization", routeDetail.getLoadUtilization());
            route.put("stops", convertStops(routeDetail.getStops()));

            routes.add(route);
        }

        return routes;
    }

    private List<Map<String, Object>> convertStops(List<Stop> stops) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (var stop : stops) {
            Map<String, Object> stopData = new HashMap<>();

            stopData.put("sequence_number", stop.getSequenceNumber());
            stopData.put("type", stop.getType());
            stopData.put("order_id", stop.getOrderId());
            stopData.put("order_code", stop.getOrderCode());
//            stopData.put("customerName", stop.getCustomerName());
//            stopData.put("address", stop.getAddress());
            stopData.put("location_id", stop.getLocationId());
            stopData.put("location_name", stop.getLocationName());
            stopData.put("arrival_time", stop.getArrivalTime());
            stopData.put("departure_time", stop.getDepartureTime());
            stopData.put("service_time", stop.getServiceTime());
            stopData.put("wait_time", stop.getWaitTime());
            stopData.put("demand", stop.getDemand());
            stopData.put("load_after", stop.getLoadAfter());

            result.add(stopData);
        }

        return result;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);
        return headers;
    }

}
