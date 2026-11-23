package org.truong.gvrp_entry_api.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.security.UserPrincipal;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link JwtTokenProvider} and single-parse behavior of {@link JwtAuthenticationFilter}
 * (R-4, R-5, R-6).
 */
class JwtTokenProviderTest {

    private static final String TEST_SECRET = "test-signing-key-for-unit-tests-only-0123456789";
    private static final String OTHER_SECRET = "different-signing-key-for-bad-signature-0123456789";

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 60_000L);
        provider.init();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void wrongSignatureToken_isCaughtAndReturnsFalseOrEmptyWithoutThrowing() {
        SecretKey otherKey = Keys.hmacShaKeyFor(OTHER_SECRET.getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("attacker")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L))
                .signWith(otherKey)
                .compact();

        Optional<Claims> parsed = assertDoesNotThrow(() -> provider.parseValidClaims(forged));
        assertThat(parsed).isEmpty();
        assertFalse(assertDoesNotThrow(() -> provider.validateToken(forged)));
    }

    @Test
    void validTokenRoundTripsClaimsOnce_andExpiredMalformedEmptyTokensReturnEmpty() {
        String valid = provider.generateTokenWithClaims("alice", 10L, 2L, "PLANNER");
        Optional<Claims> parsed = provider.parseValidClaims(valid);
        assertThat(parsed).isPresent();
        Claims claims = parsed.get();
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("userId", Long.class)).isEqualTo(10L);
        assertThat(claims.get("branchId", Long.class)).isEqualTo(2L);
        assertThat(claims.get("role", String.class)).isEqualTo("PLANNER");

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000L))
                .expiration(new Date(System.currentTimeMillis() - 60_000L))
                .signWith(key)
                .compact();
        assertThat(provider.parseValidClaims(expired)).isEmpty();
        assertThat(provider.parseValidClaims("not.a.jwt")).isEmpty();
        assertThat(provider.parseValidClaims("")).isEmpty();
    }

    @Test
    void filter_parsesTokenOnlyOncePerRequest_andRejectsLockedUserCleanly() throws Exception {
        String token = provider.generateTokenWithClaims("planner", 5L, 1L, "PLANNER");
        JwtTokenProvider spyProvider = Mockito.spy(provider);
        UserDetailsService userDetailsService = Mockito.mock(UserDetailsService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(spyProvider, userDetailsService);
        User enabledUser = User.builder()
                .id(5L)
                .username("planner")
                .enabled(true)
                .role(UserRole.PLANNER)
                .branch(Branch.builder().id(1L).build())
                .build();
        when(userDetailsService.loadUserByUsername("planner")).thenReturn(new UserPrincipal(enabledUser));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(spyProvider, times(1)).parseValidClaims(token);
        verifyNoMoreInteractions(spyProvider);

        SecurityContextHolder.clearContext();
        UserPrincipal lockedPrincipal = Mockito.mock(UserPrincipal.class);
        when(lockedPrincipal.isEnabled()).thenReturn(true);
        when(lockedPrincipal.isAccountNonLocked()).thenReturn(false);
        when(userDetailsService.loadUserByUsername("planner")).thenReturn(lockedPrincipal);

        MockHttpServletRequest lockedReq = new MockHttpServletRequest();
        lockedReq.addHeader("Authorization", "Bearer " + token);
        filter.doFilterInternal(lockedReq, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
