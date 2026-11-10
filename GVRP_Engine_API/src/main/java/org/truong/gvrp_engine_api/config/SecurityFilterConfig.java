package org.truong.gvrp_engine_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.truong.gvrp_engine_api.security.ServiceKeyFilter;

/**
 * Registers {@link ServiceKeyFilter} on every path, ahead of all other filters (SEC-ENGINE-001).
 */
@Configuration
public class SecurityFilterConfig {

    @Bean
    public FilterRegistrationBean<ServiceKeyFilter> serviceKeyFilter(
            @Value("${gvrp.security.inbound-key:}") String inboundKey) {
        FilterRegistrationBean<ServiceKeyFilter> registration =
                new FilterRegistrationBean<>(new ServiceKeyFilter(inboundKey));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
