package com.nyle.kra.revenue.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class CaseManagementIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 1, 31);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDomainRecords() {
        jdbcTemplate.update("DELETE FROM recovery_records");
        jdbcTemplate.update("DELETE FROM evidence_packs");
        jdbcTemplate.update("DELETE FROM case_events");
        jdbcTemplate.update("DELETE FROM cases");
        jdbcTemplate.update("DELETE FROM risk_scores");
        jdbcTemplate.update("DELETE FROM tax_gap_estimates");
        jdbcTemplate.update("DELETE FROM risk_signals");
        jdbcTemplate.update("DELETE FROM taxpayer_relationships");
        jdbcTemplate.update("DELETE FROM taxpayer_identifiers");
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
        jdbcTemplate.update("DELETE FROM tax_obligations");
        jdbcTemplate.update("DELETE FROM taxpayers");
    }

    @Test
    void caseCreationFromRiskSignalWorksAndWritesAudit() throws Exception {
        String token = login(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD);
        UUID signal = signalWithGap(taxpayer("P800000001A", "Case Creation Ltd"), "VAT_OUTPUT_MISMATCH", "VAT", "250000.00");

        JsonNode created = createCase(token, signal);

        assertThat(created.get("riskSignalId").asText()).isEqualTo(signal.toString());
        assertThat(created.get("status").asText()).isEqualTo("OPEN");
        assertThat(created.get("estimatedRecoverableAmount").decimalValue()).isEqualByComparingTo("40000.00");
        assertThat(auditCount("CASE_CREATED")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM case_events WHERE case_id = ? AND event_type = 'OPENED'",
                Integer.class,
                UUID.fromString(created.get("id").asText())
        )).isEqualTo(1);
    }

    @Test
    void officerAssignmentIsPermissionControlled() throws Exception {
        String adminToken = login(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD);
        UUID officer = officerUser("assignment-officer@example.test", "Assignment Officer");
        String officerToken = login("assignment-officer@example.test", "officer-pass");
        UUID signal = signalWithGap(taxpayer("P800000002B", "Assignment Ltd"), "PAYE_RATIO_ANOMALY", "PAYE", "100000.00");
        UUID caseId = UUID.fromString(createCase(adminToken, signal).get("id").asText());

        mockMvc.perform(patch("/api/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + officerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "assignedTo": "%s"
                                }
                                """.formatted(officer))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "assignedTo": "%s"
                                }
                                """.formatted(officer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value(officer.toString()));
    }

    @Test
    void statusTransitionsAndRecoveryTrackingWork() throws Exception {
        String token = login(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD);
        UUID signal = signalWithGap(taxpayer("P800000003C", "Workflow Ltd"), "IMPORT_TO_SALES_MISMATCH", "INCOME_TAX", "600000.00");
        UUID caseId = UUID.fromString(createCase(token, signal).get("id").asText());

        mockMvc.perform(patch("/api/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "status": "AWAITING_TAXPAYER"
                                }
                                """)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "status": "IN_REVIEW",
                                  "assessedAmount": 180000.00,
                                  "agreedAmount": 150000.00,
                                  "collectedAmount": 120000.00,
                                  "collectionDate": "2025-02-20",
                                  "recoveryStatus": "PARTIAL"
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.collectedAmount").value(120000.00));

        mockMvc.perform(patch("/api/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "status": "CLOSED",
                                  "closureReason": "Recovered after taxpayer engagement"
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closureReason").value("Recovered after taxpayer engagement"));

        assertThat(auditCount("CASE_UPDATED")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void eventAndEvidencePackIncludeRequiredFactsAndPdfExportRenders() throws Exception {
        String token = login(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD);
        UUID taxpayer = taxpayer("P800000004D", "Evidence Ltd");
        UUID signal = signalWithGap(taxpayer, "WHT_INCOME_MISMATCH", "WITHHOLDING_TAX", "90000.00");
        UUID caseId = UUID.fromString(createCase(token, signal).get("id").asText());
        int previousEventAudits = auditCount("CASE_EVENT_ADDED");
        int previousPackAudits = auditCount("EVIDENCE_PACK_GENERATED");

        mockMvc.perform(post("/api/cases/{id}/events", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "eventType": "NOTE",
                                  "eventNote": "Taxpayer contacted for source documents."
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("NOTE"));

        String packResponse = mockMvc.perform(post("/api/cases/{id}/evidence-packs", caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.evidence.taxpayer.kraPin").value("P800000004D"))
                .andExpect(jsonPath("$.evidence.period.start").value("2025-01-01"))
                .andExpect(jsonPath("$.evidence.rule.code").value("WHT_INCOME_MISMATCH"))
                .andExpect(jsonPath("$.evidence.gap.estimatedGap").value(90000.00))
                .andExpect(jsonPath("$.evidence.gap.confidenceScore").value(91.00))
                .andExpect(jsonPath("$.evidence.recommendation").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID packId = UUID.fromString(objectMapper.readTree(packResponse).get("id").asText());

        byte[] pdf = mockMvc.perform(get("/api/cases/{id}/evidence-packs/{packId}", caseId, packId)
                        .header("Authorization", "Bearer " + token)
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(auditCount("CASE_EVENT_ADDED")).isEqualTo(previousEventAudits + 1);
        assertThat(auditCount("EVIDENCE_PACK_GENERATED")).isEqualTo(previousPackAudits + 1);
    }

    @Test
    void caseDetailShowsEvidenceAndRecoveryReportValues() throws Exception {
        String token = login(TEST_ADMIN_EMAIL, TEST_ADMIN_PASSWORD);
        UUID signal = signalWithGap(taxpayer("P800000005E", "Detail Ltd"), "VAT_INPUT_MISMATCH", "VAT", "70000.00");
        UUID caseId = UUID.fromString(createCase(token, signal).get("id").asText());
        mockMvc.perform(patch("/api/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "status": "IN_REVIEW",
                                  "assessedAmount": 70000.00,
                                  "agreedAmount": 65000.00,
                                  "collectedAmount": 65000.00,
                                  "recoveryStatus": "COLLECTED"
                                }
                                """)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cases/{id}/evidence-packs", caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cases/{id}", caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detail.collectedAmount").value(65000.00))
                .andExpect(jsonPath("$.events[?(@.eventType == 'RECOVERY_RECORDED')]").exists())
                .andExpect(jsonPath("$.evidencePacks[0].evidence.sourceRecords.sourceRecord").value("VAT_INPUT_MISMATCH"));
    }

    private JsonNode createCase(String token, UUID signal) throws Exception {
        String response = mockMvc.perform(post("/api/cases")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "riskSignalId": "%s"
                                }
                                """.formatted(signal))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private UUID taxpayer(String pin, String legalName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO taxpayers (id, kra_pin, taxpayer_type, legal_name, registration_number, county, status)
                VALUES (?, ?, 'COMPANY', ?, ?, 'Nairobi', 'ACTIVE')
                """, id, pin, legalName, "BN-" + pin);
        return id;
    }

    private UUID signalWithGap(UUID taxpayer, String ruleCode, String taxHead, String estimatedGap) {
        UUID signal = UUID.randomUUID();
        UUID ruleId = jdbcTemplate.queryForObject("SELECT id FROM risk_rules WHERE code = ?", UUID.class, ruleCode);
        jdbcTemplate.update("""
                INSERT INTO risk_signals (
                    id, taxpayer_id, risk_rule_id, signal_type, tax_head, period_start, period_end,
                    observed_amount, declared_amount, estimated_gap, confidence_score, severity,
                    explanation, evidence, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 500000.00, 250000.00, ?, 91.00, 'HIGH',
                        'Synthetic signal for case management', CAST(? AS jsonb), 'OPEN')
                """,
                signal,
                taxpayer,
                ruleId,
                ruleCode,
                taxHead,
                PERIOD_START,
                PERIOD_END,
                new BigDecimal(estimatedGap),
                "{\"sourceRecord\":\"" + ruleCode + "\"}");
        jdbcTemplate.update("""
                INSERT INTO tax_gap_estimates (
                    deterministic_key, taxpayer_id, tax_head, period_start, period_end,
                    declared_amount, observed_amount, estimated_gap, estimated_recoverable_tax,
                    estimated_penalty, estimated_interest, estimated_total_due, confidence_score, evidence
                )
                VALUES (?, ?, ?, ?, ?, 250000.00, 500000.00, ?, ? * 0.16,
                        ? * 0.008, ? * 0.0016, ? * 0.1696, 91.00, CAST(? AS jsonb))
                """,
                "TEST:" + signal,
                taxpayer,
                taxHead,
                PERIOD_START,
                PERIOD_END,
                new BigDecimal(estimatedGap),
                new BigDecimal(estimatedGap),
                new BigDecimal(estimatedGap),
                new BigDecimal(estimatedGap),
                new BigDecimal(estimatedGap),
                "{\"signalIds\":[\"" + signal + "\"]}");
        return signal;
    }

    private UUID officerUser(String email, String fullName) {
        UUID userId = UUID.randomUUID();
        UUID roleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = 'OFFICER'", UUID.class);
        jdbcTemplate.update("""
                INSERT INTO app_users (id, email, full_name, department, status)
                VALUES (?, ?, ?, 'Compliance', 'ACTIVE')
                """, userId, email, fullName);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        jdbcTemplate.update("""
                INSERT INTO auth_credentials (app_user_id, password_hash)
                VALUES (?, ?)
                """, userId, passwordEncoder.encode("officer-pass"));
        return userId;
    }

    private Integer auditCount(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?",
                Integer.class,
                action
        );
    }
}
