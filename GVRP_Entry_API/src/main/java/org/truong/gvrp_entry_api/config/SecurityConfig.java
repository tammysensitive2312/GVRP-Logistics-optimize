package org.truong.gvrp_entry_api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.truong.gvrp_entry_api.security.ApiKeyAuthenticationFilter;
import org.truong.gvrp_entry_api.security.BranchAwareAuthenticationProvider;
import org.truong.gvrp_entry_api.security.CustomUserDetailsService;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationFilter;
import org.truong.gvrp_entry_api.security.jwt.JwtAuthenticationEntryPoint;

import java.util.Arrays;

/**
 * Spring Security Configuration
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final BranchAwareAuthenticationProvider branchAwareAuthenticationProvider;
    private final PasswordEncoder passwordEncoder;

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;


    @Bean
    public AuthenticationManager authenticationManager() throws Exception {

        DaoAuthenticationProvider defaultProvider = new DaoAuthenticationProvider();
        defaultProvider.setUserDetailsService(userDetailsService);
        defaultProvider.setPasswordEncoder(passwordEncoder);


        return new ProviderManager(
                Arrays.asList(
                        defaultProvider,
                        branchAwareAuthenticationProvider
                )
        );

    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configure(http))
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/api/v1/solutions/callbacks/**").permitAll()

                        // Authenticated endpoints
                        .requestMatchers("/api/v1/depots/**").authenticated()
                        .requestMatchers("/api/v1/fleets/**").authenticated()
                        .requestMatchers("/api/v1/vehicles/**").authenticated()
                        .requestMatchers("/api/v1/orders/**").authenticated()
                        .requestMatchers("/api/v1/jobs/**").authenticated()
                        .requestMatchers("/api/v1/solutions/**").authenticated()

                        // All other requests require authentication
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
