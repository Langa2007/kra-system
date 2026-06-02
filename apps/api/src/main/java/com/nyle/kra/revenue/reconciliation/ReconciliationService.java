package com.nyle.kra.revenue.reconciliation;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyle.kra.revenue.cases.CaseManagementService;
import com.nyle.kra.revenue.cases.CaseResponse;
import com.nyle.kra.revenue.cases.CreateCaseRequest;
import com.nyle.kra.revenue.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ReconciliationService {

    private static final String DEFAULT_ACCOUNT = "CBK-KRA-MAIN";
    private static final int DEFAULT_SETTLEMENT_DELAY_DAYS = 2;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CaseManagementService caseManagementService;

    public ReconciliationService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ObjectMapper objectMapper,
            CaseManagementService caseManagementService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
        this.caseManagementService = caseManagementService;
    }

    @Transactional
    public ReconciliationRunResponse run(ReconciliationRunRequest request) {
        LocalDate from = request.from() == null ? minPaymentDate() : request.from();
        LocalDate to = request.to() == null ? maxPaymentDate() : request.to();
        int delayDays = request.settlementDelayDays() == null
                ? DEFAULT_SETTLEMENT_DELAY_DAYS
                : Math.max(0, request.settlementDelayDays());
        String expectedAccount = blankToDefault(request.expectedSettlementAccount(), DEFAULT_ACCOUNT);

        int resultsTouched = upsertResults(from, to, delayDays, expectedAccount);
        int riskSignalsTouched = upsertRiskSignals();
        linkRiskSignals();
        Integer exceptions = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM reconciliation_results
                WHERE reconciliation_date BETWEEN ? AND ?
                  AND settlement_status <> 'MATCHED'
                """, Integer.class, from, to);

        return new ReconciliationRunResponse(from, to, resultsTouched, exceptions == null ? 0 : exceptions, riskSignalsTouched);
    }

    public List<ReconciliationResultResponse> results(String status, LocalDate from, LocalDate to, int limit) {
        return namedJdbcTemplate.query("""
                SELECT *
                FROM reconciliation_results
                WHERE (CAST(:status AS text) IS NULL OR settlement_status = CAST(:status AS text))
                  AND (CAST(:fromDate AS date) IS NULL OR reconciliation_date >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date) IS NULL OR reconciliation_date <= CAST(:toDate AS date))
                ORDER BY reconciliation_date DESC, abs(variance_amount) DESC, settlement_status
                LIMIT :limit
                """,
                new MapSqlParameterSource()
                        .addValue("status", blankToNull(status))
                        .addValue("fromDate", from)
                        .addValue("toDate", to)
                        .addValue("limit", Math.max(1, Math.min(limit, 500))),
                this::result);
    }

    public ReconciliationSummaryResponse summary(LocalDate from, LocalDate to) {
        return namedJdbcTemplate.queryForObject("""
                SELECT coalesce(sum(expected_amount), 0) AS expected_amount,
                       coalesce(sum(settled_amount), 0) AS settled_amount,
                       coalesce(sum(variance_amount), 0) AS variance_amount,
                       count(*) AS result_count,
                       count(*) FILTER (WHERE settlement_status <> 'MATCHED') AS exception_count,
                       count(*) FILTER (WHERE settlement_status = 'MISSING_SETTLEMENT') AS missing_count,
                       count(*) FILTER (WHERE settlement_status = 'DELAYED_SETTLEMENT') AS delayed_count,
                       count(*) FILTER (WHERE settlement_status = 'DUPLICATE_TRANSACTION') AS duplicate_count,
                       count(*) FILTER (WHERE settlement_status = 'WRONG_ACCOUNT') AS wrong_account_count
                FROM reconciliation_results
                WHERE (CAST(:fromDate AS date) IS NULL OR reconciliation_date >= CAST(:fromDate AS date))
                  AND (CAST(:toDate AS date) IS NULL OR reconciliation_date <= CAST(:toDate AS date))
                """,
                new MapSqlParameterSource()
                        .addValue("fromDate", from)
                        .addValue("toDate", to),
                (rs, rowNum) -> new ReconciliationSummaryResponse(
                        rs.getBigDecimal("expected_amount"),
                        rs.getBigDecimal("settled_amount"),
                        rs.getBigDecimal("variance_amount"),
                        rs.getInt("result_count"),
                        rs.getInt("exception_count"),
                        rs.getInt("missing_count"),
                        rs.getInt("delayed_count"),
                        rs.getInt("duplicate_count"),
                        rs.getInt("wrong_account_count")
                ));
    }

    @Transactional
    public CaseResponse openCase(UUID resultId, AuthenticatedUser user, HttpServletRequest request) {
        ReconciliationResultResponse result = resultById(resultId);
        if ("MATCHED".equals(result.settlementStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Matched reconciliation results do not need cases");
        }
        if (result.riskSignalId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Run reconciliation before opening a case");
        }

        List<CaseResponse> existing = namedJdbcTemplate.query("""
                SELECT c.*, t.kra_pin, t.legal_name, au.full_name AS assigned_name,
                       coalesce(r.assessed_amount, 0) AS assessed_amount,
                       coalesce(r.agreed_amount, 0) AS agreed_amount,
                       coalesce(r.collected_amount, 0) AS collected_amount
                FROM cases c
                LEFT JOIN taxpayers t ON t.id = c.taxpayer_id
                LEFT JOIN app_users au ON au.id = c.assigned_to
                LEFT JOIN LATERAL (
                    SELECT sum(coalesce(assessed_amount, 0)) AS assessed_amount,
                           sum(coalesce(agreed_amount, 0)) AS agreed_amount,
                           sum(coalesce(collected_amount, 0)) AS collected_amount
                    FROM recovery_records rr
                    WHERE rr.case_id = c.id
                ) r ON true
                WHERE c.risk_signal_id = :riskSignalId
                ORDER BY c.opened_at DESC
                LIMIT 1
                """,
                new MapSqlParameterSource().addValue("riskSignalId", result.riskSignalId()),
                (rs, rowNum) -> new CaseResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("case_number"),
                        rs.getObject("risk_signal_id", UUID.class),
                        rs.getObject("taxpayer_id", UUID.class),
                        rs.getString("kra_pin"),
                        rs.getString("legal_name"),
                        rs.getString("title"),
                        rs.getString("case_type"),
                        rs.getString("priority"),
                        rs.getString("status"),
                        rs.getBigDecimal("estimated_recoverable_amount"),
                        rs.getObject("assigned_to", UUID.class),
                        rs.getString("assigned_name"),
                        instant(rs, "opened_at"),
                        instantOrNull(rs, "closed_at"),
                        rs.getString("closure_reason"),
                        rs.getBigDecimal("assessed_amount"),
                        rs.getBigDecimal("agreed_amount"),
                        rs.getBigDecimal("collected_amount")
                ));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        return caseManagementService.create(
                new CreateCaseRequest(
                        result.riskSignalId(),
                        "Settlement exception: " + result.collectingAgency() + " / " + result.revenueChannel()
                                + " / " + result.reconciliationDate(),
                        "SETTLEMENT_RECONCILIATION",
                        priority(result),
                        null
                ),
                user,
                request
        );
    }

    private int upsertResults(LocalDate from, LocalDate to, int delayDays, String expectedAccount) {
        return namedJdbcTemplate.update("""
                WITH payment_totals AS (
                    SELECT collecting_agency,
                           revenue_channel,
                           payment_date::date AS reconciliation_date,
                           sum(amount) AS expected_amount,
                           count(*) AS transaction_count
                    FROM payment_transactions
                    WHERE upper(payment_status) IN ('PAID', 'SUCCESS', 'SUCCESSFUL', 'COMPLETED')
                      AND payment_date::date BETWEEN :fromDate AND :toDate
                    GROUP BY collecting_agency, revenue_channel, payment_date::date
                ),
                on_time_settlements AS (
                    SELECT p.collecting_agency, p.revenue_channel, p.reconciliation_date,
                           coalesce(sum(s.settled_amount), 0) AS settled_amount,
                           count(s.id) AS settlement_count,
                           string_agg(DISTINCT s.settlement_account, ', ' ORDER BY s.settlement_account) AS settlement_account,
                           count(s.id) FILTER (WHERE lower(coalesce(s.settlement_account, '')) <> lower(:expectedAccount)) AS wrong_account_count,
                           max(s.settlement_date - p.reconciliation_date) AS max_lag
                    FROM payment_totals p
                    LEFT JOIN settlement_records s
                      ON lower(s.collecting_agency) = lower(p.collecting_agency)
                     AND lower(s.revenue_channel) = lower(p.revenue_channel)
                     AND s.settlement_date >= p.reconciliation_date
                     AND s.settlement_date <= p.reconciliation_date + CAST(:delayDays AS integer)
                     AND upper(s.settlement_status) IN ('SETTLED', 'SUCCESS', 'COMPLETED')
                    GROUP BY p.collecting_agency, p.revenue_channel, p.reconciliation_date
                ),
                all_settlements AS (
                    SELECT p.collecting_agency, p.revenue_channel, p.reconciliation_date,
                           coalesce(sum(s.settled_amount), 0) AS settled_amount,
                           count(s.id) AS settlement_count,
                           string_agg(DISTINCT s.settlement_account, ', ' ORDER BY s.settlement_account) AS settlement_account,
                           max(s.settlement_date - p.reconciliation_date) AS max_lag
                    FROM payment_totals p
                    LEFT JOIN settlement_records s
                      ON lower(s.collecting_agency) = lower(p.collecting_agency)
                     AND lower(s.revenue_channel) = lower(p.revenue_channel)
                     AND s.settlement_date >= p.reconciliation_date
                     AND s.settlement_date <= p.reconciliation_date + 30
                     AND s.settled_amount = p.expected_amount
                     AND upper(s.settlement_status) IN ('SETTLED', 'SUCCESS', 'COMPLETED')
                    GROUP BY p.collecting_agency, p.revenue_channel, p.reconciliation_date
                ),
                base_results AS (
                    SELECT p.collecting_agency,
                           p.revenue_channel,
                           p.reconciliation_date,
                           p.expected_amount,
                           CASE
                               WHEN ot.settlement_count = 0 AND als.settled_amount >= p.expected_amount THEN als.settled_amount
                               ELSE ot.settled_amount
                           END AS settled_amount,
                           p.transaction_count,
                           CASE
                               WHEN ot.settlement_count = 0 AND als.settled_amount >= p.expected_amount THEN als.settlement_count
                               ELSE ot.settlement_count
                           END AS settlement_count,
                           CASE
                               WHEN ot.settlement_count = 0 AND als.settled_amount >= p.expected_amount THEN als.settlement_account
                               ELSE ot.settlement_account
                           END AS settlement_account,
                           CASE
                               WHEN ot.settlement_count = 0 AND als.settled_amount >= p.expected_amount THEN als.max_lag
                               ELSE ot.max_lag
                           END AS max_lag,
                           CASE
                               WHEN ot.settlement_count > 0 AND ot.settled_amount < p.expected_amount THEN 'PARTIAL_SETTLEMENT'
                               WHEN als.settlement_count = 0 THEN 'MISSING_SETTLEMENT'
                               WHEN ot.settlement_count = 0 AND als.settled_amount >= p.expected_amount THEN 'DELAYED_SETTLEMENT'
                               WHEN ot.wrong_account_count > 0 THEN 'WRONG_ACCOUNT'
                               ELSE 'MATCHED'
                           END AS settlement_status
                    FROM payment_totals p
                    JOIN on_time_settlements ot
                      ON ot.collecting_agency = p.collecting_agency
                     AND ot.revenue_channel = p.revenue_channel
                     AND ot.reconciliation_date = p.reconciliation_date
                    JOIN all_settlements als
                      ON als.collecting_agency = p.collecting_agency
                     AND als.revenue_channel = p.revenue_channel
                     AND als.reconciliation_date = p.reconciliation_date
                ),
                duplicate_groups AS (
                    SELECT collecting_agency,
                           revenue_channel,
                           payment_date::date AS reconciliation_date,
                           coalesce(provider_reference, transaction_reference) AS duplicate_reference,
                           count(*) AS duplicate_count,
                           sum(amount) AS duplicate_amount,
                           sum(amount) - max(amount) AS variance_amount
                    FROM payment_transactions
                    WHERE upper(payment_status) IN ('PAID', 'SUCCESS', 'SUCCESSFUL', 'COMPLETED')
                      AND payment_date::date BETWEEN :fromDate AND :toDate
                    GROUP BY collecting_agency, revenue_channel, payment_date::date,
                             coalesce(provider_reference, transaction_reference)
                    HAVING count(*) > 1
                ),
                duplicate_results AS (
                    SELECT collecting_agency,
                           revenue_channel,
                           reconciliation_date,
                           duplicate_amount AS expected_amount,
                           0::numeric AS settled_amount,
                           variance_amount,
                           duplicate_count AS transaction_count,
                           0 AS settlement_count,
                           NULL::text AS settlement_account,
                           NULL::integer AS max_lag,
                           'DUPLICATE_TRANSACTION' AS settlement_status,
                           jsonb_build_object(
                               'exceptionType', 'DUPLICATE_TRANSACTION',
                               'duplicateReference', duplicate_reference,
                               'duplicateCount', duplicate_count,
                               'duplicateAmount', duplicate_amount,
                               'varianceAmount', variance_amount
                           ) AS evidence
                    FROM duplicate_groups
                ),
                classified AS (
                    SELECT collecting_agency,
                           revenue_channel,
                           reconciliation_date,
                           expected_amount,
                           settled_amount,
                           expected_amount - settled_amount AS variance_amount,
                           transaction_count,
                           settlement_count,
                           settlement_account,
                           max_lag,
                           settlement_status,
                           jsonb_build_object(
                               'exceptionType', settlement_status,
                               'collectingAgency', collecting_agency,
                               'revenueChannel', revenue_channel,
                               'reconciliationDate', reconciliation_date,
                               'expectedAmount', expected_amount,
                               'settledAmount', settled_amount,
                               'varianceAmount', expected_amount - settled_amount,
                               'settlementDelayDays', :delayDays,
                               'expectedSettlementAccount', :expectedAccount,
                               'settlementAccount', settlement_account,
                               'maxSettlementLagDays', max_lag
                           ) AS evidence
                    FROM base_results
                    UNION ALL
                    SELECT collecting_agency,
                           revenue_channel,
                           reconciliation_date,
                           expected_amount,
                           settled_amount,
                           variance_amount,
                           transaction_count,
                           settlement_count,
                           settlement_account,
                           max_lag,
                           settlement_status,
                           evidence
                    FROM duplicate_results
                )
                INSERT INTO reconciliation_results (
                    deterministic_key, reconciliation_date, collecting_agency, revenue_channel,
                    expected_amount, settled_amount, variance_amount, transaction_count,
                    settlement_count, settlement_status, expected_settlement_account,
                    settlement_account, max_settlement_lag_days, evidence
                )
                SELECT 'recon:' || lower(collecting_agency) || ':' || lower(revenue_channel) || ':'
                           || reconciliation_date || ':' || settlement_status,
                       reconciliation_date,
                       collecting_agency,
                       revenue_channel,
                       expected_amount,
                       settled_amount,
                       variance_amount,
                       transaction_count,
                       settlement_count,
                       settlement_status,
                       :expectedAccount,
                       settlement_account,
                       max_lag,
                       evidence
                FROM classified
                ON CONFLICT (deterministic_key) DO UPDATE
                SET expected_amount = EXCLUDED.expected_amount,
                    settled_amount = EXCLUDED.settled_amount,
                    variance_amount = EXCLUDED.variance_amount,
                    transaction_count = EXCLUDED.transaction_count,
                    settlement_count = EXCLUDED.settlement_count,
                    settlement_status = EXCLUDED.settlement_status,
                    expected_settlement_account = EXCLUDED.expected_settlement_account,
                    settlement_account = EXCLUDED.settlement_account,
                    max_settlement_lag_days = EXCLUDED.max_settlement_lag_days,
                    evidence = EXCLUDED.evidence,
                    updated_at = now()
                """,
                new MapSqlParameterSource()
                        .addValue("fromDate", from)
                        .addValue("toDate", to)
                        .addValue("delayDays", delayDays)
                        .addValue("expectedAccount", expectedAccount));
    }

    private int upsertRiskSignals() {
        return jdbcTemplate.update("""
                INSERT INTO risk_signals (
                    deterministic_key, taxpayer_id, risk_rule_id, signal_type, tax_head,
                    period_start, period_end, observed_amount, declared_amount, estimated_gap,
                    confidence_score, severity, explanation, evidence, status
                )
                SELECT 'reconciliation:' || rr.deterministic_key,
                       NULL::uuid,
                       r.id,
                       'SETTLEMENT_RECONCILIATION',
                       'REVENUE_ASSURANCE',
                       rr.reconciliation_date,
                       rr.reconciliation_date,
                       rr.expected_amount,
                       rr.settled_amount,
                       abs(rr.variance_amount),
                       CASE rr.settlement_status
                           WHEN 'MISSING_SETTLEMENT' THEN 96.00
                           WHEN 'DUPLICATE_TRANSACTION' THEN 94.00
                           WHEN 'WRONG_ACCOUNT' THEN 92.00
                           WHEN 'DELAYED_SETTLEMENT' THEN 88.00
                           ELSE 85.00
                       END,
                       CASE rr.settlement_status
                           WHEN 'MISSING_SETTLEMENT' THEN 'HIGH'
                           WHEN 'WRONG_ACCOUNT' THEN 'HIGH'
                           WHEN 'DUPLICATE_TRANSACTION' THEN 'MEDIUM'
                           ELSE 'MEDIUM'
                       END,
                       rr.settlement_status || ' for ' || rr.collecting_agency || ' ' || rr.revenue_channel
                           || ' on ' || rr.reconciliation_date || ', variance ' || rr.variance_amount,
                       rr.evidence || jsonb_build_object(
                           'ruleCode', r.code,
                           'reconciliationResultId', rr.id,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM reconciliation_results rr
                JOIN risk_rules r ON r.code = 'PAYMENT_SETTLEMENT_MISMATCH'
                WHERE rr.settlement_status <> 'MATCHED'
                ON CONFLICT (deterministic_key) DO UPDATE
                SET observed_amount = EXCLUDED.observed_amount,
                    declared_amount = EXCLUDED.declared_amount,
                    estimated_gap = EXCLUDED.estimated_gap,
                    confidence_score = EXCLUDED.confidence_score,
                    severity = EXCLUDED.severity,
                    explanation = EXCLUDED.explanation,
                    evidence = EXCLUDED.evidence,
                    status = EXCLUDED.status
                """);
    }

    private void linkRiskSignals() {
        jdbcTemplate.update("""
                UPDATE reconciliation_results rr
                SET risk_signal_id = rs.id,
                    updated_at = now()
                FROM risk_signals rs
                WHERE rs.deterministic_key = 'reconciliation:' || rr.deterministic_key
                  AND rr.risk_signal_id IS DISTINCT FROM rs.id
                """);
    }

    private ReconciliationResultResponse resultById(UUID resultId) {
        List<ReconciliationResultResponse> results = namedJdbcTemplate.query("""
                SELECT *
                FROM reconciliation_results
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", resultId), this::result);
        if (results.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Reconciliation result not found");
        }
        return results.get(0);
    }

    private LocalDate minPaymentDate() {
        LocalDate value = jdbcTemplate.queryForObject(
                "SELECT min(payment_date::date) FROM payment_transactions",
                LocalDate.class
        );
        return value == null ? LocalDate.now() : value;
    }

    private LocalDate maxPaymentDate() {
        LocalDate value = jdbcTemplate.queryForObject(
                "SELECT max(payment_date::date) FROM payment_transactions",
                LocalDate.class
        );
        return value == null ? LocalDate.now() : value;
    }

    private ReconciliationResultResponse result(ResultSet rs, int rowNum) throws SQLException {
        return new ReconciliationResultResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("reconciliation_date", LocalDate.class),
                rs.getString("collecting_agency"),
                rs.getString("revenue_channel"),
                rs.getBigDecimal("expected_amount"),
                rs.getBigDecimal("settled_amount"),
                rs.getBigDecimal("variance_amount"),
                rs.getInt("transaction_count"),
                rs.getInt("settlement_count"),
                rs.getString("settlement_status"),
                rs.getString("expected_settlement_account"),
                rs.getString("settlement_account"),
                (Integer) rs.getObject("max_settlement_lag_days"),
                json(rs.getString("evidence")),
                rs.getObject("risk_signal_id", UUID.class),
                instant(rs, "created_at")
        );
    }

    private String priority(ReconciliationResultResponse result) {
        if ("MISSING_SETTLEMENT".equals(result.settlementStatus()) || "WRONG_ACCOUNT".equals(result.settlementStatus())) {
            return "HIGH";
        }
        return result.varianceAmount().compareTo(new BigDecimal("100000")) >= 0 ? "HIGH" : "MEDIUM";
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not parse reconciliation evidence", ex);
        }
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toInstant();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
