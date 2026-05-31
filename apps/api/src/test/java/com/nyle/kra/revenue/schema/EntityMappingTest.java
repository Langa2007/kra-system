package com.nyle.kra.revenue.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;

import com.nyle.kra.revenue.audit.AuditLogRepository;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.identity.AuthCredentialRepository;
import com.nyle.kra.revenue.support.PostgresIntegrationTest;
import com.nyle.kra.revenue.taxpayer.Taxpayer;
import com.nyle.kra.revenue.taxpayer.TaxpayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EntityMappingTest extends PostgresIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuthCredentialRepository authCredentialRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private TaxpayerRepository taxpayerRepository;

    @Test
    void userRoleCredentialAuditAndTaxpayerMappingsPersistAndRead() {
        AppUser admin = appUserRepository.findByEmailIgnoreCase(TEST_ADMIN_EMAIL).orElseThrow();

        assertThat(admin.getRoles()).extracting("code").contains("ADMIN");
        assertThat(authCredentialRepository.existsById(Objects.requireNonNull(admin.getId()))).isTrue();

        taxpayerRepository.save(new Taxpayer("P051234567A", "COMPANY", "Phase Two Traders Ltd", "ACTIVE"));

        assertThat(taxpayerRepository.findAll())
                .extracting(Taxpayer::getKraPin)
                .contains("P051234567A");

        String mappingAction = "ENTITY_MAPPING_TEST";

        auditLogRepository.save(new com.nyle.kra.revenue.audit.AuditLog(
                admin,
                mappingAction,
                "taxpayers",
                null,
                "127.0.0.1",
                "JUnit",
                java.util.Map.of("mapping", "verified")
        ));

        assertThat(auditLogRepository.countByAction(mappingAction)).isEqualTo(1);
    }
}
