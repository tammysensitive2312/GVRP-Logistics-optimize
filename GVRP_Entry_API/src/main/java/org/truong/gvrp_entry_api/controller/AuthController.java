package org.truong.gvrp_entry_api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.truong.gvrp_entry_api.dto.request.LoginRequest;
import org.truong.gvrp_entry_api.dto.response.LoginResponse;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.exception.DataInvalidException;
import org.truong.gvrp_entry_api.exception.ErrorDetail;
import org.truong.gvrp_entry_api.exception.ForbiddenException;
import org.truong.gvrp_entry_api.exception.ResourceNotFoundException;
import org.truong.gvrp_entry_api.repository.BranchRepository;
import org.truong.gvrp_entry_api.repository.UserRepository;
import org.truong.gvrp_entry_api.security.BranchUsernamePasswordAuthenticationToken;
import org.truong.gvrp_entry_api.security.jwt.JwtTokenProvider;
import org.truong.gvrp_entry_api.util.AppConstant;
import org.truong.gvrp_entry_api.util.ErrorCode;

import java.util.List;

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
                .orElseThrow(() -> new ResourceNotFoundException(AppConstant.BRANCH));

        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new BranchUsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword(),
                        loginRequest.getBranchName()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByUsernameAndBranchId(loginRequest.getUsername(), branch.getId())
                .orElseThrow(() -> new DataInvalidException(List.of(
                        ErrorDetail.builder()
                                .code(ErrorCode.INCORRECT_LOGIN_INFORMATION.getCode())
                                .message(ErrorCode.INCORRECT_LOGIN_INFORMATION.getMessage())
                                .resource(AppConstant.USER)
                                .build()
                ))
                );

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
     * Public self-registration is disabled (SEC-AUTH-002: SEC-008, audit lead #12).
     *
     * <p>Every call is refused with the same 403, without reading the body and without
     * querying the user table, so the response neither reports success for an account that
     * is not created nor reveals whether a username or e-mail exists in any branch.
     * Accounts are provisioned by an administrator.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register() {
        throw new ForbiddenException(
                "Self-registration is disabled. Accounts are provisioned by an administrator.");
    }
}

