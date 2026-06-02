package com.nyle.kra.revenue.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class GraphIntelligenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDomainRecords() {
        jdbcTemplate.update("DELETE FROM graph_edges");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM recovery_records");
        jdbcTemplate.update("DELETE FROM evidence_packs");
        jdbcTemplate.update("DELETE FROM case_events");
        jdbcTemplate.update("DELETE FROM cases");
        jdbcTemplate.update("DELETE FROM model_predictions");
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
        jdbcTemplate.update("DELETE FROM taxpayers");
    }

    @Test
    void graphExtractionCreatesExpectedEdgesAndControlsDuplicates() throws Exception {
        String token = login();
        UUID source = taxpayer("P930000001A", "BN-GRAPH-001", "Graph Supplier Ltd", "Nairobi");
        UUID target = taxpayer("P930000002B", "BN-GRAPH-002", "Graph Buyer Ltd", "Nairobi");
        seedGraphRecords(source, target);

        JsonNode first = runGraphExtraction(token);

        assertThat(first.get("invoiceTradeEdges").asInt()).isEqualTo(1);
        assertThat(first.get("withholdingFlowEdges").asInt()).isEqualTo(1);
        assertThat(first.get("permitEdges").asInt()).isEqualTo(1);
        assertThat(first.get("paymentChannelEdges").asInt()).isEqualTo(1);
        assertThat(first.get("importActivityEdges").asInt()).isEqualTo(1);
        assertThat(edgeCount()).isGreaterThanOrEqualTo(5);

        int afterFirstRun = edgeCount();
        JsonNode second = runGraphExtraction(token);

        assertThat(second.get("invoiceTradeEdges").asInt()).isEqualTo(1);
        assertThat(edgeCount()).isEqualTo(afterFirstRun);
    }

    @Test
    void taxpayerGraphViewLoadsEdgesAndHighRiskClusters() throws Exception {
        String token = login();
        UUID source = taxpayer("P930000003C", "BN-GRAPH-003", "Cluster Supplier Ltd", "Nairobi");
        UUID target = taxpayer("P930000004D", "BN-GRAPH-004", "Cluster Buyer Ltd", "Nairobi");
        seedGraphRecords(source, target);
        riskScore(source, "84.00");
        riskScore(target, "72.00");
        runGraphExtraction(token);

        mockMvc.perform(get("/api/taxpayers/{id}/graph", source)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxpayerId").value(source.toString()))
                .andExpect(jsonPath("$.nodes[?(@.nodeType == 'TAXPAYER')]").exists())
                .andExpect(jsonPath("$.edges[?(@.edgeType == 'INVOICE_TRADE')]").exists())
                .andExpect(jsonPath("$.edges[?(@.edgeType == 'PAYMENT_CHANNEL_USAGE')]").exists())
                .andExpect(jsonPath("$.highRiskClusters[0].edgeType").value("INVOICE_TRADE"));

        mockMvc.perform(get("/api/graph/clusters")
                        .header("Authorization", "Bearer " + token)
                        .param("minimumScore", "70"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceRiskScore").value(84.00))
                .andExpect(jsonPath("$[0].targetRiskScore").value(72.00));
    }

    @Test
    void graphEndpointsRequireAuthentication() throws Exception {
        UUID source = taxpayer("P930000005E", "BN-GRAPH-005", "Protected Graph Ltd", "Nairobi");

        mockMvc.perform(get("/api/taxpayers/{id}/graph", source))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/graph/jobs"))
                .andExpect(status().isForbidden());
    }

    private JsonNode runGraphExtraction(String token) throws Exception {
        String response = mockMvc.perform(post("/api/graph/jobs")
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

    private UUID taxpayer(String pin, String registrationNumber, String legalName, String county) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO taxpayers (
                    id, kra_pin, taxpayer_type, legal_name, registration_number, county, status
                )
                VALUES (?, ?, 'COMPANY', ?, ?, ?, 'ACTIVE')
                """, id, pin, legalName, registrationNumber, county);
        return id;
    }

    private void seedGraphRecords(UUID source, UUID target) {
        jdbcTemplate.update("""
                INSERT INTO invoices (
                    id, invoice_number, supplier_taxpayer_id, buyer_taxpayer_id, supplier_pin, buyer_pin,
                    invoice_date, invoice_type, invoice_status, taxable_amount, tax_amount, total_amount, currency
                )
                VALUES (?, 'INV-GRAPH-001', ?, ?, 'P930000001A', 'P930000002B',
                        '2025-02-10', 'SALE', 'VALID', 250000.00, 40000.00, 290000.00, 'KES')
                """, UUID.randomUUID(), source, target);
        jdbcTemplate.update("""
                INSERT INTO withholding_certificates (
                    id, certificate_number, payer_taxpayer_id, payee_taxpayer_id,
                    payer_pin, payee_pin, certificate_date, gross_amount, withheld_amount
                )
                VALUES (?, 'WHT-GRAPH-001', ?, ?, 'P930000002B', 'P930000001A',
                        '2025-02-12', 90000.00, 4500.00)
                """, UUID.randomUUID(), target, source);
        jdbcTemplate.update("""
                INSERT INTO business_permits (
                    id, taxpayer_id, permit_number, county, business_activity, permit_status
                )
                VALUES (?, ?, 'BP-GRAPH-001', 'Nairobi', 'Wholesale distribution', 'ACTIVE')
                """, UUID.randomUUID(), source);
        jdbcTemplate.update("""
                INSERT INTO payment_transactions (
                    id, transaction_reference, payer_taxpayer_id, payer_pin, collecting_agency,
                    revenue_channel, payment_date, amount, payment_status
                )
                VALUES (?, 'PAY-GRAPH-001', ?, 'P930000001A', 'KRA', 'MPESA',
                        '2025-02-15T10:00:00Z', 75000.00, 'PAID')
                """, UUID.randomUUID(), source);
        jdbcTemplate.update("""
                INSERT INTO customs_declarations (
                    id, taxpayer_id, importer_pin, declaration_number, declaration_type,
                    declaration_date, hs_code, customs_value, duty_amount, vat_amount, total_landed_cost
                )
                VALUES (?, ?, 'P930000001A', 'CUS-GRAPH-001', 'IMPORT',
                        '2025-02-17', '8471', 500000.00, 50000.00, 80000.00, 630000.00)
                """, UUID.randomUUID(), source);
    }

    private void riskScore(UUID taxpayerId, String score) {
        jdbcTemplate.update("""
                INSERT INTO risk_scores (
                    id, taxpayer_id, score, confidence_score, main_factors
                )
                VALUES (?, ?, ?, 90.00, '{"source":"GRAPH_TEST"}'::jsonb)
                """, UUID.randomUUID(), taxpayerId, new java.math.BigDecimal(score));
    }

    private int edgeCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM graph_edges", Integer.class);
        return count == null ? 0 : count;
    }
}
