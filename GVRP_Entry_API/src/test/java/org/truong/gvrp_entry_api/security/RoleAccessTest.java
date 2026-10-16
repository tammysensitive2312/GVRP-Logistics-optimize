package org.truong.gvrp_entry_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.truong.gvrp_entry_api.config.SecurityConfig;
import org.truong.gvrp_entry_api.controller.AuthController;
import org.truong.gvrp_entry_api.controller.DriverController;
import org.truong.gvrp_entry_api.controller.OrderController;
import org.truong.gvrp_entry_api.controller.RouteSendController;
import org.truong.gvrp_entry_api.dto.response.RouteSendDTO;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.service.RouteSendService;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role rules of {@link SecurityConfig} (SEC-ROLE-001).
 *
 * <p>Web slice without a database: every service, repository and supporting
 * security bean is mocked; only the HTTP status and the error envelope are
 * asserted, so this class runs in CI.
 */
@WebMvcTest(controllers = {
        OrderController.class,
        DriverController.class,
        RouteSendController.class,
        AuthController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class RoleAccessTest {

    private static final String SEND_BODY = """
            {"driver_user_id":2,"solution_id":9}""";

    private static final String LOGIN_BODY = """
            {"branch_name":"b","username":"u","password":"p"}""";

    @Autowired
    private MockMvc mockMvc;

    // Controller dependencies.
    @MockBean
    private RouteSendService routeSendService;
    @MockBean
    private OrderService orderService;
    @MockBean
    private OrderImportService orderImportService;
    @MockBean
    private AuthenticationManager authenticationManager;
    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private BranchRepository branchRepository;

    // SecurityConfig dependencies.
    @MockBean
    private CustomUserDetailsService userDetailsService;
    @MockBean
    private BranchAwareAuthenticationProvider branchAwareAuthenticationProvider;
    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_getDriverRoutes_ok() throws Exception {
        when(routeSendService.mySends()).thenReturn(List.of(
                RouteSendDTO.builder().id(1L).status("CURRENT").build()));

        mockMvc.perform(get("/api/v1/driver/routes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_getOrders_forbiddenEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0].code").value("0040301"))
                .andExpect(jsonPath("$.errors[0].message").value("Access forbidden."));
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_getDrivers_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/drivers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DRIVER")
    void driver_postRouteSend_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/routes/1/send")
                        .contentType(MediaType.APPLICATION_JSON).content(SEND_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_getOrders_ok() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_getDrivers_ok() throws Exception {
        mockMvc.perform(get("/api/v1/drivers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void planner_getDriverRoutes_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/driver/routes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_getOrders_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customer_getDriverRoutes_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/driver/routes"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_getOrders_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymous_postLogin_notBlocked() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(result -> {
                    int responseStatus = result.getResponse().getStatus();
                    assertTrue(responseStatus != 401 && responseStatus != 403,
                            "expected not 401/403 but was " + responseStatus);
                });
    }
}
