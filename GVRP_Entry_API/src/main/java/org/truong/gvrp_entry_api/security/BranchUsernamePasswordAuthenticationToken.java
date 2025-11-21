package org.truong.gvrp_entry_api.security;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class BranchUsernamePasswordAuthenticationToken extends UsernamePasswordAuthenticationToken {
    private final String branchName;


    public BranchUsernamePasswordAuthenticationToken(String username, String password, String branchName) {
        super(username, password);
        this.branchName = branchName;
    }

    public BranchUsernamePasswordAuthenticationToken(Object principal, Object credentials,
                                                     String branchName,
                                                     Collection<? extends GrantedAuthority> authorities) {
        super(principal, credentials, authorities);
        this.branchName = branchName;
    }

    public String getBranchName() {
        return branchName;
    }
}
