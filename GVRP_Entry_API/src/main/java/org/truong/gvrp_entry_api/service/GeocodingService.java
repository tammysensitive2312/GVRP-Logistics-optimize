package org.truong.gvrp_entry_api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
public class GeocodingService {

    private final RestTemplate restTemplate;

    @Value("${geocoding.opencage.api-key}")
    private String apiKey;

    @Value("${geocoding.enabled:true}")
    private boolean enabled;

    @Value("${geocoding.opencage.uri}")
    private String opencageURI;

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

            String url = UriComponentsBuilder.fromUriString(opencageURI)
                    .queryParam("q", address)
                    .queryParam("key", apiKey)
                    .queryParam("limit", 1)
                    .queryParam("countrycode", "vn")   // Ưu tiên Việt Nam
                    .queryParam("language", "vi")       // Trả về tiếng Việt
                    .queryParam("no_annotations", 1)   // Tối ưu response nhanh hơn
                    .toUriString();

            log.debug("OpenCage Geocoding URL: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null && response.containsKey("results")) {
                var results = (java.util.List<Map<String, Object>>) response.get("results");
                if (!results.isEmpty()) {
                    Map<String, Object> geometry = (Map<String, Object>) results.get(0).get("geometry");
                    double lat = (double) geometry.get("lat");
                    double lng = (double) geometry.get("lng");
                    log.info("OpenCage Geocoded success: '{}' → ({}, {})", address.trim(), lat, lng);
                    return new GeocodeResult(lat, lng);
                }
            }

            log.warn("No result from OpenCage for address: '{}'", address.trim());
            return null;

        } catch (Exception e) {
            log.warn("OpenCage Geocoding failed for address '{}': {}", address.trim(), e.getMessage());
            return null;
        }
    }
}