package com.nyle.kra.revenue.taxgap;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
public class TaxGapService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TaxGapCalculationCatalog catalog;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public TaxGapService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ObjectMapper objectMapper,
            TaxGapCalculationCatalog catalog,
            AppUserRepository appUserRepository,
            AuditService auditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TaxGapExecutionSummary run(AuthenticatedUser authenticatedUser, HttpServletRequest request) {
        int sourceSignals = sourceSignals();
        int estimates = upsertEstimates();
        jdbcTemplate.update("DELETE FROM risk_scores WHERE main_factors ->> 'source' = 'TAX_GAP_ENGINE'");
        int scores = insertRiskScores();
        TaxGapExecutionSummary summary = new TaxGapExecutionSummary(estimates, scores, sourceSignals);
        auditService.record(
                AuditService.TAX_GAP_ENGINE_RUN,
                actor(authenticatedUser),
                "tax_gap_engine",
                null,
                request,
                Map.of(
                        "estimatesTouched", summary.estimatesTouched(),
                        "taxpayerScoresCreated", summary.taxpayerScoresCreated(),
                        "sourceSignalsUsed", summary.sourceSignalsUsed()
                )
        );
        return summary;
    }

    public List<TaxGapEstimateResponse> estimates(
            String taxHead,
            UUID taxpayerId,
            java.time.LocalDate periodStart,
            java.time.LocalDate periodEnd,
            int limit
    ) {
        return namedJdbcTemplate.query("""
                SELECT e.id, e.taxpayer_id, t.kra_pin, t.legal_name, e.tax_head,
                       e.period_start, e.period_end, e.declared_amount, e.observed_amount,
                       e.estimated_gap, e.estimated_recoverable_tax, e.estimated_penalty,
                       e.estimated_interest, e.estimated_total_due, e.confidence_score,
                       e.evidence::text AS evidence, e.created_at
                FROM tax_gap_estimates e
                JOIN taxpayers t ON t.id = e.taxpayer_id
                WHERE (CAST(:taxHead AS text) IS NULL OR e.tax_head = CAST(:taxHead AS text))
                  AND (CAST(:taxpayerId AS uuid) IS NULL OR e.taxpayer_id = CAST(:taxpayerId AS uuid))
                  AND (CAST(:periodStart AS date) IS NULL OR e.period_start >= CAST(:periodStart AS date))
                  AND (CAST(:periodEnd AS date) IS NULL OR e.period_end <= CAST(:periodEnd AS date))
                ORDER BY e.estimated_recoverable_tax DESC, e.confidence_score DESC, e.tax_head ASC
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("taxHead", blankToNull(taxHead))
                        .addValue("taxpayerId", taxpayerId)
                        .addValue("periodStart", periodStart)
                        .addValue("periodEnd", periodEnd)
                        .addValue("limit", Math.max(1, Math.min(limit, 200))),
                this::estimate);
    }

    public List<TaxGapSummaryResponse> summary() {
        return jdbcTemplate.query("""
                SELECT tax_head,
                       count(DISTINCT taxpayer_id) AS taxpayer_count,
                       count(*) AS estimate_count,
                       coalesce(sum(estimated_gap), 0) AS estimated_gap,
                       coalesce(sum(estimated_recoverable_tax), 0) AS estimated_recoverable_tax,
                       coalesce(sum(estimated_total_due), 0) AS estimated_total_due,
                       coalesce(round(avg(confidence_score), 2), 0) AS average_confidence
                FROM tax_gap_estimates
                GROUP BY tax_head
                ORDER BY estimated_recoverable_tax DESC, tax_head ASC
                """, (rs, rowNum) -> new TaxGapSummaryResponse(
                rs.getString("tax_head"),
                rs.getInt("taxpayer_count"),
                rs.getInt("estimate_count"),
                rs.getBigDecimal("estimated_gap"),
                rs.getBigDecimal("estimated_recoverable_tax"),
                rs.getBigDecimal("estimated_total_due"),
                rs.getBigDecimal("average_confidence")
        ));
    }

    public List<TaxpayerGapRankingResponse> ranking(int limit) {
        return namedJdbcTemplate.query("""
                SELECT rs.taxpayer_id, t.kra_pin, t.legal_name, rs.scoring_period_start,
                       rs.scoring_period_end, rs.score, rs.confidence_score,
                       rs.main_factors::text AS main_factors
                FROM risk_scores rs
                JOIN taxpayers t ON t.id = rs.taxpayer_id
                WHERE rs.main_factors ->> 'source' = 'TAX_GAP_ENGINE'
                ORDER BY (rs.main_factors ->> 'estimatedRecoverableTax')::numeric DESC,
                         rs.confidence_score DESC,
                         rs.score DESC
                LIMIT :limit
                """,
                new MapSqlParameterSource().addValue("limit", Math.max(1, Math.min(limit, 100))),
                (rs, rowNum) -> {
                    JsonNode factors = json(rs.getString("main_factors"));
                    return new TaxpayerGapRankingResponse(
                            rs.getObject("taxpayer_id", UUID.class),
                            rs.getString("kra_pin"),
                            rs.getString("legal_name"),
                            rs.getObject("scoring_period_start", java.time.LocalDate.class),
                            rs.getObject("scoring_period_end", java.time.LocalDate.class),
                            rs.getBigDecimal("score"),
                            rs.getBigDecimal("confidence_score"),
                            factors.path("estimatedGap").decimalValue(),
                            factors.path("estimatedRecoverableTax").decimalValue(),
                            factors.path("estimatedTotalDue").decimalValue(),
                            factors
                    );
                });
    }

    private int sourceSignals() {
        return namedJdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM risk_signals
                WHERE taxpayer_id IS NOT NULL
                  AND estimated_gap IS NOT NULL
                  AND estimated_gap > 0
                  AND signal_type IN (:signalTypes)
                """,
                new MapSqlParameterSource().addValue("signalTypes", signalTypes()),
                Integer.class
        );
    }

    private int upsertEstimates() {
        return namedJdbcTemplate.update("""
                WITH calculation_config AS (
                    SELECT *
                    FROM jsonb_to_recordset(CAST(:config AS jsonb)) AS c(
                        signal_type text,
                        configured_tax_head text,
                        recoverable_rate numeric,
                        penalty_rate numeric,
                        interest_rate numeric,
                        basis text
                    )
                ),
                normalized_signals AS (
                    SELECT rs.id,
                           rs.taxpayer_id,
                           coalesce(rs.tax_head, c.configured_tax_head) AS tax_head,
                           coalesce(rs.period_start, rs.created_at::date) AS period_start,
                           coalesce(rs.period_end, rs.created_at::date) AS period_end,
                           greatest(coalesce(rs.declared_amount, 0), 0) AS declared_amount,
                           greatest(coalesce(rs.observed_amount, 0), 0) AS observed_amount,
                           greatest(coalesce(rs.estimated_gap, 0), 0) AS estimated_gap,
                           rs.confidence_score,
                           rs.signal_type,
                           c.recoverable_rate,
                           c.penalty_rate,
                           c.interest_rate,
                           c.basis,
                           rs.evidence AS signal_evidence
                    FROM risk_signals rs
                    JOIN calculation_config c ON c.signal_type = rs.signal_type
                    WHERE rs.taxpayer_id IS NOT NULL
                      AND coalesce(rs.estimated_gap, 0) > 0
                      AND upper(rs.status) IN ('OPEN', 'NEW')
                ),
                grouped AS (
                    SELECT taxpayer_id,
                           tax_head,
                           period_start,
                           period_end,
                           sum(declared_amount) AS declared_amount,
                           sum(observed_amount) AS observed_amount,
                           sum(estimated_gap) AS estimated_gap,
                           sum(round(estimated_gap * recoverable_rate, 2)) AS estimated_recoverable_tax,
                           sum(round(estimated_gap * recoverable_rate * penalty_rate, 2)) AS estimated_penalty,
                           sum(round(estimated_gap * recoverable_rate * interest_rate, 2)) AS estimated_interest,
                           round(avg(confidence_score), 2) AS confidence_score,
                           jsonb_agg(id ORDER BY signal_type, id) AS signal_ids,
                           jsonb_agg(DISTINCT signal_type) AS signal_types,
                           jsonb_agg(DISTINCT basis) AS calculation_bases,
                           jsonb_agg(signal_evidence) AS source_evidence,
                           jsonb_object_agg(signal_type, jsonb_build_object(
                               'recoverableRate', recoverable_rate,
                               'penaltyRate', penalty_rate,
                               'interestRate', interest_rate,
                               'basis', basis
                           )) AS calculationConfig
                    FROM normalized_signals
                    GROUP BY taxpayer_id, tax_head, period_start, period_end
                )
                INSERT INTO tax_gap_estimates (
                    deterministic_key,
                    taxpayer_id,
                    tax_head,
                    period_start,
                    period_end,
                    declared_amount,
                    observed_amount,
                    estimated_gap,
                    estimated_recoverable_tax,
                    estimated_penalty,
                    estimated_interest,
                    estimated_total_due,
                    confidence_score,
                    evidence
                )
                SELECT tax_head || ':' || taxpayer_id || ':' || period_start || ':' || period_end,
                       taxpayer_id,
                       tax_head,
                       period_start,
                       period_end,
                       declared_amount,
                       observed_amount,
                       estimated_gap,
                       estimated_recoverable_tax,
                       estimated_penalty,
                       estimated_interest,
                       estimated_recoverable_tax + estimated_penalty + estimated_interest,
                       confidence_score,
                       jsonb_build_object(
                           'source', 'TAX_GAP_ENGINE',
                           'signalIds', signal_ids,
                           'signalTypes', signal_types,
                           'calculationBases', calculation_bases,
                           'calculationConfig', calculationConfig,
                           'sourceEvidence', source_evidence
                       )
                FROM grouped
                ON CONFLICT (deterministic_key) DO UPDATE
                SET declared_amount = EXCLUDED.declared_amount,
                    observed_amount = EXCLUDED.observed_amount,
                    estimated_gap = EXCLUDED.estimated_gap,
                    estimated_recoverable_tax = EXCLUDED.estimated_recoverable_tax,
                    estimated_penalty = EXCLUDED.estimated_penalty,
                    estimated_interest = EXCLUDED.estimated_interest,
                    estimated_total_due = EXCLUDED.estimated_total_due,
                    confidence_score = EXCLUDED.confidence_score,
                    evidence = EXCLUDED.evidence,
                    updated_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("config", configJson())
                        .addValue("signalTypes", signalTypes()));
    }

    private int insertRiskScores() {
        return jdbcTemplate.update("""
                WITH taxpayer_totals AS (
                    SELECT taxpayer_id,
                           min(period_start) AS period_start,
                           max(period_end) AS period_end,
                           sum(estimated_gap) AS estimated_gap,
                           sum(estimated_recoverable_tax) AS estimated_recoverable_tax,
                           sum(estimated_total_due) AS estimated_total_due,
                           round(avg(confidence_score), 2) AS confidence_score,
                           jsonb_agg(jsonb_build_object(
                               'taxHead', tax_head,
                               'periodStart', period_start,
                               'periodEnd', period_end,
                               'estimatedRecoverableTax', estimated_recoverable_tax,
                               'confidenceScore', confidence_score
                           ) ORDER BY estimated_recoverable_tax DESC) AS tax_heads
                    FROM tax_gap_estimates
                    GROUP BY taxpayer_id
                )
                INSERT INTO risk_scores (
                    taxpayer_id,
                    score,
                    confidence_score,
                    scoring_period_start,
                    scoring_period_end,
                    main_factors
                )
                SELECT taxpayer_id,
                       least(100.00, round((estimated_recoverable_tax / 1000000.00) * 100, 2)) AS score,
                       confidence_score,
                       period_start,
                       period_end,
                       jsonb_build_object(
                           'source', 'TAX_GAP_ENGINE',
                           'estimatedGap', estimated_gap,
                           'estimatedRecoverableTax', estimated_recoverable_tax,
                           'estimatedTotalDue', estimated_total_due,
                           'taxHeads', tax_heads
                       )
                FROM taxpayer_totals
                """);
    }

    private TaxGapEstimateResponse estimate(ResultSet rs, int rowNum) throws SQLException {
        return new TaxGapEstimateResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("taxpayer_id", UUID.class),
                rs.getString("kra_pin"),
                rs.getString("legal_name"),
                rs.getString("tax_head"),
                rs.getObject("period_start", java.time.LocalDate.class),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getBigDecimal("declared_amount"),
                rs.getBigDecimal("observed_amount"),
                rs.getBigDecimal("estimated_gap"),
                rs.getBigDecimal("estimated_recoverable_tax"),
                rs.getBigDecimal("estimated_penalty"),
                rs.getBigDecimal("estimated_interest"),
                rs.getBigDecimal("estimated_total_due"),
                rs.getBigDecimal("confidence_score"),
                json(rs.getString("evidence")),
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

    private String configJson() {
        try {
            return objectMapper.writeValueAsString(catalog.configs());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize tax gap config", ex);
        }
    }

    private List<String> signalTypes() {
        return catalog.configs().stream().map(TaxGapRuleConfig::signalType).toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Optional<AppUser> actor(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            return Optional.empty();
        }
        return appUserRepository.findById(authenticatedUser.getUserId());
    }
}
