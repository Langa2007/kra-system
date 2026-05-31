package com.nyle.kra.revenue.taxgap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class TaxGapIntegrationTest extends PostgresIntegrationTest {

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
    void calculationsRunForEveryTaxGapCategoryAndAreIdempotent() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer("P700000001A", "Phase Seven Holdings Ltd");
        seedEverySupportedSignal(taxpayer);

        JsonNode first = runTaxGapJob(token);

        assertThat(first.get("sourceSignalsUsed").asInt()).isEqualTo(8);
        assertThat(first.get("estimatesTouched").asInt()).isEqualTo(5);
        assertThat(estimateCount()).isEqualTo(5);
        assertThat(taxHeads()).containsExactlyInAnyOrder(
                "VAT",
                "INCOME_TAX",
                "WITHHOLDING_TAX",
                "PAYE",
                "REVENUE_ASSURANCE"
        );

        JsonNode second = runTaxGapJob(token);

        assertThat(second.get("sourceSignalsUsed").asInt()).isEqualTo(8);
        assertThat(estimateCount()).isEqualTo(5);
    }

    @Test
    void edgeCasesHandleNilReturnsMissingRecordsAndNegativeValues() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer("P700000002B", "Edge Case Retail Ltd");
        UUID nilSignal = riskSignal(
                taxpayer,
                "NIL_FILER_ISSUING_INVOICES",
                "VAT",
                null,
                new BigDecimal("25000.00"),
                new BigDecimal("25000.00"),
                "Nil return with invoices",
                "{\"sourceRecord\":\"nil-return\"}"
        );
        riskSignal(
                taxpayer,
                "VAT_OUTPUT_MISMATCH",
                "VAT",
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("-40000.00"),
                "Credit note or negative adjustment should not create a gap",
                "{\"invoiceStatus\":\"CREDIT_NOTE\"}"
        );
        riskSignal(
                taxpayer,
                "VAT_OUTPUT_MISMATCH",
                "VAT",
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                "Cancelled invoice signal with zero gap should be ignored",
                "{\"invoiceStatus\":\"CANCELLED\"}"
        );

        runTaxGapJob(token);

        assertThat(estimateCount()).isEqualTo(1);
        JsonNode evidence = estimateEvidence("VAT");
        assertThat(evidence.get("signalIds").toString()).contains(nilSignal.toString());
        BigDecimal declared = jdbcTemplate.queryForObject(
                "SELECT declared_amount FROM tax_gap_estimates WHERE tax_head = 'VAT'",
                BigDecimal.class
        );
        assertThat(declared).isEqualByComparingTo("0.00");
    }

    @Test
    void summariesMatchUnderlyingRiskSignals() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer("P700000003C", "Summary Traders Ltd");
        riskSignal(taxpayer, "VAT_OUTPUT_MISMATCH", "VAT", 100000, 300000, 200000);
        riskSignal(taxpayer, "VAT_INPUT_MISMATCH", "VAT", 10000, 80000, 70000);

        runTaxGapJob(token);

        mockMvc.perform(get("/api/tax-gaps/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taxHead").value("VAT"))
                .andExpect(jsonPath("$[0].estimatedGap").value(270000.00))
                .andExpect(jsonPath("$[0].estimatedRecoverableTax").value(102000.00));
    }

    @Test
    void rankingOrdersByEstimatedRecoverableAmount() throws Exception {
        String token = login();
        UUID lower = taxpayer("P700000004D", "Lower Gap Ltd");
        UUID higher = taxpayer("P700000005E", "Higher Gap Ltd");
        riskSignal(lower, "VAT_OUTPUT_MISMATCH", "VAT", 0, 100000, 100000);
        riskSignal(higher, "PAYE_RATIO_ANOMALY", "PAYE", 0, 150000, 150000);

        runTaxGapJob(token);

        mockMvc.perform(get("/api/tax-gaps/ranking")
                        .header("Authorization", "Bearer " + token)
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taxpayerId").value(higher.toString()))
                .andExpect(jsonPath("$[0].estimatedRecoverableTax").value(150000.00))
                .andExpect(jsonPath("$[1].taxpayerId").value(lower.toString()));
    }

    @Test
    void estimateViewerReturnsEvidenceLinksToSourceSignals() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer("P700000006F", "Evidence Links Ltd");
        UUID signal = riskSignal(taxpayer, "WHT_INCOME_MISMATCH", "WITHHOLDING_TAX", 10000, 100000, 90000);

        runTaxGapJob(token);

        mockMvc.perform(get("/api/tax-gaps/estimates")
                        .header("Authorization", "Bearer " + token)
                        .param("taxHead", "WITHHOLDING_TAX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taxpayerId").value(taxpayer.toString()))
                .andExpect(jsonPath("$[0].evidence.source").value("TAX_GAP_ENGINE"))
                .andExpect(jsonPath("$[0].evidence.signalIds[0]").value(signal.toString()))
                .andExpect(jsonPath("$[0].estimatedRecoverableTax").value(4500.00));
    }

    private JsonNode runTaxGapJob(String token) throws Exception {
        String response = mockMvc.perform(post("/api/tax-gaps/jobs")
                        .header("Authorization", "Bearer " + token))
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

    private void seedEverySupportedSignal(UUID taxpayer) {
        riskSignal(taxpayer, "VAT_OUTPUT_MISMATCH", "VAT", 50000, 200000, 150000);
        riskSignal(taxpayer, "VAT_INPUT_MISMATCH", "VAT", 10000, 80000, 70000);
        riskSignal(taxpayer, "NIL_FILER_ISSUING_INVOICES", "VAT", 0, 25000, 25000);
        riskSignal(taxpayer, "IMPORT_TO_SALES_MISMATCH", "INCOME_TAX", 100000, 500000, 400000);
        riskSignal(taxpayer, "PERMIT_ACTIVE_TAX_INACTIVE", "INCOME_TAX", 0, 20000, 20000);
        riskSignal(taxpayer, "WHT_INCOME_MISMATCH", "WITHHOLDING_TAX", 10000, 100000, 90000);
        riskSignal(taxpayer, "PAYE_RATIO_ANOMALY", "PAYE", 1000, 16000, 15000);
        riskSignal(taxpayer, "PAYMENT_SETTLEMENT_MISMATCH", "REVENUE_ASSURANCE", 0, 50000, 50000);
    }

    private UUID taxpayer(String pin, String legalName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO taxpayers (id, kra_pin, taxpayer_type, legal_name, registration_number, county, status)
                VALUES (?, ?, 'COMPANY', ?, ?, 'Nairobi', 'ACTIVE')
                """, id, pin, legalName, "BN-" + pin);
        return id;
    }

    private UUID riskSignal(
            UUID taxpayer,
            String signalType,
            String taxHead,
            int declaredAmount,
            int observedAmount,
            int estimatedGap
    ) {
        return riskSignal(
                taxpayer,
                signalType,
                taxHead,
                new BigDecimal(declaredAmount),
                new BigDecimal(observedAmount),
                new BigDecimal(estimatedGap),
                signalType + " test signal",
                "{\"sourceRecord\":\"" + signalType + "\"}"
        );
    }

    private UUID riskSignal(
            UUID taxpayer,
            String signalType,
            String taxHead,
            BigDecimal declaredAmount,
            BigDecimal observedAmount,
            BigDecimal estimatedGap,
            String explanation,
            String evidence
    ) {
        UUID id = UUID.randomUUID();
        UUID ruleId = jdbcTemplate.queryForObject(
                "SELECT id FROM risk_rules WHERE code = ?",
                UUID.class,
                signalType
        );
        jdbcTemplate.update("""
                INSERT INTO risk_signals (
                    id, taxpayer_id, risk_rule_id, signal_type, tax_head, period_start, period_end,
                    observed_amount, declared_amount, estimated_gap, confidence_score, severity,
                    explanation, evidence, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 90.00, 'HIGH', ?, CAST(? AS jsonb), 'OPEN')
                """, id, taxpayer, ruleId, signalType, taxHead, PERIOD_START, PERIOD_END,
                observedAmount, declaredAmount, estimatedGap, explanation, evidence);
        return id;
    }

    private Integer estimateCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM tax_gap_estimates", Integer.class);
    }

    private List<String> taxHeads() {
        return jdbcTemplate.queryForList("SELECT tax_head FROM tax_gap_estimates", String.class);
    }

    private JsonNode estimateEvidence(String taxHead) throws Exception {
        String evidence = jdbcTemplate.queryForObject(
                "SELECT evidence::text FROM tax_gap_estimates WHERE tax_head = ?",
                String.class,
                taxHead
        );
        return objectMapper.readTree(evidence);
    }
}
