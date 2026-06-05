package com.nyle.kra.revenue.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;
import java.util.List;
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
class EntityResolutionIntegrationTest extends PostgresIntegrationTest {

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
    void exactPinResolutionLinksMvpRecordsAndIsIdempotent() throws Exception {
        String token = login();
        UUID supplier = taxpayer("P990000001A", "BN-P5-001", "Exact Supplier Ltd", "Exact Supplier", "Nairobi");
        UUID buyer = taxpayer("P990000002B", "BN-P5-002", "Exact Buyer Ltd", "Exact Buyer", "Nairobi");
        insertFragmentedExactRecords(supplier, buyer, "P990000001A", "P990000002B");

        JsonNode first = runResolution(token);

        assertThat(first.get("kraPinIdentifiersCreated").asInt()).isEqualTo(2);
        assertThat(first.get("registrationIdentifiersCreated").asInt()).isEqualTo(2);
        assertThat(first.get("invoiceSupplierLinks").asInt()).isEqualTo(1);
        assertThat(first.get("invoiceBuyerLinks").asInt()).isEqualTo(1);
        assertThat(first.get("customsLinks").asInt()).isEqualTo(1);
        assertThat(first.get("withholdingPayerLinks").asInt()).isEqualTo(1);
        assertThat(first.get("withholdingPayeeLinks").asInt()).isEqualTo(1);
        assertThat(first.get("propertyLinks").asInt()).isEqualTo(1);
        assertThat(first.get("paymentLinks").asInt()).isEqualTo(1);

        assertLinked("invoices", "supplier_taxpayer_id", supplier);
        assertLinked("invoices", "buyer_taxpayer_id", buyer);
        assertLinked("customs_declarations", "taxpayer_id", supplier);
        assertLinked("properties", "owner_taxpayer_id", supplier);
        assertLinked("payment_transactions", "payer_taxpayer_id", supplier);

        JsonNode second = runResolution(token);
        assertThat(second.get("kraPinIdentifiersCreated").asInt()).isZero();
        assertThat(second.get("registrationIdentifiersCreated").asInt()).isZero();
        assertThat(second.get("invoiceSupplierLinks").asInt()).isZero();
        assertThat(second.get("customsLinks").asInt()).isZero();
        assertThat(second.get("duplicateRelationshipsCreated").asInt()).isZero();
    }

