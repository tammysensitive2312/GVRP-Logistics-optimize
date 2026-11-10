package org.truong.gvrp_entry_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.truong.gvrp_entry_api.config.SecurityConfig;
import org.truong.gvrp_entry_api.controller.AuthController;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Anonymous {@code POST /api/v1/auth/register} (SEC-AUTH-002: SEC-008 and audit lead #12).
 *
 * <p>The endpoint must not reveal whether a username or e-mail exists in any branch, and must
 * not report success for an account it does not create. Web slice with the real
 * {@link SecurityConfig}; repositories are mocked, so "existing" means the mock says so.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        ApiKeyAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        RoleAccessDeniedHandler.class
})
@TestPropertySource(properties = "engine.api.key=test-key")
class RegisterEndpointTest {

    private static final String EXISTING_USERNAME = "dummy_a";
    private static final String EXISTING_EMAIL = "dummy_a@example.test";

    @Autowired
    private MockMvc mockMvc;

    // AuthController dependencies.
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

    private static String body(String username, String email) {
        return """
                {"username":"%s","password":"secret-123","email":"%s",\
                "full_name":"Dummy User","branch_id":1}""".formatted(username, email);
    }

    private MockHttpServletResponse register(String content) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andReturn().getResponse();
    }

    private void stubExistingAccount() {
        when(userRepository.existsByUsername(anyString()))
                .thenAnswer(inv -> EXISTING_USERNAME.equals(inv.getArgument(0)));
        when(userRepository.existsByEmail(anyString()))
                .thenAnswer(inv -> EXISTING_EMAIL.equals(inv.getArgument(0)));
    }

    @Test
    void existingAndUnusedValues_getIdenticalResponses() throws Exception {
        stubExistingAccount();

        MockHttpServletResponse unused = register(body("fresh_user", "fresh@example.test"));
        MockHttpServletResponse takenName = register(body(EXISTING_USERNAME, "fresh@example.test"));
        MockHttpServletResponse takenEmail = register(body("fresh_user", EXISTING_EMAIL));

        assertEquals(unused.getStatus(), takenName.getStatus(), "status reveals an existing username");
        assertEquals(unused.getStatus(), takenEmail.getStatus(), "status reveals an existing e-mail");
        assertEquals(unused.getContentAsString(), takenName.getContentAsString(),
                "body reveals an existing username");
        assertEquals(unused.getContentAsString(), takenEmail.getContentAsString(),
                "body reveals an existing e-mail");
    }

    @Test
    void register_neverReportsSuccessAndNeverTouchesUsers() throws Exception {
        stubExistingAccount();

        String[] bodies = {
                body("fresh_user", "fresh@example.test"),
                body(EXISTING_USERNAME, EXISTING_EMAIL),
                "{}",
                "not json"
        };
        MockHttpServletResponse first = register(bodies[0]);
        for (String content : bodies) {
            MockHttpServletResponse response = register(content);
            assertEquals(403, response.getStatus(), "self-registration must be refused: " + content);
            assertEquals(first.getContentAsString(), response.getContentAsString(),
                    "refusal must not depend on the body: " + content);
            assertFalse(response.getContentAsString().toLowerCase().contains("success"),
                    "must not report success");
        }

        verifyNoInteractions(userRepository);
    }
}
