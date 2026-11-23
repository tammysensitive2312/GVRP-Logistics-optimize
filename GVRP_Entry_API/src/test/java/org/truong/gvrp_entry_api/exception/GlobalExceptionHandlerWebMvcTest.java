package org.truong.gvrp_entry_api.exception;

import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.truong.gvrp_entry_api.config.SecurityConfig;
import org.truong.gvrp_entry_api.controller.AuthController;
import org.truong.gvrp_entry_api.controller.OrderController;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.security.ApiKeyAuthenticationFilter;
import org.truong.gvrp_entry_api.security.BranchAwareAuthenticationProvider;
import org.truong.gvrp_entry_api.security.CustomUserDetailsService;
import org.truong.gvrp_entry_api.security.RoleAccessDeniedHandler;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.service.orders.OrderImportService;
import org.truong.gvrp_entry_api.service.orders.OrderService;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for unified error responses across {@link GlobalExceptionHandler},
 * {@link JwtAuthenticationEntryPoint}, {@link RoleAccessDeniedHandler}, and
 * {@link SecurityConfig} (E-1, E-6, E-7, and failed-login 500 -> 401).
 */
@WebMvcTest(controllers = {
        AuthController.class,
        OrderController.class,
        GlobalExceptionHandlerWebMvcTest.ProbeController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class,
        GlobalExceptionHandler.class,
        GlobalExceptionHandlerWebMvcTest.ProbeController.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class GlobalExceptionHandlerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    // AuthController dependencies
    @MockitoBean private AuthenticationManager authenticationManager;
    @MockitoBean private JwtTokenProvider tokenProvider;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private BranchRepository branchRepository;

    // OrderController dependencies
    @MockitoBean private OrderService orderService;
    @MockitoBean private OrderImportService orderImportService;

    // SecurityConfig dependencies
    @MockitoBean private CustomUserDetailsService userDetailsService;
    @MockitoBean private BranchAwareAuthenticationProvider branchAwareAuthenticationProvider;
    @MockitoBean private PasswordEncoder passwordEncoder;

    @Validated
    @RestController
    @RequestMapping("/api/v1/test-errors")
    static class ProbeController {

        @GetMapping("/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        public String adminOnly() {
            return "ok";
        }

        @GetMapping("/require-param")
        public String requireParam(@RequestParam String name) {
            return name;
        }

        @GetMapping("/constrained")
        public String constrained(@RequestParam @Min(1) int count) {
            return String.valueOf(count);
        }

        @GetMapping("/illegal-arg")
        public String illegalArg() {
            throw new IllegalArgumentException("secret-customer-value");
        }

        @GetMapping(value = "/ping", produces = MediaType.APPLICATION_JSON_VALUE)
        public String ping() {
            return "{\"ok\":true}";
        }
    }

    // ---------- E-1: 401 unified ErrorResponse ----------

    @Test
    void anonymousProtectedRequest_returns401WithErrorResponseEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.UNAUTHORIZED.getMessage()))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
    }

    @Test
    void loginWithBadCredentialsOrDisabledUser_returns401InsteadOf500() throws Exception {
        when(branchRepository.findByName("branch-a"))
                .thenReturn(Optional.of(Branch.builder().id(1L).name("branch-a").build()));

        String loginJson = """
                {"branch_name":"branch-a","username":"u","password":"wrong"}""";

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INCORRECT_LOGIN_INFORMATION.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.INCORRECT_LOGIN_INFORMATION.getMessage()));

        doThrow(new DisabledException("User is disabled"))
                .when(authenticationManager).authenticate(any());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INCORRECT_LOGIN_INFORMATION.getCode()));
    }

    // ---------- E-7: @PreAuthorize -> 403 instead of 500 ----------

    @Test
    @WithMockUser(roles = "PLANNER")
    void preAuthorizeFailure_returns403InsteadOf500() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.FORBIDDEN.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.FORBIDDEN.getMessage()));
    }

    // ---------- E-6: Standard Spring MVC errors -> 4xx instead of 500 ----------

    @Test
    @WithMockUser(roles = "PLANNER")
    void wrongPathParamType_returns400InvalidValueTypeWithoutEchoingInput() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/orders/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.INVALID_VALUE_TYPE.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.INVALID_VALUE_TYPE.getMessage()))
                .andExpect(jsonPath("$.errors[0].field").value("orderId"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("not-a-number")
                .doesNotContain("Long");
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void missingRequiredParam_returns400EmptyFieldError() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/require-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.EMPTY_FIELD_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void constraintViolationAndIllegalArgument_return400UnsupportedValue() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/constrained").param("count", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNSUPPORTED_VALUE.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("count"));

        MvcResult illegalRes = mockMvc.perform(get("/api/v1/test-errors/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNSUPPORTED_VALUE.getCode()))
                .andExpect(jsonPath("$.errors[0].message").value(ErrorCode.UNSUPPORTED_VALUE.getMessage()))
                .andReturn();

        assertThat(illegalRes.getResponse().getContentAsString())
                .doesNotContain("secret-customer-value");
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void unsupportedMediaType_notAcceptable_andNoResourceFound_return4xxInsteadOf500() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode()));

        mockMvc.perform(get("/api/v1/test-errors/ping")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());

        mockMvc.perform(get("/api/v1/no-such-endpoint-anywhere"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].code").value(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }
}
