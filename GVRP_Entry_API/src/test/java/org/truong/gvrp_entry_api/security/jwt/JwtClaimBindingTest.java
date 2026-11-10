package org.truong.gvrp_entry_api.security.jwt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.security.UserPrincipal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Token claims must match the user reloaded at request time (SEC-AUTH-002, audit lead #9).
 *
 * <p>A user moved to another branch (another company) must not keep the former branch's
 * tenant scope through a token issued before the move. Plain JUnit 5 + Mockito.
 */
class JwtClaimBindingTest {

    private static final String TOKEN = "token-value";
    private static final String USERNAME = "dispatcher";
    private static final long USER_ID = 5L;
    private static final long OLD_BRANCH = 1L;
    private static final long CURRENT_BRANCH = 2L;

    private final JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
    private final UserDetailsService userDetailsService = Mockito.mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenProvider, userDetailsService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal userInBranch(long branchId, UserRole role) {
        User user = User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .enabled(true)
                .role(role)
                .branch(Branch.builder().id(branchId).build())
                .build();
        return new UserPrincipal(user);
    }

    private void stubToken(Long branchClaim, String roleClaim) {
        when(tokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(tokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
        when(tokenProvider.getBranchIdFromToken(TOKEN)).thenReturn(branchClaim);
        when(tokenProvider.getRoleFromToken(TOKEN)).thenReturn(roleClaim);
    }

    private MockHttpServletRequest run() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest(), "the chain must always continue");
        return request;
    }

    private static void assertNotAuthenticated(MockHttpServletRequest request) {
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute("userId"));
        assertNull(request.getAttribute("branchId"));
        assertNull(request.getAttribute("role"));
    }

    @Test
    void branchClaimDiffersFromUserBranch_doesNotAuthenticate() throws Exception {
        stubToken(OLD_BRANCH, "PLANNER");
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(userInBranch(CURRENT_BRANCH, UserRole.PLANNER));

        assertNotAuthenticated(run());
    }

    @Test
    void missingBranchClaim_doesNotAuthenticate() throws Exception {
        stubToken(null, "PLANNER");
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(userInBranch(CURRENT_BRANCH, UserRole.PLANNER));

        assertNotAuthenticated(run());
    }

    @Test
    void roleClaimDiffersFromUserRole_doesNotAuthenticate() throws Exception {
        stubToken(CURRENT_BRANCH, "DRIVER");
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(userInBranch(CURRENT_BRANCH, UserRole.PLANNER));

        assertNotAuthenticated(run());
    }

    @Test
    void matchingClaims_authenticateWithUserBranch() throws Exception {
        stubToken(CURRENT_BRANCH, "PLANNER");
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(userInBranch(CURRENT_BRANCH, UserRole.PLANNER));

        MockHttpServletRequest request = run();

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(USER_ID, request.getAttribute("userId"));
        assertEquals(CURRENT_BRANCH, request.getAttribute("branchId"));
        assertEquals("PLANNER", request.getAttribute("role"));
    }
}
