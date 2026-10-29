package org.truong.gvrp_entry_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.truong.gvrp_entry_api.config.SecurityConfig;
import org.truong.gvrp_entry_api.controller.LezMembershipController;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.service.lez.ZoneMembershipService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role rules of {@link SecurityConfig} for the order-zone membership endpoint
 * (LEZ-001 P1c, OZ-T3). Same web slice as {@code LezComplianceRoleAccessTest}:
 * no database, the existing {@code /api/v1/**} PLANNER rule decides, so no
 * {@code SecurityConfig} change is needed.
 */
@WebMvcTest(controllers = LezMembershipController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class LezMembershipRoleAccessTest {

    private static final String ORDER_ZONES = "/api/v1/lez/order-zones?orderIds=1,2";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ZoneMembershipService zoneMembershipService;

    // Security dependencies.
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;
    @MockitoBean
    private BranchAwareAuthenticationProvider branchAwareAuthenticationProvider;
    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_forbidden_andServiceNotCalled() throws Exception {
        mockMvc.perform(get(ORDER_ZONES))
                .andExpect(status().isForbidden());
        verify(zoneMembershipService, never()).memberships(any(), any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_forbidden_andServiceNotCalled() throws Exception {
        mockMvc.perform(get(ORDER_ZONES))
                .andExpect(status().isForbidden());
        verify(zoneMembershipService, never()).memberships(any(), any());
    }

    @Test
    void anonymous_unauthorized() throws Exception {
        mockMvc.perform(get(ORDER_ZONES))
                .andExpect(status().isUnauthorized());
        verify(zoneMembershipService, never()).memberships(any(), any());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_ok() throws Exception {
        mockMvc.perform(get(ORDER_ZONES))
                .andExpect(status().isOk());
    }
}
