package com.nyle.kra.revenue.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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
class RuleEngineIntegrationTest extends PostgresIntegrationTest {

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
        resetThresholds();
    }

    @Test
    void allFirstRulesProduceExplainableSignalsAndJobIsIdempotent() throws Exception {
        String token = login();
        seedAllRuleScenarios();

        JsonNode firstRun = runRules(token);

        assertThat(firstRun.get("rulesExecuted").asInt()).isEqualTo(11);
        assertThat(firstRun.get("signalsTouched").asInt()).isEqualTo(11);
        assertThat(signalCount()).isEqualTo(11);
        assertThat(signalCodes()).containsExactlyInAnyOrder(
                "VAT_OUTPUT_MISMATCH",
                "VAT_INPUT_MISMATCH",
                "IMPORT_TO_SALES_MISMATCH",
                "WHT_INCOME_MISMATCH",
                "NIL_FILER_ISSUING_INVOICES",
                "PAYE_RATIO_ANOMALY",
                "PERMIT_ACTIVE_TAX_INACTIVE",
                "PAYMENT_SETTLEMENT_MISMATCH",
                "RENTAL_INCOME_MISMATCH",
                "SECTOR_MARGIN_DEVIATION",
                "EXPENSE_FROM_NON_COMPLIANT_SUPPLIER"
        );
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM risk_signals
                WHERE explanation <> ''
                  AND evidence ? 'ruleCode'
                  AND evidence ? 'thresholds'
                  AND confidence_score > 0
                  AND deterministic_key IS NOT NULL
                """, Integer.class)).isEqualTo(11);

        JsonNode secondRun = runRules(token);

        assertThat(secondRun.get("rulesExecuted").asInt()).isEqualTo(11);
        assertThat(signalCount()).isEqualTo(11);
    }

    @Test
    void viewerReturnsSignalEvidenceAndTaxpayerContext() throws Exception {
        String token = login();
        seedAllRuleScenarios();
        runRules(token);

        mockMvc.perform(get("/api/rules/signals")
                        .header("Authorization", "Bearer " + token)
                        .param("ruleCode", "VAT_OUTPUT_MISMATCH")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleCode").value("VAT_OUTPUT_MISMATCH"))
                .andExpect(jsonPath("$[0].taxpayerPin").value("P600000001A"))
                .andExpect(jsonPath("$[0].evidence.ruleCode").value("VAT_OUTPUT_MISMATCH"))
                .andExpect(jsonPath("$[0].explanation").isNotEmpty());
    }

    @Test
    void thresholdsAreConfigurableAndCanSuppressSignals() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer("P600000009I", "Threshold Traders Ltd");
        vatReturn(taxpayer, 0, 0, 0, "FILED");
        salesInvoice("INV-P6-THRESHOLD", taxpayer, null, "2025-01-10", 200000, 32000);

        mockMvc.perform(put("/api/rules/{code}/thresholds", "VAT_OUTPUT_MISMATCH")
                        .header("Authorization", "Bearer " + token)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull("""
                                {
                                  "thresholdConfig": {
                                    "minimumGap": 999999,
                                    "minimumGapPercent": 10,
                                    "periodGraceDays": 45
                                  },
                                  "severity": "LOW",
                                  "active": true
                                }
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity").value("LOW"))
                .andExpect(jsonPath("$.thresholdConfig.minimumGap").value(999999));

        runRules(token, "VAT_OUTPUT_MISMATCH");

        assertThat(signalCount()).isZero();
    }

    @Test
    void obviousTimingDifferencesInsideGraceWindowAreIgnored() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer("P600000010J", "Grace Window Traders Ltd");
        jdbcTemplate.update("""
                INSERT INTO tax_returns (
                    id, taxpayer_id, tax_head, period_start, period_end, return_reference,
                    declared_sales, declared_output_tax, filing_status
                )
                VALUES (?, ?, 'VAT', date_trunc('month', current_date)::date,
                        (date_trunc('month', current_date)::date + INTERVAL '1 month - 1 day')::date,
                        'RET-P6-GRACE', 0, 0, 'FILED')
                """, UUID.randomUUID(), taxpayer);
        jdbcTemplate.update("""
                INSERT INTO invoices (
                    id, invoice_number, supplier_taxpayer_id, invoice_date, invoice_type,
                    invoice_status, taxable_amount, tax_amount, total_amount, currency
                )
                VALUES (?, 'INV-P6-GRACE', ?, current_date, 'SALE',
                        'VALID', 250000.00, 40000.00, 290000.00, 'KES')
                """, UUID.randomUUID(), taxpayer);

        runRules(token, "VAT_OUTPUT_MISMATCH");

        assertThat(signalCount()).isZero();
    }

    @Test
    void performanceRunHandlesSyntheticMvpSlice() throws Exception {
        String token = login();
        for (int index = 0; index < 150; index++) {
            UUID taxpayer = taxpayer("P61%07dZ".formatted(index), "Synthetic Slice " + index);
            vatReturn(taxpayer, 1000, 160, 100, "FILED");
            salesInvoice("INV-P6-PERF-" + index, taxpayer, null, "2025-01-10", 1500, 240);
        }

        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> runRules(token));
    }

    private JsonNode runRules(String token, String code) throws Exception {
        String response = mockMvc.perform(post("/api/rules/jobs")
                        .header("Authorization", "Bearer " + token)
                        .param("code", code))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode runRules(String token) throws Exception {
        String response = mockMvc.perform(post("/api/rules/jobs")
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

    private void seedAllRuleScenarios() {
        UUID vatOutput = taxpayer("P600000001A", "VAT Output Traders Ltd");
        taxObligation(vatOutput, "VAT", "ACTIVE");
        vatReturn(vatOutput, 50000, 8000, 0, "FILED");
        salesInvoice("INV-P6-OUTPUT", vatOutput, null, "2025-01-10", 200000, 32000);

        UUID vatInput = taxpayer("P600000002B", "VAT Input Traders Ltd");
        vatReturn(vatInput, 100000, 16000, 10000, "FILED");
        salesInvoice("INV-P6-INPUT", vatOutput, vatInput, "2025-01-11", 500000, 80000);

        UUID importer = taxpayer("P600000003C", "Import Gap Ltd");
        incomeReturn(importer, 100000);
        customs(importer, "CUS-P6-IMPORT", 500000);

        UUID whtPayee = taxpayer("P600000004D", "WHT Gap Ltd");
        incomeReturn(whtPayee, 10000);
        whtCertificate(whtPayee, "WHT-P6-GAP", 100000, 5000);

        UUID nilFiler = taxpayer("P600000005E", "Nil Issuer Ltd");
        vatReturn(nilFiler, 0, 0, 0, "NIL");
        salesInvoice("INV-P6-NIL", nilFiler, null, "2025-01-13", 25000, 4000);

        UUID payroll = taxpayer("P600000006F", "Payroll Ratio Ltd");
        payrollReturn(payroll, 200000, 1000);

        UUID permitOnly = taxpayer("P600000007G", "Permit Only Ltd");
        permit(permitOnly, "BP-P6-INACTIVE", 20000);

        payment("PAY-P6-UNSETTLED", "KRA", "MPESA", "2025-01-15T10:00:00Z", 50000);

        UUID landlord = taxpayer("P600000011K", "Rental Gap Ltd");
        incomeReturn(landlord, 10000);
        property(landlord, "PROP-P6-RENTAL", 10000);

        UUID sectorTarget = taxpayer("P600000012L", "Low Margin Sector Ltd");
        setSector(sectorTarget, "RET", "Retail Trade");
        incomeReturn(sectorTarget, 300000, 3000);
        UUID sectorPeerOne = taxpayer("P600000013M", "Peer Margin One Ltd");
        setSector(sectorPeerOne, "RET", "Retail Trade");
        incomeReturn(sectorPeerOne, 300000, 150000);
        UUID sectorPeerTwo = taxpayer("P600000014N", "Peer Margin Two Ltd");
        setSector(sectorPeerTwo, "RET", "Retail Trade");
        incomeReturn(sectorPeerTwo, 300000, 150000);

        UUID buyer = taxpayer("P600000015O", "Buyer With Risky Expense Ltd");
        UUID inactiveSupplier = taxpayer("P600000016P", "Inactive Supplier Ltd", "DORMANT");
        salesInvoice("INV-P6-RISKY-SUPPLIER", inactiveSupplier, buyer, "2025-01-16", 80000, 0);
    }

    private UUID taxpayer(String pin, String legalName) {
        return taxpayer(pin, legalName, "ACTIVE");
    }

    private UUID taxpayer(String pin, String legalName, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO taxpayers (id, kra_pin, taxpayer_type, legal_name, registration_number, county, status)
                VALUES (?, ?, 'COMPANY', ?, ?, 'Nairobi', ?)
                """, id, pin, legalName, "BN-" + pin, status);
        return id;
    }

    private void vatReturn(UUID taxpayer, int sales, int outputTax, int inputTax, String filingStatus) {
        jdbcTemplate.update("""
                INSERT INTO tax_returns (
                    id, taxpayer_id, tax_head, period_start, period_end, return_reference,
                    declared_sales, declared_output_tax, declared_input_tax, filing_status
                )
                VALUES (?, ?, 'VAT', ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), taxpayer, PERIOD_START, PERIOD_END, "RET-" + UUID.randomUUID(),
                sales, outputTax, inputTax, filingStatus);
    }

    private void incomeReturn(UUID taxpayer, int income) {
        incomeReturn(taxpayer, income, income);
    }

    private void incomeReturn(UUID taxpayer, int sales, int income) {
        jdbcTemplate.update("""
                INSERT INTO tax_returns (
                    id, taxpayer_id, tax_head, period_start, period_end, return_reference,
                    declared_income, declared_sales, filing_status
                )
                VALUES (?, ?, 'INCOME_TAX', ?, ?, ?, ?, ?, 'FILED')
                """, UUID.randomUUID(), taxpayer, PERIOD_START, PERIOD_END, "RET-" + UUID.randomUUID(),
                income, sales);
    }

    private void setSector(UUID taxpayer, String sectorCode, String sectorName) {
        jdbcTemplate.update("""
                UPDATE taxpayers
                SET sector_code = ?, sector_name = ?
                WHERE id = ?
                """, sectorCode, sectorName, taxpayer);
    }

    private void property(UUID taxpayer, String reference, int estimatedMonthlyRent) {
        jdbcTemplate.update("""
                INSERT INTO properties (
                    id, owner_taxpayer_id, property_reference, county, property_type,
                    valuation_amount, estimated_monthly_rent
                )
                VALUES (?, ?, ?, 'Nairobi', 'Residential rental', 2500000, ?)
                """, UUID.randomUUID(), taxpayer, reference, estimatedMonthlyRent);
    }

    private void taxObligation(UUID taxpayer, String taxHead, String status) {
        jdbcTemplate.update("""
                INSERT INTO tax_obligations (
                    id, taxpayer_id, tax_head, obligation_status, effective_from
                )
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), taxpayer, taxHead, status, PERIOD_START);
    }

    private void salesInvoice(
            String number,
            UUID supplier,
            UUID buyer,
            String invoiceDate,
            int taxableAmount,
            int taxAmount
    ) {
        jdbcTemplate.update("""
                INSERT INTO invoices (
                    id, invoice_number, supplier_taxpayer_id, buyer_taxpayer_id, invoice_date,
                    invoice_type, invoice_status, taxable_amount, tax_amount, total_amount, currency
                )
                VALUES (?, ?, ?, ?, CAST(? AS date), 'SALE', 'VALID', ?, ?, ?, 'KES')
                """, UUID.randomUUID(), number, supplier, buyer, invoiceDate,
                taxableAmount, taxAmount, taxableAmount + taxAmount);
    }

    private void customs(UUID taxpayer, String number, int customsValue) {
        jdbcTemplate.update("""
                INSERT INTO customs_declarations (
                    id, taxpayer_id, declaration_number, declaration_type, declaration_date,
                    customs_value, duty_amount, vat_amount, total_landed_cost
                )
                VALUES (?, ?, ?, 'IMPORT', '2025-01-10', ?, 0, 0, ?)
                """, UUID.randomUUID(), taxpayer, number, customsValue, customsValue);
    }

    private void whtCertificate(UUID payee, String number, int grossAmount, int withheldAmount) {
        jdbcTemplate.update("""
                INSERT INTO withholding_certificates (
                    id, certificate_number, payee_taxpayer_id, certificate_date,
                    payment_period_start, payment_period_end, gross_amount, withheld_amount
                )
                VALUES (?, ?, ?, '2025-01-20', ?, ?, ?, ?)
                """, UUID.randomUUID(), number, payee, PERIOD_START, PERIOD_END, grossAmount, withheldAmount);
    }

    private void payrollReturn(UUID taxpayer, int grossPay, int payeDue) {
        jdbcTemplate.update("""
                INSERT INTO payroll_returns (
                    id, taxpayer_id, period_start, period_end, employee_count,
                    gross_pay, paye_due, paye_paid, filing_status
                )
                VALUES (?, ?, ?, ?, 12, ?, ?, ?, 'FILED')
                """, UUID.randomUUID(), taxpayer, PERIOD_START, PERIOD_END, grossPay, payeDue, payeDue);
    }

    private void permit(UUID taxpayer, String number, int fee) {
        jdbcTemplate.update("""
                INSERT INTO business_permits (
                    id, taxpayer_id, permit_number, county, business_activity,
                    valid_from, valid_to, permit_fee, permit_status
                )
                VALUES (?, ?, ?, 'Nairobi', 'Retail trade', ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), taxpayer, number, PERIOD_START, PERIOD_END, fee);
    }

    private void payment(String reference, String agency, String channel, String paymentDate, int amount) {
        jdbcTemplate.update("""
                INSERT INTO payment_transactions (
                    id, transaction_reference, collecting_agency, revenue_channel,
                    payment_date, amount, payment_status
                )
                VALUES (?, ?, ?, ?, CAST(? AS timestamptz), ?, 'PAID')
                """, UUID.randomUUID(), reference, agency, channel, paymentDate, amount);
    }

    private Integer signalCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM risk_signals", Integer.class);
    }

    private java.util.List<String> signalCodes() {
        return jdbcTemplate.queryForList("""
                SELECT rr.code
                FROM risk_signals rs
                JOIN risk_rules rr ON rr.id = rs.risk_rule_id
                """, String.class);
    }

    private void resetThresholds() {
        jdbcTemplate.update("""
                UPDATE risk_rules
                SET severity = 'HIGH',
                    active = TRUE,
                    threshold_config = '{"minimumGap": 100000, "minimumGapPercent": 10, "periodGraceDays": 45}'::jsonb
                WHERE code = 'VAT_OUTPUT_MISMATCH'
                """);
        jdbcTemplate.update("""
                UPDATE risk_rules
                SET severity = 'HIGH',
                    active = TRUE,
                    threshold_config = '{"minimumGap": 50000, "minimumGapPercent": 10, "periodGraceDays": 45}'::jsonb
                WHERE code = 'VAT_INPUT_MISMATCH'
                """);
    }
}
