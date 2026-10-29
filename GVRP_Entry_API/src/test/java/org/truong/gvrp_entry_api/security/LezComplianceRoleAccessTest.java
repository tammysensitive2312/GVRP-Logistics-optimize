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
import org.truong.gvrp_entry_api.controller.LezComplianceController;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.service.lez.ComplianceReadService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role rules of {@link SecurityConfig} for the LEZ compliance read APIs
 * (LEZ-001 P1b, RA-T2). Same web slice as {@code LezRoleAccessTest}: no
 * database, the existing {@code /api/v1/**} PLANNER rule decides.
 */
@WebMvcTest(controllers = LezComplianceController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class LezComplianceRoleAccessTest {

    private static final String COMPLIANCE = "/api/v1/solutions/9/compliance";
    private static final String RECHECK = "/api/v1/solutions/9/compliance/recheck";
    private static final String GEOMETRY = "/api/v1/routes/21/geometry";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ComplianceReadService complianceReadService;

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
    void driver_getCompliance_forbidden_andServiceNotCalled() throws Exception {
        mockMvc.perform(get(COMPLIANCE))
                .andExpect(status().isForbidden());
        verify(complianceReadService, never()).compliance(any(), any());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_recheck_forbidden() throws Exception {
        mockMvc.perform(post(RECHECK))
                .andExpect(status().isForbidden());
        verify(complianceReadService, never()).recheck(any(), any());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_getGeometry_forbidden() throws Exception {
        mockMvc.perform(get(GEOMETRY))
                .andExpect(status().isForbidden());
        verify(complianceReadService, never()).geometry(any(), any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_getCompliance_forbidden() throws Exception {
        mockMvc.perform(get(COMPLIANCE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_getGeometry_forbidden() throws Exception {
        mockMvc.perform(get(GEOMETRY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_recheck_forbidden() throws Exception {
        mockMvc.perform(post(RECHECK))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_getCompliance_ok() throws Exception {
        mockMvc.perform(get(COMPLIANCE))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_getGeometry_ok() throws Exception {
        mockMvc.perform(get(GEOMETRY))
                .andExpect(status().isOk());
    }

    @Test
    void anonymous_getCompliance_unauthorized() throws Exception {
        mockMvc.perform(get(COMPLIANCE))
                .andExpect(status().isUnauthorized());
    }
}
