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
import org.springframework.test.web.servlet.MvcResult;
import org.truong.gvrp_entry_api.config.SecurityConfig;
import org.truong.gvrp_entry_api.controller.OptimizationCallbackController;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.service.OptimizationCallbackService;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CB-T1..CB-T5 (SEC-CALLBACK-001, SEC-012): engine callbacks need a valid
 * {@code X-API-Key} on every spelling of the callback path, including
 * percent-encoded ones the old raw-URI check missed.
 *
 * <p>Web slice through the real {@link SecurityConfig} chain, DB-free: the
 * callback service and the security collaborators are mocked.
 */
@WebMvcTest(controllers = OptimizationCallbackController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class CallbackAuthenticationTest {

    private static final String PROGRESS_BODY = """
            {"job_id":1,"progress":50}""";

    private static final List<String> ENCODED_VARIANTS = List.of(
            "/api/v1/solutions/%63allbacks/progress",
            "/api/v1/%73olutions/callbacks/progress",
            "/api/v1/solutions/callbacks/progress/",
            "/api/v1/solutions//callbacks/progress");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OptimizationCallbackService callbackService;

    // Security collaborators.
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;
    @MockitoBean
    private BranchAwareAuthenticationProvider branchAwareAuthenticationProvider;
    @MockitoBean
    private PasswordEncoder passwordEncoder;

    private int postProgress(String rawPath, String apiKey) throws Exception {
        var request = post(URI.create(rawPath))
                .contentType(MediaType.APPLICATION_JSON)
                .content(PROGRESS_BODY);
        if (apiKey != null) {
            request.header("X-API-Key", apiKey);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        return result.getResponse().getStatus();
    }

    @Test
    void cbT1_plainPath_noKey_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/solutions/callbacks/progress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROGRESS_BODY))
                .andExpect(status().isUnauthorized());

        verify(callbackService, never()).handleProgressUpdate(any());
    }

    @Test
    void cbT2_encodedOrOddPaths_noKey_neverReachHandler() throws Exception {
        for (String path : ENCODED_VARIANTS) {
            int status = postProgress(path, null);
            assertThat(status).as(path).isIn(400, 401);
        }

        verify(callbackService, never()).handleProgressUpdate(any());
    }

    @Test
    void cbT3_validKey_plainPath_ok() throws Exception {
        mockMvc.perform(post("/api/v1/solutions/callbacks/progress")
                        .header("X-API-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROGRESS_BODY))
                .andExpect(status().isOk());

        verify(callbackService, times(1)).handleProgressUpdate(any());
    }

    @Test
    void cbT3_validKey_encodedPath_ok() throws Exception {
        assertThat(postProgress("/api/v1/solutions/%63allbacks/progress", "test-key")).isEqualTo(200);

        verify(callbackService, times(1)).handleProgressUpdate(any());
    }

    @Test
    void cbT4_wrongKey_unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/solutions/callbacks/progress")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PROGRESS_BODY))
                .andExpect(status().isUnauthorized());

        assertThat(postProgress("/api/v1/solutions/%63allbacks/progress", "wrong-key")).isEqualTo(401);

        verify(callbackService, never()).handleProgressUpdate(any());
    }

    @Test
    void cbT4_emptyKey_unauthorized() throws Exception {
        assertThat(postProgress("/api/v1/solutions/callbacks/progress", "")).isEqualTo(401);

        verify(callbackService, never()).handleProgressUpdate(any());
    }

    @Test
    @WithMockUser(roles = "PLANNER")
    void cbT5_plannerOnCallbackPath_refused() throws Exception {
        assertThat(postProgress("/api/v1/solutions/callbacks/progress", null)).isIn(401, 403);
        assertThat(postProgress("/api/v1/solutions/%63allbacks/progress", null)).isIn(401, 403);

        verify(callbackService, never()).handleProgressUpdate(any());
    }
}
