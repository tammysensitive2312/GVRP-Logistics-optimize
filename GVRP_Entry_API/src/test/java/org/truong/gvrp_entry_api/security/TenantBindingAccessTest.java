package org.truong.gvrp_entry_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.truong.gvrp_entry_api.config.SecurityConfig;
import org.truong.gvrp_entry_api.controller.OrderController;
import org.truong.gvrp_entry_api.dto.response.OrderDTO;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.enums.UserRole;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant scope follows the user's current branch, not the branch frozen in an older token
 * (SEC-AUTH-002, audit lead #9).
 *
 * <p>Web slice with the real {@link SecurityConfig} and {@link JwtAuthenticationFilter};
 * token parsing and user loading are mocked, so no database or signing key is needed.
 * Scenario: user 5 logged in while in branch 1 (token claim branchId=1) and was then moved
 * to branch 2 in the database.
 */
@WebMvcTest(controllers = OrderController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class TenantBindingAccessTest {

    private static final String TOKEN = "token-value";
    private static final String USERNAME = "planner_a";
    private static final long USER_ID = 5L;
    private static final long ORDER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    // Controller dependencies.
    @MockBean
    private OrderService orderService;
    @MockBean
    private OrderImportService orderImportService;

    // Security dependencies.
    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private CustomUserDetailsService userDetailsService;
    @MockBean
    private BranchAwareAuthenticationProvider branchAwareAuthenticationProvider;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private AuthenticationManager authenticationManager;

    private void tokenWithBranchClaim(long branchClaim) {
        when(tokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn(USERNAME);
        when(tokenProvider.getUserIdFromToken(TOKEN)).thenReturn(USER_ID);
        when(tokenProvider.getBranchIdFromToken(TOKEN)).thenReturn(branchClaim);
        when(tokenProvider.getRoleFromToken(TOKEN)).thenReturn("PLANNER");
    }

    private void userNowInBranch(long branchId) {
        User user = User.builder()
                .id(USER_ID)
                .username(USERNAME)
                .enabled(true)
                .role(UserRole.PLANNER)
                .branch(Branch.builder().id(branchId).build())
                .build();
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(new UserPrincipal(user));
    }

    @Test
    void staleBranchToken_getOrder_unauthorizedAndServiceNotCalled() throws Exception {
        tokenWithBranchClaim(1L);
        userNowInBranch(2L);

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).getOrderById(any(), any());
    }

    @Test
    void currentBranchToken_getOrder_scopedToUserBranch() throws Exception {
        tokenWithBranchClaim(2L);
        userNowInBranch(2L);
        when(orderService.getOrderById(ORDER_ID, 2L)).thenReturn(new OrderDTO());

        mockMvc.perform(get("/api/v1/orders/{id}", ORDER_ID)
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        verify(orderService).getOrderById(ORDER_ID, 2L);
    }
}
