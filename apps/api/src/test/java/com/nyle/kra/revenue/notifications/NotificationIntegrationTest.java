package com.nyle.kra.revenue.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class NotificationIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 2, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 2, 28);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDomainRecords() {
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM recovery_records");
        jdbcTemplate.update("DELETE FROM evidence_packs");
        jdbcTemplate.update("DELETE FROM case_events");
        jdbcTemplate.update("DELETE FROM cases");
        jdbcTemplate.update("DELETE FROM model_predictions");
        jdbcTemplate.update("DELETE FROM reconciliation_results");
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
    void templatesAreAvailableForOfficerReview() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/notifications/templates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'SOFT_COMPLIANCE_EMAIL')]").exists())
                .andExpect(jsonPath("$[?(@.code == 'SOFT_COMPLIANCE_SMS')]").exists());
    }

    @Test
    void nudgeCanBeGeneratedFromRiskSignalAndTrackedInHistory() throws Exception {
        String token = login();
        UUID signal = signalWithGap(taxpayer("P900000001A", "Voluntary Signal Ltd"), "VAT_OUTPUT_MISMATCH", "VAT", "120000.00");
        int previousAudits = auditCount("NOTIFICATION_SENT");

        String response = mockMvc.perform(post("/api/notifications/nudges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "riskSignalId": "%s",
                                  "channel": "EMAIL"
                                }
                                """.formatted(signal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.templateCode").value("SOFT_COMPLIANCE_EMAIL"))
                .andExpect(jsonPath("$.messageBody").value(Objects.requireNonNull(org.hamcrest.Matchers.containsString("VAT_OUTPUT_MISMATCH"))))
                .andExpect(jsonPath("$.recipient").value("p900000001a@example.test"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID notificationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        assertThat(auditCount("NOTIFICATION_SENT")).isEqualTo(previousAudits + 1);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token)
                        .param("riskSignalId", signal.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].status").value("SENT"));
    }

    @Test
    void nudgeCanBeGeneratedFromCaseAndTaxpayerResponseRecorded() throws Exception {
        String token = login();
        UUID signal = signalWithGap(taxpayer("P900000002B", "Voluntary Case Ltd"), "WHT_INCOME_MISMATCH", "WITHHOLDING_TAX", "85000.00");
        UUID caseId = createCase(token, signal);
        int previousResponseAudits = auditCount("TAXPAYER_RESPONSE_RECORDED");

        JsonNode nudge = objectMapper.readTree(mockMvc.perform(post("/api/notifications/nudges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "caseId": "%s",
                                  "channel": "SMS"
                                }
                                """.formatted(caseId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value(caseId.toString()))
                .andExpect(jsonPath("$.templateCode").value("SOFT_COMPLIANCE_SMS"))
                .andExpect(jsonPath("$.deliveryProvider").value("LOCAL_SMS"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        UUID notificationId = UUID.fromString(nudge.get("id").asText());
        mockMvc.perform(post("/api/notifications/{id}/response", notificationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "responseStatus": "EXPLANATION_RECEIVED",
                                  "responseBody": "Taxpayer uploaded reconciliation records."
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESPONDED"))
                .andExpect(jsonPath("$.responseStatus").value("EXPLANATION_RECEIVED"))
                .andExpect(jsonPath("$.responseBody").value("Taxpayer uploaded reconciliation records."));

        assertThat(auditCount("TAXPAYER_RESPONSE_RECORDED")).isEqualTo(previousResponseAudits + 1);
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token)
                        .param("caseId", caseId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].status").value("RESPONDED"));
    }

    private UUID createCase(String token, UUID signal) throws Exception {
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
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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
                VALUES (?, ?, ?, ?, ?, ?, ?, 500000.00, 250000.00, ?, 89.00, 'MEDIUM',
                        'Synthetic signal for voluntary compliance', CAST(? AS jsonb), 'OPEN')
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
        return signal;
    }

    private Integer auditCount(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ?",
                Integer.class,
                action
        );
    }
}
