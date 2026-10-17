package org.truong.gvrp_entry_api.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.truong.gvrp_entry_api.security.UserPrincipal;

import java.io.IOException;
import java.util.Objects;

/**
 * JWT Authentication Filter
 * Intercepts every request and validates JWT token
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);

                // Load user details
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Get additional info from token
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                Long branchId = tokenProvider.getBranchIdFromToken(jwt);
                String role = tokenProvider.getRoleFromToken(jwt);

                if (isUsable(userDetails, userId)) {
                    // Create authentication
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set authentication in Security Context
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Store userId, branchId in request attributes for easy access
                    request.setAttribute("userId", userId);
                    request.setAttribute("branchId", branchId);
                    request.setAttribute("role", role);

                    logger.debug("Set Authentication for user: {}, branchId: {}, role: {}",
                            username, branchId, role);
                } else {
                    // Disabled/locked account or userId mismatch: leave the request
                    // unauthenticated so protected endpoints answer 401 (SS-001..SS-003).
                    logger.warn("Rejected authentication for user '{}': account disabled/locked "
                            + "or token/user id mismatch", username);
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * A token is usable only when its user is enabled and unlocked, and the
     * {@code userId} claim matches the user loaded from the database.
     */
    private boolean isUsable(UserDetails userDetails, Long userIdFromToken) {
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
            return false;
        }
        if (!(userDetails instanceof UserPrincipal principal)) {
            return false;
        }
        return Objects.equals(principal.getUser().getId(), userIdFromToken);
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
