package org.truong.gvrp_entry_api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.truong.gvrp_entry_api.entity.Branch;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.repository.BranchRepository;

// Trong thư mục security/auth
@Component
@RequiredArgsConstructor
public class BranchAwareAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final BranchRepository branchRepository; // Cần Repository để kiểm tra Branch

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        BranchUsernamePasswordAuthenticationToken token = (BranchUsernamePasswordAuthenticationToken) authentication;

        String username = token.getName();
        String password = token.getCredentials().toString();
        String branchName = token.getBranchName();

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new BadCredentialsException("Sai mật khẩu hoặc tên đăng nhập.");
        }

        Branch branch = branchRepository.findByName(branchName)
                .orElseThrow(() -> new BadCredentialsException("Chi nhánh không tồn tại: " + branchName));
        User user = ((UserPrincipal) userDetails).getUser();

        if (!user.getBranch().getId().equals(branch.getId())) {
            throw new BadCredentialsException("Tài khoản không thuộc chi nhánh này.");
        }

        // 5. Xác thực thành công -> Tạo token mới chứa quyền hạn
        return new BranchUsernamePasswordAuthenticationToken(
                userDetails.getUsername(),
                userDetails.getPassword(),
                branchName, // Giữ lại branchName trong token đã xác thực
                userDetails.getAuthorities()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BranchUsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
