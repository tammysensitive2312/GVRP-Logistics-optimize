package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.LoginRequest;
import org.truong.gvrp_entry_api.dto.response.LoginResponse;
import org.truong.gvrp_entry_api.dto.request.RegisterRequest;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.security.BranchUsernamePasswordAuthenticationToken;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;

/**
 * Authentication Controller
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        String branchName = loginRequest.getBranchName();
        Branch branch = branchRepository.findByName(branchName)
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + branchName, "branchName"));

        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new BranchUsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword(),
                        loginRequest.getBranchName()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Get user info
        User user = userRepository.findByUsernameAndBranchId(loginRequest.getUsername(), branch.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User is not belong to this branch", "username"));

        // Generate JWT token with claims
        String jwt = tokenProvider.generateTokenWithClaims(
                user.getUsername(),
                user.getId(),
                user.getBranch().getId(),
                user.getRole().name()
        );

        // Build response
        LoginResponse response = LoginResponse.builder()
                .accessToken(jwt)
                .userId(user.getId())
                .username(user.getUsername())
                .branchId(user.getBranch().getId())
                .branchName(user.getBranch().getName())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Register endpoint (optional)
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.badRequest().body("Username is already taken");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest().body("Email is already in use");
        }

        // Create new user
        // ... implementation

        return ResponseEntity.ok("User registered successfully");
    }
}