    @Test
    void fuzzyBusinessNameMatchLinksOnlyHighConfidencePermit() throws Exception {
        String token = login();
        UUID taxpayer = taxpayer("P990000003C", "BN-P5-003", "Amani Wholesale Traders Ltd", "Amani Traders", "Nairobi");
        permit("BP-HIGH", "Nairobi", "Amani Wholesale Trader Limited");

        mockMvc.perform(get("/api/entity-resolution/match-candidates")
                        .header("Authorization", "Bearer " + token)
                        .param("name", "Amani Wholesale Trader Limited"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taxpayerId").value(taxpayer.toString()))
                .andExpect(jsonPath("$[0].autoLinkEligible").value(true));

        JsonNode summary = runResolution(token);

        assertThat(summary.get("permitFuzzyLinks").asInt()).isEqualTo(1);
        assertLinked("business_permits", "taxpayer_id", taxpayer);
    }

    @Test
    void lowConfidenceFuzzyPermitCandidateIsNotAutoLinked() throws Exception {
        String token = login();
        taxpayer("P990000004D", "BN-P5-004", "Rift Valley Logistics Ltd", "Rift Logistics", "Nakuru");
        permit("BP-LOW", "Nakuru", "Downtown Bakery");

        JsonNode summary = runResolution(token);

        assertThat(summary.get("permitFuzzyLinks").asInt()).isZero();
        Integer linkedPermits = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM business_permits WHERE taxpayer_id IS NOT NULL",
                Integer.class
        );
        assertThat(linkedPermits).isZero();
    }

    @Test
    void duplicateTaxpayerDetectionUsesRegistrationAndFuzzyNames() throws Exception {
        String token = login();
        UUID first = taxpayer("P990000005E", "BN-DUP-001", "Lake Retail Traders Ltd", "Lake Retail", "Kisumu");
        UUID second = taxpayer("P990000006F", "BN-DUP-001", "Lake Retail Traders Limited", "Lake Retail", "Kisumu");

        String duplicates = mockMvc.perform(get("/api/entity-resolution/duplicate-candidates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchBasis").value("REGISTRATION_NUMBER"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode duplicate = objectMapper.readTree(duplicates).get(0);
        assertThat(List.of(duplicate.get("sourceTaxpayerId").asText(), duplicate.get("targetTaxpayerId").asText()))
                .containsExactlyInAnyOrder(first.toString(), second.toString());

        JsonNode summary = runResolution(token);

        assertThat(summary.get("duplicateRelationshipsCreated").asInt()).isEqualTo(1);
        Integer relationships = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM taxpayer_relationships WHERE relationship_type = 'POSSIBLE_DUPLICATE'",
                Integer.class
        );
        assertThat(relationships).isEqualTo(1);
    }

    @Test
    void taxpayerProfileAggregatesIdentifiersAndLinkedRecords() throws Exception {
        String token = login();
        UUID supplier = taxpayer("P990000007G", "BN-P5-007", "Profile Supplier Ltd", "Profile Supplier", "Mombasa");
        UUID buyer = taxpayer("P990000008H", "BN-P5-008", "Profile Buyer Ltd", "Profile Buyer", "Mombasa");
        insertFragmentedExactRecords(supplier, buyer, "P990000007G", "P990000008H");
        runResolution(token);

        mockMvc.perform(get("/api/taxpayers/{id}/profile", supplier)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxpayerId").value(supplier.toString()))
                .andExpect(jsonPath("$.identifiers[?(@.identifierType == 'KRA_PIN')]").exists())
                .andExpect(jsonPath("$.identifiers[?(@.identifierType == 'REGISTRATION_NUMBER')]").exists())
                .andExpect(jsonPath("$.recordCounts.invoicesAsSupplier").value(1))
                .andExpect(jsonPath("$.recordCounts.customsDeclarations").value(1))
                .andExpect(jsonPath("$.recordCounts.properties").value(1))
                .andExpect(jsonPath("$.recordCounts.paymentTransactions").value(1))
                .andExpect(jsonPath("$.totals.supplierInvoiceTotal").value(1160.00))
                .andExpect(jsonPath("$.totals.customsValue").value(2500.00))
                .andExpect(jsonPath("$.totals.payments").value(800.00));
    }

    private JsonNode runResolution(String token) throws Exception {
        String response = mockMvc.perform(post("/api/entity-resolution/jobs")
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

    private UUID taxpayer(String pin, String registrationNumber, String legalName, String tradingName, String county) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO taxpayers (
                    id, kra_pin, taxpayer_type, legal_name, trading_name, registration_number, county, status
                )
                VALUES (?, ?, 'COMPANY', ?, ?, ?, ?, 'ACTIVE')
                """, id, pin, legalName, tradingName, registrationNumber, county);
        return id;
    }

    private void permit(String permitNumber, String county, String businessActivity) {
        jdbcTemplate.update("""
                INSERT INTO business_permits (id, permit_number, county, business_activity, permit_status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), permitNumber, county, businessActivity);
    }

    private void insertFragmentedExactRecords(UUID supplier, UUID buyer, String supplierPin, String buyerPin) {
        jdbcTemplate.update("""
                INSERT INTO invoices (
                    id, invoice_number, supplier_pin, buyer_pin, invoice_date, invoice_type,
                    invoice_status, taxable_amount, tax_amount, total_amount, currency
                )
                VALUES (?, 'INV-P5-001', ?, ?, '2025-01-10', 'SALE',
                        'VALID', 1000.00, 160.00, 1160.00, 'KES')
                """, UUID.randomUUID(), supplierPin, buyerPin);
        jdbcTemplate.update("""
                INSERT INTO customs_declarations (
                    id, importer_pin, declaration_number, declaration_type, declaration_date,
                    customs_value, duty_amount, vat_amount
                )
                VALUES (?, ?, 'CUS-P5-001', 'IMPORT', '2025-01-11', 2500.00, 250.00, 400.00)
                """, UUID.randomUUID(), supplierPin);
        jdbcTemplate.update("""
                INSERT INTO withholding_certificates (
                    id, certificate_number, payer_pin, payee_pin, certificate_date, gross_amount, withheld_amount
                )
                VALUES (?, 'WHT-P5-001', ?, ?, '2025-01-12', 500.00, 25.00)
                """, UUID.randomUUID(), supplierPin, buyerPin);
        jdbcTemplate.update("""
                INSERT INTO properties (
                    id, property_reference, owner_pin, county, valuation_amount, estimated_monthly_rent
                )
                VALUES (?, 'PROP-P5-001', ?, 'Nairobi', 100000.00, 2000.00)
                """, UUID.randomUUID(), supplierPin);
        jdbcTemplate.update("""
                INSERT INTO payment_transactions (
                    id, transaction_reference, payer_pin, collecting_agency, revenue_channel,
                    payment_date, amount, payment_status
                )
                VALUES (?, 'PAY-P5-001', ?, 'KRA', 'MPESA', '2025-01-13T10:00:00Z', 800.00, 'PAID')
                """, UUID.randomUUID(), supplierPin);
        jdbcTemplate.update("""
                INSERT INTO tax_returns (
                    id, taxpayer_id, tax_head, period_start, period_end, return_reference,
                    declared_sales, filing_status
                )
                VALUES (?, ?, 'VAT', '2025-01-01', '2025-01-31', 'RET-P5-001', 900.00, 'FILED')
                """, UUID.randomUUID(), supplier);

        assertThat(supplier).isNotEqualTo(buyer);
    }

    private void assertLinked(String table, String column, UUID taxpayerId) {
        Integer linked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                taxpayerId
        );
        assertThat(linked).isEqualTo(1);
    }
}
