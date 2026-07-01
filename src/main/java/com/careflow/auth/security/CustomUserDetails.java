package com.careflow.auth.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String username; // email 값이 들어감
    private final String password;
    private final String role;
    private final Long agencyId; // AGENCY·ENGINEER 역할일 때만 값이 들어가고, 나머지는 null

    public CustomUserDetails(Long userId, String username, String password, String role, Long agencyId) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.role = role;
        this.agencyId = agencyId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_"+role));
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}