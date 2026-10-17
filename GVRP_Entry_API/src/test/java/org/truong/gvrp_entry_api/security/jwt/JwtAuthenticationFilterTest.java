package org.truong.gvrp_entry_api.security.jwt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.security.UserPrincipal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter} (SEC-SESSION-001, SS-001..SS-006).
 *
 * <p>Plain JUnit 5 + Mockito, no Spring context and no database.
 */
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "token-value";
    private static final String USERNAME = "dispatcher";
    private static final long USER_ID = 5L;

    private final JwtTokenProvider tokenProvider = Mockito.mock(JwtTokenProvider.class);
    private final UserDetailsService userDetailsService = Mockito.mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(tokenProvider, userDetailsService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    private UserPrincipal principal(boolean enabled) {
        User user = User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .enabled(enabled)
                .role(UserRole.PLANNER)
                .build();
        return new UserPrincipal(user);
    }

    private void stubValidToken(Long tokenUserId) {
        when(tokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(tokenProvider.getUserIdFromToken(TOKEN)).thenReturn(tokenUserId);
        when(tokenProvider.getBranchIdFromToken(TOKEN)).thenReturn(1L);
        when(tokenProvider.getRoleFromToken(TOKEN)).thenReturn("PLANNER");
    }

    @Test
    void enabledAndMatchingId_authenticatesAndSetsAttributes() throws Exception {
        stubValidToken(USER_ID);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(principal(true));

        MockHttpServletRequest request = requestWithToken();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(request.getAttribute("userId"));
        assertNotNull(request.getAttribute("branchId"));
        assertNotNull(request.getAttribute("role"));
        assertNotNull(chain.getRequest());
    }

    @Test
    void disabledUser_doesNotAuthenticate() throws Exception {
        stubValidToken(USER_ID);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(principal(false));

        MockHttpServletRequest request = requestWithToken();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute("userId"));
        assertNull(request.getAttribute("branchId"));
        assertNotNull(chain.getRequest());
    }

    @Test
    void missingUser_doesNotAuthenticateAndNoExceptionEscapes() throws Exception {
        when(tokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenThrow(new UsernameNotFoundException("User not found: " + USERNAME));

        MockHttpServletRequest request = requestWithToken();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }

    @Test
    void idMismatch_doesNotAuthenticate() throws Exception {
        stubValidToken(USER_ID + 1);
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(principal(true));

        MockHttpServletRequest request = requestWithToken();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getAttribute("userId"));
        assertNotNull(chain.getRequest());
    }

    @Test
    void invalidToken_doesNotLoadUser() throws Exception {
        when(tokenProvider.validateToken(TOKEN)).thenReturn(false);

        MockHttpServletRequest request = requestWithToken();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertNotNull(chain.getRequest());
    }

    @Test
    void noHeader_doesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userDetailsService, never()).loadUserByUsername(any());
        assertNotNull(chain.getRequest());
    }
}
