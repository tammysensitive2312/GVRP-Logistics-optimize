package org.truong.gvrp_entry_api.security.jwt;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.security.UserPrincipal;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

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
            Optional<Claims> parsedClaims = StringUtils.hasText(jwt)
                    ? tokenProvider.parseValidClaims(jwt)
                    : Optional.empty();

            if (parsedClaims.isPresent()) {
                Claims claims = parsedClaims.get();
                String username = claims.getSubject();

                // Load user details
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Extract all claims from the single parse (R-5)
                Long userId = claims.get("userId", Long.class);
                Long branchId = claims.get("branchId", Long.class);
                String role = claims.get("role", String.class);

                String rejection = rejectionReason(userDetails, userId, branchId, role);
                if (rejection == null) {
                    // Tenant scope comes from the reloaded user (equal to the claim once
                    // rejectionReason passed); the claim is used only for a user without a branch,
                    // which the NOT NULL users.branch_id column rules out in the database.
                    Long currentBranchId = currentBranchId((UserPrincipal) userDetails);
                    if (currentBranchId != null) {
                        branchId = currentBranchId;
                    }

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
                    // Disabled/locked account, or a userId/branchId/role claim that no longer
                    // matches the user: leave the request unauthenticated so protected
                    // endpoints answer 401 (SS-001..SS-003, SEC-AUTH-002, R-6).
                    logger.warn("Rejected authentication for user '{}': {}", username, rejection);
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Returns {@code null} when the user and token claims are valid, or a short reason
     * string for diagnostics when authentication must be refused (R-6).
     */
    private String rejectionReason(UserDetails userDetails, Long userIdFromToken,
                                   Long branchIdFromToken, String roleFromToken) {
        if (!userDetails.isEnabled()) {
            return "account disabled";
        }
        if (!userDetails.isAccountNonLocked()) {
            return "account locked";
        }
        if (!(userDetails instanceof UserPrincipal principal)) {
            return "unexpected principal type";
        }
        User user = principal.getUser();
        if (!Objects.equals(user.getId(), userIdFromToken)) {
            return "userId claim mismatch";
        }
        Long currentBranchId = currentBranchId(principal);
        if (currentBranchId != null && !currentBranchId.equals(branchIdFromToken)) {
            return "branchId claim mismatch";
        }
        if (user.getRole() == null || !user.getRole().name().equals(roleFromToken)) {
            return "role claim mismatch";
        }
        return null;
    }

    /**
     * Id of the user's current branch, or {@code null} when the user has none. Reads only
     * the identifier of the lazy branch association, which does not initialize the proxy.
     */
    private static Long currentBranchId(UserPrincipal principal) {
        Branch branch = principal.getUser().getBranch();
        return branch != null ? branch.getId() : null;
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
