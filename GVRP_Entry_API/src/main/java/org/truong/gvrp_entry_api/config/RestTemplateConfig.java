package org.truong.gvrp_entry_api.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // Read timeout covers a full route evaluation round-trip; the engine
        // used to need minutes for O(n^2) matrices (see ROUTE-CUSTOM-002).
        // Evaluate is O(n) now, but keep headroom for large dispatcher plans.
        return builder
                .connectTimeout(Duration.ofMinutes(1))
                .readTimeout(Duration.ofMinutes(3))
                .build();
    }
}
