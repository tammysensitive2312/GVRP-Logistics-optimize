package org.truong.gvrp_entry_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.truong.gvrp_entry_api.config.SecurityConfig;
import org.truong.gvrp_entry_api.controller.BranchZoneController;
import org.truong.gvrp_entry_api.controller.VehiclePermitController;
import org.truong.gvrp_entry_api.controller.ZoneTemplateController;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.service.lez.VehiclePermitService;
import org.truong.gvrp_entry_api.service.lez.ZoneService;
import org.truong.gvrp_entry_api.service.lez.ZoneTemplateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role rules of {@link SecurityConfig} for the LEZ endpoints (LEZ-001 P1a, RA-T1).
 *
 * <p>Same mechanism as {@code RoleAccessTest}: a web slice without a database
 * where the existing {@code /api/v1/**} PLANNER rule decides. SecurityConfig
 * itself is not changed by LEZ-001.
 */
@WebMvcTest(controllers = {
        ZoneTemplateController.class,
        BranchZoneController.class,
        VehiclePermitController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class LezRoleAccessTest {

    private static final String CREATE_BODY = """
            {"template_id":10,"mode":"LINKED"}""";

    @Autowired
    private MockMvc mockMvc;

    // Controller dependencies.
    @MockitoBean
    private ZoneTemplateService zoneTemplateService;
    @MockitoBean
    private ZoneService zoneService;
    @MockitoBean
    private VehiclePermitService vehiclePermitService;

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
    void driver_getZones_forbiddenEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("0040301"));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_getZoneTemplates_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/zone-templates"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_postZone_forbidden_andServiceNotCalled() throws Exception {
        mockMvc.perform(post("/api/v1/zones")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verify(zoneService, never()).createZone(any(), any());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_getPermits_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/5/permits"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_getZones_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_getZoneTemplates_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/zone-templates"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_getPermits_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles/5/permits"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_getZones_ok() throws Exception {
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_getZoneTemplates_ok() throws Exception {
        mockMvc.perform(get("/api/v1/zone-templates"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymous_getZones_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/zones"))
                .andExpect(status().isUnauthorized());
    }
}
