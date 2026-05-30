package com.nyle.kra.revenue.identity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_credentials")
public class AuthCredential {

    @Id
    @Column(name = "app_user_id")
    private UUID appUserId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "app_user_id")
    private AppUser appUser;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "password_updated_at", nullable = false)
    private OffsetDateTime passwordUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AuthCredential() {
    }

    public AuthCredential(AppUser appUser, String passwordHash) {
        this.appUser = appUser;
        this.passwordHash = passwordHash;
        this.passwordUpdatedAt = OffsetDateTime.now();
        this.createdAt = this.passwordUpdatedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
