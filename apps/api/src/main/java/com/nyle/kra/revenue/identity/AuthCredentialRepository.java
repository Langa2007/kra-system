package com.nyle.kra.revenue.identity;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthCredentialRepository extends JpaRepository<AuthCredential, UUID> {
}
