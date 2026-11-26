package org.truong.gvrp_engine_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SEC-ENGINE-001 EN-T1…T4: default-deny service-key filter, exact health exemption, fail closed.
 */
class ServiceKeyFilterTest {

    private static final String KEY = "k".repeat(40);

    private static MockHttpServletResponse run(ServiceKeyFilter filter, MockHttpServletRequest request,
                                               MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest request(String method, String uri, String key) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (key != null) {
            request.addHeader(ServiceKeyFilter.HEADER, key);
        }
        return request;
    }

    private static void assertRefused(ServiceKeyFilter filter, MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter, request, chain);
        assertEquals(401, response.getStatus(), request.getMethod() + " " + request.getRequestURI());
        assertNull(chain.getRequest(), "chain must not be called");
        assertEquals("{\"errors\":[{\"code\":\"0040102\",\"message\":\"Unauthorized.\"}]}", response.getContentAsString());
    }

    private static void assertPassed(ServiceKeyFilter filter, MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filter, request, chain);
        assertNotNull(chain.getRequest(), "chain must be called for " + request.getRequestURI());
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingWrongEmptyOrDifferentCaseKeyIsRefused() throws Exception { // EN-T1
        ServiceKeyFilter filter = new ServiceKeyFilter(KEY);
        assertRefused(filter, request("POST", "/api/v1/optimization", null));
        assertRefused(filter, request("POST", "/api/v1/optimization", ""));
        assertRefused(filter, request("POST", "/api/v1/optimization", "wrong"));
        assertRefused(filter, request("POST", "/api/v1/optimization", KEY.toUpperCase()));
        assertRefused(filter, request("POST", "/api/v1/optimization", KEY + "x"));
        assertRefused(filter, request("GET", "/api/v1/optimization/42/result", KEY.substring(1)));
    }

    @Test
    void correctKeyPassesOnEveryEndpoint() throws Exception { // EN-T2
        ServiceKeyFilter filter = new ServiceKeyFilter(KEY);
        for (String[] call : new String[][]{
                {"POST", "/api/v1/optimization"}, {"GET", "/api/v1/optimization/42/progress"},
                {"POST", "/api/v1/optimization/42/cancel"}, {"GET", "/api/v1/optimization/42/result"},
                {"POST", "/api/v1/optimization/42/resend"}, {"POST", "/api/v1/optimization/evaluate"},
                {"POST", "/api/v1/optimization/legs"}, {"GET", "/anything/else"}}) {
            assertPassed(filter, request(call[0], call[1], KEY));
        }
    }

    @Test
    void onlyTheExactHealthGetIsExempt() throws Exception { // EN-T3
        ServiceKeyFilter filter = new ServiceKeyFilter(KEY);
        assertPassed(filter, request("GET", ServiceKeyFilter.HEALTH_PATH, null));
        for (String uri : new String[]{
                "/api/v1/optimization/health/", "/api/v1/optimization/%68ealth", "/api/v1/optimization//health",
                "/api/v1/optimization/health;x=1", "//api/v1/optimization/health", "/API/v1/optimization/health"}) {
            assertRefused(filter, request("GET", uri, null));
        }
        assertRefused(filter, request("POST", ServiceKeyFilter.HEALTH_PATH, null));
        assertRefused(filter, request("OPTIONS", ServiceKeyFilter.HEALTH_PATH, null));
    }

    @Test
    void blankConfiguredKeyRefusesEverythingButHealth() throws Exception { // EN-T4
        for (String configured : new String[]{null, "", "   "}) {
            ServiceKeyFilter filter = new ServiceKeyFilter(configured);
            assertRefused(filter, request("POST", "/api/v1/optimization", null));
            assertRefused(filter, request("POST", "/api/v1/optimization", ""));
            assertRefused(filter, request("POST", "/api/v1/optimization", "   "));
            assertPassed(filter, request("GET", ServiceKeyFilter.HEALTH_PATH, null));
        }
    }

    @Test
    void configuredKeyIsTrimmed() throws Exception {
        ServiceKeyFilter filter = new ServiceKeyFilter("  " + KEY + "\n");
        assertPassed(filter, request("POST", "/api/v1/optimization", KEY));
    }
}
