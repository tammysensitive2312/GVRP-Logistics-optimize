package org.truong.gvrp_entry_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@Slf4j
public class GeocodingService {

    private final RestTemplate restTemplate;

    @Value("${geocoding.goong.api-key}")
    private String apiKey;

    @Value("${geocoding.enabled:true}")
    private boolean enabled;

    @Value("${geocoding.goong.uri}")
    private String goongURI;

    public GeocodingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public static class GeocodeResult {
        public double lat;
        public double lng;

        public GeocodeResult(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }

    public GeocodeResult geocode(String address) {
        if (!enabled || address == null || address.trim().isEmpty()) {
            return null;
        }

        try {
            // Goong sử dụng param 'address' và 'api_key'
            // Sử dụng {address} placeholder để RestTemplate tự động encode tiếng Việt đúng cách
            String url = UriComponentsBuilder.fromUriString(goongURI)
                    .queryParam("address", "{address}")
                    .queryParam("api_key", apiKey)
                    .encode()
                    .toUriString();

            log.debug("Calling Goong API for address: {}", address.trim());

            // Goong trả về một Object (không phải List như LocationIQ)
            Map<String, Object> response = restTemplate.getForObject(url, Map.class, address.trim());

            if (response != null && "OK".equals(response.get("status"))) {
                var results = (java.util.List<Map<String, Object>>) response.get("results");

                if (!results.isEmpty()) {
                    Map<String, Object> firstResult = results.get(0);
                    Map<String, Object> geometry = (Map<String, Object>) firstResult.get("geometry");
                    Map<String, Object> location = (Map<String, Object>) geometry.get("location");

                    double lat = Double.parseDouble(location.get("lat").toString());
                    double lng = Double.parseDouble(location.get("lng").toString());

                    log.info("Goong success: '{}' -> ({}, {})", address.trim(), lat, lng);
                    return new GeocodeResult(lat, lng);
                }
            }

            log.warn("Goong could not find address: {}", address.trim());
            return null;

        } catch (Exception e) {
            log.error("Goong API error: {}", e.getMessage());
            return null;
        }
    }
}