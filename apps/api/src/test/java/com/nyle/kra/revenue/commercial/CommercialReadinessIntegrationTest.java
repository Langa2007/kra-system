package com.nyle.kra.revenue.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

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
class CommercialReadinessIntegrationTest extends PostgresIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM recovery_records");
        jdbcTemplate.update("DELETE FROM evidence_packs");
        jdbcTemplate.update("DELETE FROM case_events");
        jdbcTemplate.update("DELETE FROM cases");
        jdbcTemplate.update("DELETE FROM reconciliation_results");
        jdbcTemplate.update("DELETE FROM tax_gap_estimates");
        jdbcTemplate.update("DELETE FROM risk_scores");
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
    void phaseSixteenPilotPackageRoiAndExportsAreAvailable() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer();
        UUID signal = riskSignal(taxpayer);
        taxGapEstimate(taxpayer);
        caseWithRecovery(taxpayer, signal);
        reconciliationException(signal);

        mockMvc.perform(get("/api/commercial/pilot-package")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("Phase 16"))
                .andExpect(jsonPath("$.roi.estimatedGap").value(500000.00))
                .andExpect(jsonPath("$.roi.recoverableTax").value(125000.00))
                .andExpect(jsonPath("$.roi.settlementVariance").value(75000.00))
                .andExpect(jsonPath("$.roi.openCases").value(1))
                .andExpect(jsonPath("$.documents[0].filePath").value("docs/phase16/pilot-proposal.md"))
                .andExpect(jsonPath("$.sampleDashboards[5]").value("Pilot ROI calculator"));

        byte[] pdf = mockMvc.perform(get("/api/commercial/exports/pilot-package.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        byte[] xlsx = mockMvc.perform(get("/api/commercial/exports/roi-calculator.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");
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

    private UUID taxpayer() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO taxpayers (
                    id, kra_pin, taxpayer_type, legal_name, registration_number,
                    sector_code, sector_name, county, status
                )
                VALUES (?, 'P160000001A', 'COMPANY', 'Phase Sixteen Pilot Ltd', 'BN-P16',
                        'PIL', 'Pilot Services', 'Nairobi', 'ACTIVE')
                """, id);
        return id;
    }

    private UUID riskSignal(UUID taxpayer) {
        UUID id = UUID.randomUUID();
        UUID ruleId = jdbcTemplate.queryForObject(
                "SELECT id FROM risk_rules WHERE code = 'VAT_OUTPUT_MISMATCH'",
                UUID.class
        );
        jdbcTemplate.update("""
                INSERT INTO risk_signals (
                    id, taxpayer_id, risk_rule_id, signal_type, tax_head, period_start, period_end,
                    observed_amount, declared_amount, estimated_gap, confidence_score, severity,
                    explanation, evidence, status
                )
                VALUES (?, ?, ?, 'VAT_OUTPUT_MISMATCH', 'VAT', ?, ?, 700000, 200000, 500000,
                        91.00, 'HIGH', 'Commercial readiness fixture signal', '{"sourceRecord":"phase16"}'::jsonb, 'OPEN')
                """, id, taxpayer, ruleId, PERIOD_START, PERIOD_END);
        return id;
    }

    private void taxGapEstimate(UUID taxpayer) {
        jdbcTemplate.update("""
                INSERT INTO tax_gap_estimates (
                    id, deterministic_key, taxpayer_id, tax_head, period_start, period_end,
                    declared_amount, observed_amount, estimated_gap, estimated_recoverable_tax,
                    estimated_penalty, estimated_interest, estimated_total_due, confidence_score, evidence
                )
                VALUES (?, ?, ?, 'VAT', ?, ?, 200000, 700000, 500000, 125000,
                        25000, 5000, 155000, 91.00, '{"source":"phase16-test"}'::jsonb)
                """, UUID.randomUUID(), "phase16-roi-" + taxpayer, taxpayer, PERIOD_START, PERIOD_END);
    }

    private void caseWithRecovery(UUID taxpayer, UUID signal) {
        UUID caseId = UUID.randomUUID();
        UUID officerId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_users WHERE email = ?",
                UUID.class,
                TEST_ADMIN_EMAIL
        );
        jdbcTemplate.update("""
                INSERT INTO cases (
                    id, case_number, taxpayer_id, risk_signal_id, title, case_type, priority,
                    status, estimated_recoverable_amount, assigned_to
                )
                VALUES (?, 'CASE-P16-001', ?, ?, 'Phase 16 pilot case', 'RISK_SIGNAL',
                        'HIGH', 'OPEN', 125000, ?)
                """, caseId, taxpayer, signal, officerId);
        jdbcTemplate.update("""
                INSERT INTO recovery_records (
                    id, case_id, assessed_amount, agreed_amount, collected_amount, collection_date, recovery_status
                )
                VALUES (?, ?, 90000, 80000, 60000, ?, 'PARTIAL')
                """, UUID.randomUUID(), caseId, PERIOD_END);
    }

    private void reconciliationException(UUID signal) {
        jdbcTemplate.update("""
                INSERT INTO reconciliation_results (
                    id, deterministic_key, reconciliation_date, collecting_agency, revenue_channel,
                    expected_amount, settled_amount, variance_amount, transaction_count, settlement_count,
                    settlement_status, expected_settlement_account, settlement_account, max_settlement_lag_days,
                    evidence, risk_signal_id
                )
                VALUES (?, ?, ?, 'Pilot County', 'MPESA', 175000, 100000, 75000, 12, 1,
                        'PARTIAL_SETTLEMENT', 'CBK-KRA-MAIN', 'CBK-KRA-MAIN', 1,
                        '{"source":"phase16-test"}'::jsonb, ?)
                """, UUID.randomUUID(), "phase16-recon-" + signal, PERIOD_END, signal);
    }
}
