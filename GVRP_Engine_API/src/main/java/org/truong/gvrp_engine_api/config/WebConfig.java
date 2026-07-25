package org.truong.gvrp_engine_api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:8080")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    @Bean
    JsonMapper objectMapper() {
        return JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    /**
     * Read timeout phải đủ cho Entry ghi xong toàn bộ solution.
     *
     * <p>Mốc 10 giây cũ là nguyên nhân lỗi job #23: Entry nhận và lưu thành công
     * 10k orders, nhưng mất hơn 10s nên engine bỏ cuộc với {@code Read timed out}
     * và tưởng là thất bại. Ghi vài chục nghìn dòng route_stops mất hàng chục giây
     * là chuyện bình thường, không phải sự cố.
     *
     * <p>Override được bằng {@code entry.callback.read-timeout-ms} nếu cần.
     */
    @Bean
    RestTemplate restTemplate(
            JsonMapper objectMapper,
            @Value("${entry.callback.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${entry.callback.read-timeout-ms:300000}") int readTimeoutMs) {
        RestTemplate restTemplate = new RestTemplate();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        restTemplate.setRequestFactory(requestFactory);

        JacksonJsonHttpMessageConverter jacksonConverter =
                new JacksonJsonHttpMessageConverter(objectMapper);

        restTemplate.getMessageConverters().removeIf(c -> c instanceof JacksonJsonHttpMessageConverter);
        restTemplate.getMessageConverters().add(jacksonConverter);

        return restTemplate;
    }
}