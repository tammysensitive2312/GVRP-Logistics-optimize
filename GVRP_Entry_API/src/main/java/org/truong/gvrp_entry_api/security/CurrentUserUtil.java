package org.truong.gvrp_entry_api.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Utility class to get current authenticated user information
 */
@Component
public class CurrentUserUtil {

    /**
     * Get current authenticated username
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getUsername();
        }
        return null;
    }

    /**
     * Get current userId from request attribute (set by JwtAuthenticationFilter)
     */
    public static Long getCurrentUserId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            Object userId = attributes.getRequest().getAttribute("userId");
            return userId != null ? (Long) userId : null;
        }
        return null;
    }

    /**
     * Get current branchId from request attribute
     */
    public static Long getCurrentBranchId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            Object branchId = attributes.getRequest().getAttribute("branchId");
            return branchId != null ? (Long) branchId : null;
        }
        return null;
    }

    /**
     * Get current role from request attribute
     */
    public static String getCurrentRole() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            Object role = attributes.getRequest().getAttribute("role");
            return role != null ? (String) role : null;
        }
        return null;
    }

    /**
     * Check if current user is authenticated
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
