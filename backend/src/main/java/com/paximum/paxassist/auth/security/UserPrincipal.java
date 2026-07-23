package com.paximum.paxassist.auth.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;

/**
 * Adapts the {@link User} entity to Spring Security's {@link UserDetails}, keeping the JPA
 * entity free of framework-specific contracts.
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String displayName;
    private final String passwordHash;
    private final Role role;
    private final List<GrantedAuthority> authorities;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * The role as a domain value, kept alongside the derived {@link #authorities} so callers that
     * need to REPORT the role (GET /auth/me) don't have to parse the "ROLE_" prefix back off a
     * GrantedAuthority. Both are built from the same {@code user.getRole()} so they cannot diverge.
     */
    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
