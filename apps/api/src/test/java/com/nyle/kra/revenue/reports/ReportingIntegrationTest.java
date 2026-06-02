package com.nyle.kra.revenue.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
class ReportingIntegrationTest extends PostgresIntegrationTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2025, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2025, 1, 31);

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
    void phaseFifteenReportsSummariesAndExportsAreAvailable() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer();
        UUID signal = riskSignal(taxpayer);
        taxGapEstimate(taxpayer);
        caseWithRecovery(taxpayer, signal);

        mockMvc.perform(get("/api/reports/tax-gap/by-sector")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sectorCode").value("RET"))
                .andExpect(jsonPath("$[0].sectorName").value("Retail Trade"))
                .andExpect(jsonPath("$[0].estimatedGap").value(250000.00))
                .andExpect(jsonPath("$[0].estimatedRecoverableTax").value(40000.00));

        mockMvc.perform(get("/api/reports/tax-gap/by-region")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].region").value("Nairobi"))
                .andExpect(jsonPath("$[0].taxpayerCount").value(1));

        mockMvc.perform(get("/api/reports/officer-productivity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assignedCases").value(1))
                .andExpect(jsonPath("$[0].collectedAmount").value(20000.00));

        mockMvc.perform(get("/api/reports/revenue-recovery")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taxHead").value("VAT"))
                .andExpect(jsonPath("$[0].recoveryRecords").value(1));

        mockMvc.perform(get("/api/reports/audit-pipeline")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].caseCount").value(1));

        mockMvc.perform(get("/api/reports/exports/tax-gap-by-sector.csv")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Retail Trade")));

        byte[] xlsx = mockMvc.perform(get("/api/reports/exports/tax-gap-by-sector.xlsx")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2)).isEqualTo("PK");

        byte[] pdf = mockMvc.perform(get("/api/reports/exports/tax-gap-by-sector.pdf")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
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
                VALUES (?, 'P150000001A', 'COMPANY', 'Phase Fifteen Retail Ltd', 'BN-P15',
                        'RET', 'Retail Trade', 'Nairobi', 'ACTIVE')
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
                VALUES (?, ?, ?, 'VAT_OUTPUT_MISMATCH', 'VAT', ?, ?, 300000, 50000, 250000,
                        92.00, 'HIGH', 'Report fixture signal', '{"sourceRecord":"report-fixture"}'::jsonb, 'OPEN')
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
                VALUES (?, ?, ?, 'VAT', ?, ?, 50000, 300000, 250000, 40000,
                        8000, 2000, 50000, 92.00, '{"source":"test"}'::jsonb)
                """, UUID.randomUUID(), "phase15-report-" + taxpayer, taxpayer, PERIOD_START, PERIOD_END);
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
                VALUES (?, 'CASE-P15-001', ?, ?, 'Phase 15 reporting case', 'RISK_SIGNAL',
                        'HIGH', 'OPEN', 40000, ?)
                """, caseId, taxpayer, signal, officerId);
        jdbcTemplate.update("""
                INSERT INTO recovery_records (
                    id, case_id, assessed_amount, agreed_amount, collected_amount, collection_date, recovery_status
                )
                VALUES (?, ?, 30000, 25000, 20000, ?, 'PARTIAL')
                """, UUID.randomUUID(), caseId, PERIOD_END);
    }
}
