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
import java.util.Collections;
import java.util.List;

/**
 * Filter to authenticate callback requests from Engine API using API Key
 * Only applies to /api/solutions/callbacks/** endpoints
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String CALLBACK_PATH_PREFIX = "/api/solutions/callbacks/";

    @Value("${engine.api.key}")
    private String engineApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        log.debug("🔐 API Key Filter processing: {}", requestPath);

        // Only process callback endpoints
        if (!requestPath.startsWith(CALLBACK_PATH_PREFIX)) {
            log.debug("   ↪ Not a callback endpoint, skipping API key auth");
            filterChain.doFilter(request, response);
            return;
        }

        // Extract API key from header
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("❌ Missing API key for callback endpoint: {}", requestPath);
            sendUnauthorizedResponse(response, "Missing API key");
            return;
        }

        // Validate API key
        if (!engineApiKey.equals(apiKey)) {
            log.warn("❌ Invalid API key for callback endpoint: {}", requestPath);
            sendUnauthorizedResponse(response, "Invalid API key");
            return;
        }

        // API key is valid - set authentication
        log.debug("✅ Valid API key - authenticating as ENGINE_SERVICE");

        // Create authentication token with ENGINE_SERVICE role
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "ENGINE_SERVICE",  // Principal
                null,              // Credentials
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ENGINE_SERVICE"))
        );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("✅ Authentication set for ENGINE_SERVICE");

        // Continue filter chain
        filterChain.doFilter(request, response);
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