package com.nyle.kra.revenue.graph;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraphIntelligenceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public GraphIntelligenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GraphExtractionResponse extractGraph() {
        GraphExtractionResponse response = new GraphExtractionResponse(
                extractTaxpayerRelationships(),
                extractInvoiceTrade(),
                extractWithholdingFlows(),
                extractSharedRegistrations(),
                extractPermitCountyEdges(),
                extractPaymentChannelEdges(),
                extractImportActivityEdges(),
                highRiskClusters(70).size()
        );
        return response;
    }

    public TaxpayerGraphResponse taxpayerGraph(UUID taxpayerId) {
        List<GraphEdgeResponse> edges = jdbcTemplate.query("""
                SELECT e.id, e.source_type, e.source_id,
                       COALESCE(st.legal_name, e.evidence ->> 'sourceLabel', e.source_type) AS source_label,
                       e.target_type, e.target_id,
                       COALESCE(tt.legal_name, e.evidence ->> 'targetLabel', e.target_type) AS target_label,
                       e.edge_type, e.weight, e.source, e.evidence::text AS evidence, e.created_at
                FROM graph_edges e
                LEFT JOIN taxpayers st ON e.source_type = 'TAXPAYER' AND st.id = e.source_id
                LEFT JOIN taxpayers tt ON e.target_type = 'TAXPAYER' AND tt.id = e.target_id
                WHERE (e.source_type = 'TAXPAYER' AND e.source_id = ?)
                   OR (e.target_type = 'TAXPAYER' AND e.target_id = ?)
                   OR (
                        e.source_type = 'TAXPAYER'
                        AND e.source_id IN (
                            SELECT target_id FROM graph_edges
                            WHERE source_type = 'TAXPAYER' AND source_id = ? AND target_type = 'TAXPAYER'
                            UNION
                            SELECT source_id FROM graph_edges
                            WHERE target_type = 'TAXPAYER' AND target_id = ? AND source_type = 'TAXPAYER'
                        )
                        AND e.target_type = 'TAXPAYER'
                   )
                ORDER BY e.weight DESC, e.edge_type, target_label
                LIMIT 80
                """, this::edgeRow, taxpayerId, taxpayerId, taxpayerId, taxpayerId);

        return new TaxpayerGraphResponse(
                taxpayerId,
                nodes(edges),
                edges,
                highRiskClustersForTaxpayer(taxpayerId)
        );
    }

    public List<HighRiskClusterResponse> highRiskClusters(int minimumScore) {
        return jdbcTemplate.query("""
                WITH latest_scores AS (
                    SELECT taxpayer_id, max(score) AS score
                    FROM risk_scores
                    GROUP BY taxpayer_id
                )
                SELECT e.source_id,
                       st.legal_name AS source_name,
                       e.target_id,
                       tt.legal_name AS target_name,
                       e.edge_type,
                       e.weight,
                       COALESCE(ss.score, 0) AS source_score,
                       COALESCE(ts.score, 0) AS target_score
                FROM graph_edges e
                JOIN taxpayers st ON st.id = e.source_id
                JOIN taxpayers tt ON tt.id = e.target_id
                LEFT JOIN latest_scores ss ON ss.taxpayer_id = e.source_id
                LEFT JOIN latest_scores ts ON ts.taxpayer_id = e.target_id
                WHERE e.source_type = 'TAXPAYER'
                  AND e.target_type = 'TAXPAYER'
                  AND (COALESCE(ss.score, 0) >= ? OR COALESCE(ts.score, 0) >= ?)
                ORDER BY greatest(COALESCE(ss.score, 0), COALESCE(ts.score, 0)) DESC,
                         e.weight DESC,
                         e.edge_type
                LIMIT 50
                """, this::clusterRow, minimumScore, minimumScore);
    }

    private List<HighRiskClusterResponse> highRiskClustersForTaxpayer(UUID taxpayerId) {
        return highRiskClusters(70).stream()
                .filter(cluster -> taxpayerId.equals(cluster.sourceTaxpayerId())
                        || taxpayerId.equals(cluster.targetTaxpayerId()))
                .toList();
    }

    private int extractTaxpayerRelationships() {
        return jdbcTemplate.update("""
                INSERT INTO graph_edges (
                    source_type, source_id, target_type, target_id, edge_type, weight, source, evidence, created_at
                )
                SELECT 'TAXPAYER', source_taxpayer_id, 'TAXPAYER', target_taxpayer_id,
                       relationship_type,
                       confidence_score / 100,
                       COALESCE(source, 'taxpayer_relationships'),
                       jsonb_build_object(
                           'confidenceScore', confidence_score,
                           'relatedPersonName', related_person_name,
                           'sourceTable', 'taxpayer_relationships'
                       ),
                       now()
                FROM taxpayer_relationships
                WHERE target_taxpayer_id IS NOT NULL
                ON CONFLICT (source_type, source_id, target_type, target_id, edge_type, source)
                DO UPDATE SET weight = EXCLUDED.weight,
                              evidence = EXCLUDED.evidence,
                              created_at = now()
                """);
    }

    private int extractInvoiceTrade() {
        return jdbcTemplate.update("""
                INSERT INTO graph_edges (
                    source_type, source_id, target_type, target_id, edge_type, weight, source, evidence, created_at
                )
                SELECT 'TAXPAYER', supplier_taxpayer_id, 'TAXPAYER', buyer_taxpayer_id,
                       'INVOICE_TRADE',
                       LEAST(count(*)::numeric, 9999.9999),
                       'ETIMS_INVOICES',
                       jsonb_build_object(
                           'invoiceCount', count(*),
                           'totalAmount', COALESCE(sum(total_amount), 0),
                           'latestInvoiceDate', max(invoice_date),
                           'sourceTable', 'invoices'
                       ),
                       now()
                FROM invoices
                WHERE supplier_taxpayer_id IS NOT NULL
                  AND buyer_taxpayer_id IS NOT NULL
                  AND supplier_taxpayer_id <> buyer_taxpayer_id
                GROUP BY supplier_taxpayer_id, buyer_taxpayer_id
                ON CONFLICT (source_type, source_id, target_type, target_id, edge_type, source)
                DO UPDATE SET weight = EXCLUDED.weight,
                              evidence = EXCLUDED.evidence,
                              created_at = now()
                """);
    }

    private int extractWithholdingFlows() {
        return jdbcTemplate.update("""
                INSERT INTO graph_edges (
                    source_type, source_id, target_type, target_id, edge_type, weight, source, evidence, created_at
                )
                SELECT 'TAXPAYER', payer_taxpayer_id, 'TAXPAYER', payee_taxpayer_id,
                       'WITHHOLDING_FLOW',
                       LEAST(count(*)::numeric, 9999.9999),
                       'WITHHOLDING_CERTIFICATES',
                       jsonb_build_object(
                           'certificateCount', count(*),
                           'grossAmount', COALESCE(sum(gross_amount), 0),
                           'withheldAmount', COALESCE(sum(withheld_amount), 0),
                           'sourceTable', 'withholding_certificates'
                       ),
                       now()
                FROM withholding_certificates
                WHERE payer_taxpayer_id IS NOT NULL
                  AND payee_taxpayer_id IS NOT NULL
                  AND payer_taxpayer_id <> payee_taxpayer_id
                GROUP BY payer_taxpayer_id, payee_taxpayer_id
                ON CONFLICT (source_type, source_id, target_type, target_id, edge_type, source)
                DO UPDATE SET weight = EXCLUDED.weight,
                              evidence = EXCLUDED.evidence,
                              created_at = now()
                """);
    }

    private int extractSharedRegistrations() {
        return jdbcTemplate.update("""
                INSERT INTO graph_edges (
                    source_type, source_id, target_type, target_id, edge_type, weight, source, evidence, created_at
                )
                SELECT 'TAXPAYER', least(a.id, b.id), 'TAXPAYER', greatest(a.id, b.id),
                       'SHARED_REGISTRATION_NUMBER',
                       1,
                       'TAXPAYER_REGISTRY',
                       jsonb_build_object(
                           'registrationNumber', a.registration_number,
                           'sourceTable', 'taxpayers'
                       ),
                       now()
                FROM taxpayers a
                JOIN taxpayers b
                  ON a.registration_number = b.registration_number
                 AND a.id < b.id
                WHERE a.registration_number IS NOT NULL
                  AND b.registration_number IS NOT NULL
                ON CONFLICT (source_type, source_id, target_type, target_id, edge_type, source)
                DO UPDATE SET weight = EXCLUDED.weight,
                              evidence = EXCLUDED.evidence,
                              created_at = now()
                """);
    }

    private int extractPermitCountyEdges() {
        return jdbcTemplate.update("""
                INSERT INTO graph_edges (
                    source_type, source_id, target_type, target_id, edge_type, weight, source, evidence, created_at
                )
                SELECT 'TAXPAYER', taxpayer_id, 'PERMIT_COUNTY', stable_uuid('PERMIT_COUNTY:' || COALESCE(county, 'UNKNOWN')),
                       'COUNTY_PERMIT_ACTIVITY',
                       LEAST(count(*)::numeric, 9999.9999),
                       'BUSINESS_PERMITS',
                       jsonb_build_object(
                           'targetLabel', COALESCE(county, 'Unknown county') || ' permits',
                           'permitCount', count(*),
                           'activePermitCount', count(*) FILTER (WHERE upper(permit_status) = 'ACTIVE'),
                           'activities', jsonb_agg(DISTINCT business_activity) FILTER (WHERE business_activity IS NOT NULL),
                           'sourceTable', 'business_permits'
                       ),
                       now()
                FROM business_permits
                WHERE taxpayer_id IS NOT NULL
                GROUP BY taxpayer_id, county
                ON CONFLICT (source_type, source_id, target_type, target_id, edge_type, source)
                DO UPDATE SET weight = EXCLUDED.weight,
                              evidence = EXCLUDED.evidence,
                              created_at = now()
                """);
    }

    private int extractPaymentChannelEdges() {
        return jdbcTemplate.update("""
                INSERT INTO graph_edges (
                    source_type, source_id, target_type, target_id, edge_type, weight, source, evidence, created_at
                )
                SELECT 'TAXPAYER', payer_taxpayer_id, 'PAYMENT_CHANNEL',
                       stable_uuid('PAYMENT_CHANNEL:' || collecting_agency || ':' || revenue_channel),
                       'PAYMENT_CHANNEL_USAGE',
                       LEAST(count(*)::numeric, 9999.9999),
                       'PAYMENT_TRANSACTIONS',
                       jsonb_build_object(
                           'targetLabel', collecting_agency || ' / ' || revenue_channel,
                           'transactionCount', count(*),
                           'totalAmount', COALESCE(sum(amount), 0),
                           'sourceTable', 'payment_transactions'
                       ),
                       now()
                FROM payment_transactions
                WHERE payer_taxpayer_id IS NOT NULL
                GROUP BY payer_taxpayer_id, collecting_agency, revenue_channel
                ON CONFLICT (source_type, source_id, target_type, target_id, edge_type, source)
                DO UPDATE SET weight = EXCLUDED.weight,
                              evidence = EXCLUDED.evidence,
                              created_at = now()
                """);
    }

    private int extractImportActivityEdges() {
        return jdbcTemplate.update("""
                INSERT INTO graph_edges (
                    source_type, source_id, target_type, target_id, edge_type, weight, source, evidence, created_at
                )
                SELECT 'TAXPAYER', taxpayer_id, 'IMPORT_ACTIVITY',
                       stable_uuid('IMPORT_ACTIVITY:' || COALESCE(hs_code, 'UNCLASSIFIED')),
                       'IMPORT_ACTIVITY',
                       LEAST(count(*)::numeric, 9999.9999),
                       'CUSTOMS_DECLARATIONS',
                       jsonb_build_object(
                           'targetLabel', 'HS ' || COALESCE(hs_code, 'unclassified'),
                           'declarationCount', count(*),
                           'landedCost', COALESCE(sum(total_landed_cost), 0),
                           'sourceTable', 'customs_declarations'
                       ),
                       now()
                FROM customs_declarations
                WHERE taxpayer_id IS NOT NULL
                GROUP BY taxpayer_id, hs_code
                ON CONFLICT (source_type, source_id, target_type, target_id, edge_type, source)
                DO UPDATE SET weight = EXCLUDED.weight,
                              evidence = EXCLUDED.evidence,
                              created_at = now()
                """);
    }

    private List<GraphNodeResponse> nodes(List<GraphEdgeResponse> edges) {
        Map<String, GraphNodeResponse> nodes = new LinkedHashMap<>();
        for (GraphEdgeResponse edge : edges) {
            nodes.putIfAbsent(key(edge.sourceType(), edge.sourceId()), node(edge.sourceType(), edge.sourceId(), edge.sourceLabel()));
            nodes.putIfAbsent(key(edge.targetType(), edge.targetId()), node(edge.targetType(), edge.targetId(), edge.targetLabel()));
        }
        return nodes.values().stream()
                .sorted(Comparator.comparing(GraphNodeResponse::nodeType).thenComparing(GraphNodeResponse::label))
                .toList();
    }

    private GraphNodeResponse node(String nodeType, UUID nodeId, String label) {
        BigDecimal riskScore = "TAXPAYER".equals(nodeType)
                ? jdbcTemplate.queryForObject(
                        "SELECT COALESCE(max(score), 0) FROM risk_scores WHERE taxpayer_id = ?",
                        BigDecimal.class,
                        nodeId
                )
                : BigDecimal.ZERO;
        return new GraphNodeResponse(nodeId, nodeType, label, riskScore);
    }

    private GraphEdgeResponse edgeRow(ResultSet rs, int rowNum) throws SQLException {
        return new GraphEdgeResponse(
                rs.getObject("id", UUID.class),
                rs.getString("source_type"),
                rs.getObject("source_id", UUID.class),
                rs.getString("source_label"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                rs.getString("target_label"),
                rs.getString("edge_type"),
                rs.getBigDecimal("weight"),
                rs.getString("source"),
                jsonMap(rs.getString("evidence")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private HighRiskClusterResponse clusterRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal sourceScore = rs.getBigDecimal("source_score");
        BigDecimal targetScore = rs.getBigDecimal("target_score");
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (sourceScore.compareTo(BigDecimal.ZERO) > 0) {
            reasons.add("Source risk score " + sourceScore);
        }
        if (targetScore.compareTo(BigDecimal.ZERO) > 0) {
            reasons.add("Target risk score " + targetScore);
        }
        reasons.add(rs.getString("edge_type"));
        UUID sourceId = rs.getObject("source_id", UUID.class);
        UUID targetId = rs.getObject("target_id", UUID.class);
        return new HighRiskClusterResponse(
                sourceId + ":" + targetId + ":" + rs.getString("edge_type"),
                sourceId,
                rs.getString("source_name"),
                targetId,
                rs.getString("target_name"),
                rs.getString("edge_type"),
                rs.getBigDecimal("weight"),
                sourceScore,
                targetScore,
                new ArrayList<>(reasons)
        );
    }

    private Map<String, Object> jsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid graph evidence JSON", ex);
        }
    }

    private String key(String type, UUID id) {
        return type + ":" + id;
    }
}
