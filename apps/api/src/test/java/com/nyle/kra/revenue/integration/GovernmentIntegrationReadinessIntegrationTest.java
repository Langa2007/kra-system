package com.nyle.kra.revenue.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyle.kra.revenue.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class GovernmentIntegrationReadinessIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDomainRecords() {
        jdbcTemplate.update("DELETE FROM source_schema_mappings");
        jdbcTemplate.update("DELETE FROM data_quality_issues");
        jdbcTemplate.update("DELETE FROM reconciliation_results");
        jdbcTemplate.update("DELETE FROM recovery_records");
        jdbcTemplate.update("DELETE FROM evidence_packs");
        jdbcTemplate.update("DELETE FROM case_events");
        jdbcTemplate.update("DELETE FROM cases");
        jdbcTemplate.update("DELETE FROM invoice_lines");
        jdbcTemplate.update("DELETE FROM invoices");
        jdbcTemplate.update("DELETE FROM tax_returns");
        jdbcTemplate.update("DELETE FROM customs_declarations");
        jdbcTemplate.update("DELETE FROM withholding_certificates");
        jdbcTemplate.update("DELETE FROM payroll_returns");
        jdbcTemplate.update("DELETE FROM business_permits");
        jdbcTemplate.update("DELETE FROM properties");
        jdbcTemplate.update("DELETE FROM payment_transactions");
        jdbcTemplate.update("DELETE FROM settlement_records");
        jdbcTemplate.update("DELETE FROM ingestion_jobs");
        jdbcTemplate.update("DELETE FROM data_sources");
    }

    @Test
    void readinessCoversAdaptersMappingsFreshnessRetriesAndSecretRedaction() throws Exception {
        String token = login();
        UUID apiSourceId = seedDataSource(
                "PHASE17_API_" + suffix(),
                "National taxpayer registry",
                "TAXPAYER_REGISTRY",
                "API_POLL",
                OffsetDateTime.now().minusHours(2),
                240,
                "CONNECTED"
        );
        UUID sftpSourceId = seedDataSource(
                "PHASE17_SFTP_" + suffix(),
                "County permit SFTP",
                "BUSINESS_PERMITS",
                "SFTP_BATCH",
                OffsetDateTime.now().minusDays(3),
                1440,
                "CONNECTED"
        );
        UUID dbSourceId = seedDataSource(
                "PHASE17_DB_" + suffix(),
                "Read-only settlement database",
                "SETTLEMENTS",
                "READ_ONLY_DB",
                OffsetDateTime.now().minusMinutes(30),
                120,
                "CONNECTED"
        );
        seedFailedRetryJob(sftpSourceId);

        mockMvc.perform(post("/api/integrations/schema-mappings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("""
                                {
                                  "dataSourceId": "%s",
                                  "targetEntity": "taxpayers",
                                  "sourceSchema": {
                                    "fields": ["pin", "taxpayer_name", "county"]
                                  },
                                  "mappingConfig": {
                                    "kra_pin": "pin",
                                    "legal_name": "taxpayer_name",
                                    "county": "county"
                                  }
                                }
                                """.formatted(apiSourceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dataSourceId").value(apiSourceId.toString()))
                .andExpect(jsonPath("$.targetEntity").value("taxpayers"))
                .andExpect(jsonPath("$.mappingConfig.kra_pin").value("pin"));

        mockMvc.perform(post("/api/integrations/mock-adapter-tests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "adapterType": "API",
                                  "connectionProfile": {
                                    "baseUrl": "https://registry.example.gov",
                                    "clientSecret": "top-secret-client-value",
                                    "nested": {
                                      "password": "database-password"
                                    }
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adapterType").value("API"))
                .andExpect(jsonPath("$.secretsRedacted").value(true))
                .andExpect(jsonPath("$.sanitizedConnectionProfile.clientSecret").value("***"))
                .andExpect(jsonPath("$.sanitizedConnectionProfile.nested.password").value("***"));

        for (String adapterType : new String[] {"SFTP", "DATABASE"}) {
            mockMvc.perform(post("/api/integrations/mock-adapter-tests")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "adapterType": "%s",
                                      "connectionProfile": {
                                        "host": "source.example.gov",
                                        "password": "never-log-me"
                                      }
                                    }
                                    """.formatted(adapterType)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.adapterType").value(adapterType))
                    .andExpect(jsonPath("$.sanitizedConnectionProfile.password").value("***"));
        }

        String readinessJson = mockMvc.perform(get("/api/integrations/readiness")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("Phase 17"))
                .andExpect(jsonPath("$.adapterTemplates[?(@.adapterType == 'API')]").exists())
                .andExpect(jsonPath("$.adapterTemplates[?(@.adapterType == 'SFTP')]").exists())
                .andExpect(jsonPath("$.adapterTemplates[?(@.adapterType == 'DATABASE')]").exists())
                .andExpect(jsonPath("$.schemaMappings[0].targetEntity").value("taxpayers"))
                .andExpect(jsonPath("$.lateSources").value(1))
                .andExpect(jsonPath("$.controlledRetryErrors").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode readiness = objectMapper.readTree(readinessJson);
        assertThat(readiness.path("freshness"))
                .anySatisfy(node -> {
                    assertThat(node.path("dataSourceCode").asText()).startsWith("PHASE17_SFTP_");
                    assertThat(node.path("late").asBoolean()).isTrue();
                    assertThat(node.path("alertLevel").asText()).isEqualTo("LATE");
                });
        assertThat(readiness.path("integrationErrors"))
                .anySatisfy(node -> {
                    assertThat(node.path("status").asText()).isEqualTo("FAILED");
                    assertThat(node.path("retryControlled").asBoolean()).isTrue();
                });
        assertThat(readiness.toString()).doesNotContain("top-secret-client-value", "never-log-me");
        assertThat(jdbcTemplate.queryForList(
                "SELECT details::text FROM audit_logs WHERE action = 'INTEGRATION_ADAPTER_TESTED'",
                String.class
        )).allSatisfy(details -> assertThat(details).doesNotContain("top-secret-client-value", "never-log-me"));

        assertThat(dbSourceId).isNotNull();
    }

    private UUID seedDataSource(
            String code,
            String name,
            String sourceType,
            String ingestionMethod,
            OffsetDateTime lastSuccessfulIngestionAt,
            int expectedFreshnessMinutes,
            String integrationStatus
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO data_sources (
                    id, code, name, source_type, owner_agency, ingestion_method, schema_version,
                    active, connection_profile, expected_freshness_minutes, raw_archive_bucket,
                    last_successful_ingestion_at, integration_status
                )
                VALUES (?, ?, ?, ?, 'Phase 17 Test Agency', ?, 'v1', TRUE, '{}'::jsonb, ?, ?, ?, ?)
                """,
                id,
                code,
                name,
                sourceType,
                ingestionMethod,
                expectedFreshnessMinutes,
                "phase17-raw-archive",
                lastSuccessfulIngestionAt,
                integrationStatus
        );
        return id;
    }

    private void seedFailedRetryJob(UUID dataSourceId) {
        jdbcTemplate.update("""
                INSERT INTO ingestion_jobs (
                    id, data_source_id, file_name, status, records_received, records_valid,
                    records_invalid, started_at, error_summary, retry_count, max_retries, next_retry_at
                )
                VALUES (?, ?, 'county-permits.csv', 'FAILED', 0, 0, 0, now(), ?, 1, 3, now() + interval '30 minutes')
                """,
                UUID.randomUUID(),
                dataSourceId,
                "Mock SFTP file checksum mismatch; retry scheduled with capped attempts"
        );
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
