package com.nyle.kra.revenue.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AuthenticatedUser extends User {

    private final UUID userId;
    private final String fullName;

    public AuthenticatedUser(
            UUID userId,
            String email,
            String fullName,
            Collection<? extends GrantedAuthority> authorities
    ) {
        super(email, "", authorities);
        this.userId = userId;
        this.fullName = fullName;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }
}
