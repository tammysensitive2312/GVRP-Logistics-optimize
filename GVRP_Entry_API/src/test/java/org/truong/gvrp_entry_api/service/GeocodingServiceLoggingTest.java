package org.truong.gvrp_entry_api.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * UO-T8 (UX-ORDERS-001, S3): geocoding log lines carry no address, no
 * coordinate and no API key or URL (AGENTS.md section 15).
 */
class GeocodingServiceLoggingTest {

    private static final String ADDRESS = "1 Trang Tien, Ha Noi";
    private static final String KEY = "test-key-not-real";
    private static final String URI = "https://geocoder.example.test/geocode";

    private RestTemplate restTemplate;
    private GeocodingService service;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new GeocodingService(restTemplate);
        ReflectionTestUtils.setField(service, "apiKey", KEY);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "goongURI", URI);

        logger = (Logger) LoggerFactory.getLogger(GeocodingService.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    private void assertNothingSensitiveLogged() {
        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            String message = event.getFormattedMessage();
            assertThat(message)
                    .doesNotContain("Trang Tien")
                    .doesNotContain("21.0245")
                    .doesNotContain("105.8572")
                    .doesNotContain(KEY)
                    .doesNotContain("geocoder.example.test");
            assertThat(event.getThrowableProxy()).isNull();
        }
    }

    @Test
    void uoT8_success_logsNoAddressNoCoordinates() {
        when(restTemplate.getForObject(anyString(), eq(Map.class), anyString())).thenReturn(Map.of(
                "status", "OK",
                "results", List.of(Map.of("geometry", Map.of("location", Map.of("lat", 21.0245, "lng", 105.8572))))));

        GeocodingService.GeocodeResult result = service.geocode(ADDRESS);

        assertThat(result).isNotNull();
        assertThat(result.lat).isEqualTo(21.0245);
        assertNothingSensitiveLogged();
    }

    @Test
    void uoT8_noResult_logsNoAddress() {
        when(restTemplate.getForObject(anyString(), eq(Map.class), anyString()))
                .thenReturn(Map.of("status", "ZERO_RESULTS"));

        assertThat(service.geocode(ADDRESS)).isNull();
        assertNothingSensitiveLogged();
    }

    @Test
    void uoT8_failure_logsNeitherTheUrlNorTheKey() {
        when(restTemplate.getForObject(anyString(), eq(Map.class), anyString()))
                .thenThrow(new RestClientException("I/O error on GET request for \"" + URI
                        + "?address=" + ADDRESS + "&api_key=" + KEY + "\""));

        assertThat(service.geocode(ADDRESS)).isNull();
        assertNothingSensitiveLogged();
        assertThat(appender.list).anyMatch(event -> event.getFormattedMessage().contains("RestClientException"));
    }
}
