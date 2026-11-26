package org.truong.gvrp_engine_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.truong.gvrp_engine_api.exception.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Inbound service authentication for every Engine endpoint (SEC-010, SEC-ENGINE-001).
 *
 * <p>Default deny: a request passes only with a correct {@code X-Engine-Key} header. The single
 * exemption is {@code GET} on exactly {@value #HEALTH_PATH}; any other spelling of that path
 * (encoded, trailing slash, double slash, path parameter) is not exempt, so the check fails closed
 * instead of trusting a raw-URI prefix (the trap behind SEC-012).
 *
 * <p>With no configured key every protected request is refused. Wrong and missing keys get the same
 * 401, and the presented key is never logged.
 */
@Slf4j
public class ServiceKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Engine-Key";
    public static final String HEALTH_PATH = "/api/v1/optimization/health";
    static final int MIN_KEY_LENGTH = 32;
    private static final String UNAUTHORIZED_BODY = "{\"errors\":[{\"code\":\""
            + ErrorCode.UNAUTHORIZED.getCode()
            + "\",\"message\":\""
            + ErrorCode.UNAUTHORIZED.getMessage()
            + "\"}]}";

    private final byte[] expected;

    public ServiceKeyFilter(String configuredKey) {
        String key = configuredKey == null ? "" : configuredKey.trim();
        this.expected = key.getBytes(StandardCharsets.UTF_8);
        if (key.isEmpty()) {
            log.error("Engine inbound key (gvrp.security.inbound-key / GVRP_ENGINE_INBOUND_KEY) is not "
                    + "configured; every request except GET {} will be refused", HEALTH_PATH);
        } else if (key.length() < MIN_KEY_LENGTH) {
            log.warn("Engine inbound key is shorter than {} characters", MIN_KEY_LENGTH);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isHealthCheck(request) || hasValidKey(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(UNAUTHORIZED_BODY);
    }

    private static boolean isHealthCheck(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && HEALTH_PATH.equals(request.getRequestURI());
    }

    private boolean hasValidKey(String presented) {
        if (expected.length == 0 || presented == null || presented.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }
}
