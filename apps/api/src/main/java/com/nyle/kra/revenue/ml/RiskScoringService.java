package com.nyle.kra.revenue.ml;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyle.kra.revenue.audit.AuditService;
import com.nyle.kra.revenue.identity.AppUser;
import com.nyle.kra.revenue.identity.AppUserRepository;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskScoringService {

    private static final String PREDICTION_TYPE = "TAXPAYER_ANOMALY";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AnalyticsRiskScoringClient analyticsRiskScoringClient;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public RiskScoringService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ObjectMapper objectMapper,
            AnalyticsRiskScoringClient analyticsRiskScoringClient,
            AppUserRepository appUserRepository,
            AuditService auditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
        this.analyticsRiskScoringClient = analyticsRiskScoringClient;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @Transactional
    public RiskScoringJobResponse run(AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        List<TaxpayerRiskFeature> features = loadFeatures();
        AnalyticsRiskScoringResponse modelRun = analyticsRiskScoringClient.trainAndScore(features);
        List<RiskScoringPrediction> predictions = modelRun.predictions().stream()
                .map(this::prediction)
                .toList();
        UUID modelVersionId = upsertModelVersion(modelRun, features.size());

        jdbcTemplate.update(
                "DELETE FROM model_predictions WHERE model_version_id = ? AND prediction_type = ?",
                modelVersionId,
                PREDICTION_TYPE
        );
        jdbcTemplate.update("DELETE FROM risk_scores WHERE main_factors ->> 'source' = 'AI_RISK_SCORING'");

        int predictionCount = insertPredictions(modelVersionId, predictions);
        int scoreCount = insertCombinedScores(modelVersionId, predictions);
        auditService.record(
                "AI_RISK_SCORING_RUN",
                actor(authenticatedUser),
                "model_version",
                modelVersionId,
                request,
                auditDetails(modelRun, features.size(), predictionCount, scoreCount)
        );
        return new RiskScoringJobResponse(
                modelVersionId,
                modelRun.modelName(),
                modelRun.modelVersion(),
                features.size(),
                predictionCount,
                scoreCount
        );
    }

    private Map<String, Object> auditDetails(
            AnalyticsRiskScoringResponse modelRun,
            int taxpayersScored,
            int predictionCount,
            int scoreCount
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("taxpayersScored", taxpayersScored);
        details.put("predictionsCreated", predictionCount);
        details.put("combinedScoresCreated", scoreCount);
        details.put("modelName", modelRun.modelName());
        details.put("modelVersion", modelRun.modelVersion());
        details.put("algorithm", modelRun.algorithm());
        details.put("mlflowRunId", modelRun.mlflowRunId());
        return details;
    }

    public List<ModelPredictionResponse> predictions(UUID taxpayerId, int limit) {
        return namedJdbcTemplate.query("""
                SELECT p.id, p.model_version_id, p.taxpayer_id, t.kra_pin, t.legal_name,
                       p.prediction_type, p.score, p.explanation::text AS explanation, p.created_at
                FROM model_predictions p
                JOIN taxpayers t ON t.id = p.taxpayer_id
                JOIN model_versions mv ON mv.id = p.model_version_id
                WHERE p.prediction_type = :predictionType
                  AND mv.model_name = :modelName
                  AND (CAST(:taxpayerId AS uuid) IS NULL OR p.taxpayer_id = CAST(:taxpayerId AS uuid))
                ORDER BY p.score DESC, p.created_at DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("predictionType", PREDICTION_TYPE)
                        .addValue("modelName", RiskScoringConstants.MODEL_NAME)
                        .addValue("taxpayerId", taxpayerId)
                        .addValue("limit", Math.max(1, Math.min(limit, 100))),
                this::predictionRow);
    }

    public List<ModelVersionResponse> modelVersions() {
        return jdbcTemplate.query("""
                SELECT id, model_name, version, model_type, training_data_summary,
                       metrics::text AS metrics, active, created_at
                FROM model_versions
                WHERE model_name = ?
                ORDER BY active DESC, created_at DESC
                """, this::modelVersionRow, RiskScoringConstants.MODEL_NAME);
    }

    public RiskScoringDashboardResponse dashboard(int limit) {
        String activeVersion = jdbcTemplate.queryForObject("""
                SELECT coalesce(max(version), 'none')
                FROM model_versions
                WHERE model_name = ? AND active = true
                """, String.class, RiskScoringConstants.MODEL_NAME);
        Integer predictionCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM model_predictions p
                JOIN model_versions mv ON mv.id = p.model_version_id
                WHERE mv.model_name = ?
                """, Integer.class, RiskScoringConstants.MODEL_NAME);
        Integer highRiskCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM risk_scores
                WHERE main_factors ->> 'source' = 'AI_RISK_SCORING'
                  AND score >= 70
                """, Integer.class);
        java.math.BigDecimal averageModelScore = jdbcTemplate.queryForObject("""
                SELECT coalesce(round(avg(p.score), 2), 0)
                FROM model_predictions p
                JOIN model_versions mv ON mv.id = p.model_version_id
                WHERE mv.model_name = ?
                """, java.math.BigDecimal.class, RiskScoringConstants.MODEL_NAME);
        java.math.BigDecimal averageCombinedScore = jdbcTemplate.queryForObject("""
                SELECT coalesce(round(avg(score), 2), 0)
                FROM risk_scores
                WHERE main_factors ->> 'source' = 'AI_RISK_SCORING'
                """, java.math.BigDecimal.class);
        return new RiskScoringDashboardResponse(
                activeVersion,
                predictionCount == null ? 0 : predictionCount,
                highRiskCount == null ? 0 : highRiskCount,
                averageModelScore,
                averageCombinedScore,
                predictions(null, limit)
        );
    }

    private UUID upsertModelVersion(AnalyticsRiskScoringResponse modelRun, int taxpayerCount) {
        jdbcTemplate.update("""
                UPDATE model_versions
                SET active = false
                WHERE model_name = ?
                """, RiskScoringConstants.MODEL_NAME);
        return jdbcTemplate.queryForObject("""
                INSERT INTO model_versions (
                    model_name, version, model_type, training_data_summary, metrics, active
                )
                VALUES (?, ?, ?,
                        ?, CAST(? AS jsonb), true)
                ON CONFLICT (model_name, version) DO UPDATE
                SET training_data_summary = EXCLUDED.training_data_summary,
                    metrics = EXCLUDED.metrics,
                    active = true
                RETURNING id
                """,
                UUID.class,
                modelRun.modelName(),
                modelRun.modelVersion(),
                modelRun.modelType(),
                modelRun.trainingDataSummary(),
                jsonString(metrics(modelRun, taxpayerCount)));
    }

    private Map<String, Object> metrics(AnalyticsRiskScoringResponse modelRun, int taxpayerCount) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.putAll(modelRun.metrics() == null ? Map.of() : modelRun.metrics());
        metrics.put("algorithm", modelRun.algorithm());
        metrics.put("mlflowRunId", modelRun.mlflowRunId());
        metrics.put("modelType", modelRun.modelType());
        metrics.put("reproducibilitySeed", modelRun.reproducibilitySeed());
        metrics.put("taxpayersTrained", taxpayerCount);
        metrics.put("officerReviewRequired", true);
        return metrics;
    }

    private RiskScoringPrediction prediction(AnalyticsRiskScoringResponse.Prediction prediction) {
        return new RiskScoringPrediction(
                UUID.fromString(prediction.taxpayerId()),
                prediction.modelScore(),
                prediction.combinedScore(),
                prediction.confidenceScore(),
                prediction.mainContributingFeatures(),
                prediction.explanation()
        );
    }

    private List<TaxpayerRiskFeature> loadFeatures() {
        return jdbcTemplate.query("""
                WITH return_features AS (
                    SELECT taxpayer_id,
                           min(period_start) AS period_start,
                           max(period_end) AS period_end,
                           coalesce(sum(declared_sales), 0) AS declared_sales,
                           coalesce(sum(declared_income), 0) AS declared_income,
                           count(*) AS return_count
                    FROM tax_returns
                    GROUP BY taxpayer_id
                ),
                invoice_features AS (
                    SELECT supplier_taxpayer_id AS taxpayer_id,
                           coalesce(sum(total_amount), 0) AS invoice_sales,
                           count(*) AS invoice_count
                    FROM invoices
                    WHERE supplier_taxpayer_id IS NOT NULL
                    GROUP BY supplier_taxpayer_id
                ),
                customs_features AS (
                    SELECT taxpayer_id,
                           coalesce(sum(total_landed_cost), 0) AS customs_landed_cost,
                           count(*) AS customs_count
                    FROM customs_declarations
                    WHERE taxpayer_id IS NOT NULL
                    GROUP BY taxpayer_id
                ),
                withholding_features AS (
                    SELECT payee_taxpayer_id AS taxpayer_id,
                           coalesce(sum(gross_amount), 0) AS withholding_income,
                           count(*) AS withholding_count
                    FROM withholding_certificates
                    WHERE payee_taxpayer_id IS NOT NULL
                    GROUP BY payee_taxpayer_id
                ),
                signal_features AS (
                    SELECT taxpayer_id,
                           coalesce(sum(greatest(coalesce(estimated_gap, 0), 0)), 0) AS risk_signal_gap,
                           count(*) FILTER (WHERE upper(status) IN ('OPEN', 'NEW')) AS open_signal_count
                    FROM risk_signals
                    WHERE taxpayer_id IS NOT NULL
                    GROUP BY taxpayer_id
                ),
                rule_scores AS (
                    SELECT taxpayer_id,
                           coalesce(max(score), 0) AS rule_score
                    FROM risk_scores
                    WHERE main_factors ->> 'source' IN ('TAX_GAP_ENGINE', 'RULE_ENGINE')
                    GROUP BY taxpayer_id
                )
                SELECT t.id AS taxpayer_id, t.kra_pin, t.legal_name, t.sector_name, t.county,
                       coalesce(rf.period_start, current_date - interval '30 days')::date AS period_start,
                       coalesce(rf.period_end, current_date)::date AS period_end,
                       coalesce(rf.declared_sales, 0) AS declared_sales,
                       coalesce(rf.declared_income, 0) AS declared_income,
                       coalesce(invf.invoice_sales, 0) AS invoice_sales,
                       coalesce(cf.customs_landed_cost, 0) AS customs_landed_cost,
                       coalesce(wf.withholding_income, 0) AS withholding_income,
                       coalesce(sf.risk_signal_gap, 0) AS risk_signal_gap,
                       coalesce(rs.rule_score, 0) AS rule_score,
                       coalesce(rf.return_count, 0) AS return_count,
                       coalesce(invf.invoice_count, 0) AS invoice_count,
                       coalesce(cf.customs_count, 0) AS customs_count,
                       coalesce(wf.withholding_count, 0) AS withholding_count,
                       coalesce(sf.open_signal_count, 0) AS open_signal_count
                FROM taxpayers t
                LEFT JOIN return_features rf ON rf.taxpayer_id = t.id
                LEFT JOIN invoice_features invf ON invf.taxpayer_id = t.id
                LEFT JOIN customs_features cf ON cf.taxpayer_id = t.id
                LEFT JOIN withholding_features wf ON wf.taxpayer_id = t.id
                LEFT JOIN signal_features sf ON sf.taxpayer_id = t.id
                LEFT JOIN rule_scores rs ON rs.taxpayer_id = t.id
                WHERE t.status = 'ACTIVE'
                ORDER BY t.kra_pin NULLS LAST, t.legal_name
                """, this::feature);
    }

    private int insertPredictions(UUID modelVersionId, List<RiskScoringPrediction> predictions) {
        int[][] counts = jdbcTemplate.batchUpdate("""
                INSERT INTO model_predictions (
                    model_version_id, taxpayer_id, prediction_type, score, explanation
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """, predictions, 100, (ps, prediction) -> {
                    ps.setObject(1, modelVersionId);
                    ps.setObject(2, prediction.taxpayerId());
                    ps.setString(3, PREDICTION_TYPE);
                    ps.setBigDecimal(4, prediction.modelScore());
                    ps.setString(5, jsonString(prediction.explanation()));
                });
        return sum(counts);
    }

    private int insertCombinedScores(UUID modelVersionId, List<RiskScoringPrediction> predictions) {
        int[][] counts = jdbcTemplate.batchUpdate("""
                INSERT INTO risk_scores (
                    taxpayer_id, score, confidence_score, scoring_period_start,
                    scoring_period_end, model_version_id, main_factors
                )
                VALUES (
                    ?, ?, ?, current_date - 30, current_date, ?, CAST(? AS jsonb)
                )
                """, predictions, 100, (ps, prediction) -> {
                    ps.setObject(1, prediction.taxpayerId());
                    ps.setBigDecimal(2, prediction.combinedScore());
                    ps.setBigDecimal(3, prediction.confidenceScore());
                    ps.setObject(4, modelVersionId);
                    ps.setString(5, jsonString(prediction.explanation()));
                });
        return sum(counts);
    }

    private TaxpayerRiskFeature feature(ResultSet rs, int rowNum) throws SQLException {
        return new TaxpayerRiskFeature(
                rs.getObject("taxpayer_id", UUID.class),
                rs.getString("kra_pin"),
                rs.getString("legal_name"),
                rs.getString("sector_name"),
                rs.getString("county"),
                rs.getObject("period_start", java.time.LocalDate.class),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getBigDecimal("declared_sales"),
                rs.getBigDecimal("declared_income"),
                rs.getBigDecimal("invoice_sales"),
                rs.getBigDecimal("customs_landed_cost"),
                rs.getBigDecimal("withholding_income"),
                rs.getBigDecimal("risk_signal_gap"),
                rs.getBigDecimal("rule_score"),
                rs.getInt("return_count"),
                rs.getInt("invoice_count"),
                rs.getInt("customs_count"),
                rs.getInt("withholding_count"),
                rs.getInt("open_signal_count")
        );
    }

    private ModelPredictionResponse predictionRow(ResultSet rs, int rowNum) throws SQLException {
        return new ModelPredictionResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("model_version_id", UUID.class),
                rs.getObject("taxpayer_id", UUID.class),
                rs.getString("kra_pin"),
                rs.getString("legal_name"),
                rs.getString("prediction_type"),
                rs.getBigDecimal("score"),
                json(rs.getString("explanation")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private ModelVersionResponse modelVersionRow(ResultSet rs, int rowNum) throws SQLException {
        return new ModelVersionResponse(
                rs.getObject("id", UUID.class),
                rs.getString("model_name"),
                rs.getString("version"),
                rs.getString("model_type"),
                rs.getString("training_data_summary"),
                json(rs.getString("metrics")),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid JSON from database", ex);
        }
    }

    private String jsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize risk scoring JSON", ex);
        }
    }

    private int sum(int[] counts) {
        int total = 0;
        for (int count : counts) {
            total += count;
        }
        return total;
    }

    private int sum(int[][] counts) {
        int total = 0;
        for (int[] batch : counts) {
            total += sum(batch);
        }
        return total;
    }

    private Optional<AppUser> actor(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(authenticatedUser.getUserId());
    }
}
