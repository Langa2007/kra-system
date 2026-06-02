package com.nyle.kra.revenue.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
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
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ReconciliationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDomainRecords() {
        jdbcTemplate.update("DELETE FROM recovery_records");
        jdbcTemplate.update("DELETE FROM evidence_packs");
        jdbcTemplate.update("DELETE FROM case_events");
        jdbcTemplate.update("DELETE FROM cases");
        jdbcTemplate.update("DELETE FROM reconciliation_results");
        jdbcTemplate.update("DELETE FROM risk_signals");
        jdbcTemplate.update("DELETE FROM payment_transactions");
        jdbcTemplate.update("DELETE FROM settlement_records");
    }

    @Test
    void runCreatesExceptionReportSignalsAndSettlementCase() throws Exception {
        String token = login();
        seedReconciliationScenarios();

        JsonNode run = runJob(token);

        assertThat(run.get("resultsTouched").asInt()).isEqualTo(7);
        assertThat(run.get("exceptions").asInt()).isEqualTo(5);
        assertThat(run.get("riskSignalsTouched").asInt()).isEqualTo(5);

        JsonNode results = objectMapper.readTree(mockMvc.perform(get("/api/reconciliation/results")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(statuses(results)).contains(
                "MATCHED",
                "MISSING_SETTLEMENT",
                "PARTIAL_SETTLEMENT",
                "DELAYED_SETTLEMENT",
                "DUPLICATE_TRANSACTION",
                "WRONG_ACCOUNT"
        );

        mockMvc.perform(get("/api/reconciliation/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceptionCount").value(5))
                .andExpect(jsonPath("$.missingCount").value(1))
                .andExpect(jsonPath("$.delayedCount").value(1))
                .andExpect(jsonPath("$.duplicateCount").value(1))
                .andExpect(jsonPath("$.wrongAccountCount").value(1));

        String resultId = firstResultId(results, "MISSING_SETTLEMENT");
        String caseResponse = mockMvc.perform(post("/api/reconciliation/results/{id}/case", resultId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseType").value("SETTLEMENT_RECONCILIATION"))
                .andExpect(jsonPath("$.riskSignalId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String caseId = objectMapper.readTree(caseResponse).get("id").asText();

        mockMvc.perform(post("/api/cases/{id}/evidence-packs", caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileUri").isNotEmpty());
    }

    @Test
    void reconciliationHandlesLargePaymentSetWithinPerformanceGate() throws Exception {
        String token = login();
        seedLargeMatchedPaymentSet(50_000);

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            JsonNode run = runJob(token);
            assertThat(run.get("resultsTouched").asInt()).isGreaterThan(0);
            assertThat(run.get("exceptions").asInt()).isZero();
        });
    }

    private JsonNode runJob(String token) throws Exception {
        String response = mockMvc.perform(post("/api/reconciliation/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "from": "2025-01-01",
                                  "to": "2025-01-31",
                                  "settlementDelayDays": 2,
                                  "expectedSettlementAccount": "CBK-KRA-MAIN"
                                }
                                """)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
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

    private void seedReconciliationScenarios() {
        payment("PAY-MATCH", "REF-MATCH", "KRA", "MPESA", "2025-01-01T10:00:00Z", 1000);
        settlement("SET-MATCH", "KRA", "MPESA", "CBK-KRA-MAIN", "2025-01-02", 1000, 1);

        payment("PAY-MISSING", "REF-MISSING", "KRA", "BANK", "2025-01-02T10:00:00Z", 2000);

        payment("PAY-PARTIAL", "REF-PARTIAL", "KRA", "CARD", "2025-01-03T10:00:00Z", 3000);
        settlement("SET-PARTIAL", "KRA", "CARD", "CBK-KRA-MAIN", "2025-01-04", 1000, 1);

        payment("PAY-DELAYED", "REF-DELAYED", "COUNTY", "MPESA", "2025-01-04T10:00:00Z", 4000);
        settlement("SET-DELAYED", "COUNTY", "MPESA", "CBK-KRA-MAIN", "2025-01-10", 4000, 1);

        payment("PAY-WRONG", "REF-WRONG", "COUNTY", "BANK", "2025-01-05T10:00:00Z", 5000);
        settlement("SET-WRONG", "COUNTY", "BANK", "COMMERCIAL-BANK-123", "2025-01-06", 5000, 1);

        payment("PAY-DUP-A", "DUP-REF", "COUNTY", "PARKING", "2025-01-06T10:00:00Z", 700);
        payment("PAY-DUP-B", "DUP-REF", "COUNTY", "PARKING", "2025-01-06T10:05:00Z", 700);
        settlement("SET-DUP", "COUNTY", "PARKING", "CBK-KRA-MAIN", "2025-01-07", 1400, 2);
    }

    private void seedLargeMatchedPaymentSet(int count) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO payment_transactions (
                    id, transaction_reference, collecting_agency, revenue_channel,
                    payment_date, amount, payment_status, provider_reference
                )
                VALUES (?, ?, 'PERF', 'MPESA', CAST(? AS timestamptz), 1.00, 'PAID', ?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(@NonNull PreparedStatement ps, int i) throws SQLException {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "PAY-PERF-" + i);
                ps.setString(3, "2025-01-%02dT10:00:00Z".formatted((i % 10) + 1));
                ps.setString(4, "REF-PERF-" + i);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });

        for (int day = 1; day <= 10; day++) {
            settlement(
                    "SET-PERF-" + day,
                    "PERF",
                    "MPESA",
                    "CBK-KRA-MAIN",
                    "2025-01-%02d".formatted(day + 1),
                    count / 10,
                    count / 10
            );
        }
    }

    private void payment(String reference, String providerReference, String agency, String channel, String date, int amount) {
        jdbcTemplate.update("""
                INSERT INTO payment_transactions (
                    id, transaction_reference, collecting_agency, revenue_channel,
                    payment_date, amount, payment_status, provider_reference
                )
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), ?, 'PAID', ?)
                """, UUID.randomUUID(), reference, agency, channel, date, amount, providerReference);
    }

    private void settlement(String reference, String agency, String channel, String account, String date, int amount, int count) {
        jdbcTemplate.update("""
                INSERT INTO settlement_records (
                    id, settlement_reference, collecting_agency, revenue_channel,
                    settlement_account, settlement_date, settled_amount, transaction_count, settlement_status
                )
                VALUES (?, ?, ?, ?, ?, CAST(? AS date), ?, ?, 'SETTLED')
                """, UUID.randomUUID(), reference, agency, channel, account, date, amount, count);
    }

    private List<String> statuses(JsonNode results) {
        return results.findValues("settlementStatus").stream().map(JsonNode::asText).toList();
    }

    private String firstResultId(JsonNode results, String status) {
        for (JsonNode result : results) {
            if (status.equals(result.get("settlementStatus").asText())) {
                return result.get("id").asText();
            }
        }
        throw new AssertionError("No result found for status " + status);
    }
}
