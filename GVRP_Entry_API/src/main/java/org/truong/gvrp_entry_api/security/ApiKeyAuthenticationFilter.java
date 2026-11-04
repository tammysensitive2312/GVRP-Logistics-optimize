package org.truong.gvrp_entry_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/**
 * Authenticates Engine API callbacks by the {@code X-API-Key} header.
 *
 * <p>The filter does not decide which paths need the key (SEC-012): a raw-URI
 * prefix check missed encoded variants such as {@code /api/v1/solutions/%63allbacks/}.
 * {@code SecurityConfig} requires {@code ROLE_ENGINE_SERVICE} on the decoded
 * callback path; this filter only grants that role for a valid key, refuses an
 * invalid key with 401 and leaves requests without the header untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${engine.api.key}")
    private String engineApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        // No key: nothing to authenticate; the authorization rules decide.
        if (apiKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isValidKey(apiKey)) {
            log.warn("❌ Invalid API key on {}", request.getServletPath());
            sendUnauthorizedResponse(response, "Invalid API key");
            return;
        }

        log.debug("✅ Valid API key - authenticating as ENGINE_SERVICE");

        // Create authentication token with ENGINE_SERVICE role
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "ENGINE_SERVICE",  // Principal
                null,              // Credentials
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ENGINE_SERVICE"))
        );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /** Constant-time comparison (SEC-004); an empty key never matches. */
    private boolean isValidKey(String apiKey) {
        if (apiKey.isEmpty() || engineApiKey == null || engineApiKey.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                engineApiKey.getBytes(StandardCharsets.UTF_8),
                apiKey.getBytes(StandardCharsets.UTF_8));
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
                "{\"error\": \"Unauthorized\", \"message\": \"%s\"}",
                message
        ));
    }
}