package com.nyle.kra.revenue.rules;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
public class RuleEngineService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DeterministicRuleCatalog catalog;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public RuleEngineService(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedJdbcTemplate,
            ObjectMapper objectMapper,
            DeterministicRuleCatalog catalog,
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

    public List<RuleDefinitionResponse> listRules() {
        return jdbcTemplate.query("""
                SELECT id, code, name, description, tax_head, rule_type, severity,
                       threshold_config::text AS threshold_config, active, updated_at
                FROM risk_rules
                ORDER BY code
                """, (rs, rowNum) -> new RuleDefinitionResponse(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("tax_head"),
                rs.getString("rule_type"),
                rs.getString("severity"),
                json(rs.getString("threshold_config")),
                rs.getBoolean("active"),
                instant(rs, "updated_at")
        ));
    }

    @Transactional
    public RuleDefinitionResponse updateRule(String code, RuleThresholdUpdateRequest request) {
        String normalizedCode = normalize(code);
        String thresholdConfig = request.thresholdConfig() == null ? null : request.thresholdConfig().toString();
        List<RuleDefinitionResponse> updated = namedJdbcTemplate.query("""
                UPDATE risk_rules
                SET threshold_config = COALESCE(CAST(:thresholdConfig AS jsonb), threshold_config),
                    severity = COALESCE(:severity, severity),
                    active = COALESCE(:active, active),
                    updated_at = now()
                WHERE code = :code
                RETURNING id, code, name, description, tax_head, rule_type, severity,
                          threshold_config::text AS threshold_config, active, updated_at
                """,
                new MapSqlParameterSource()
                        .addValue("code", normalizedCode)
                        .addValue("thresholdConfig", thresholdConfig)
                        .addValue("severity", blankToNull(request.severity()))
                        .addValue("active", request.active()),
                (rs, rowNum) -> new RuleDefinitionResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("tax_head"),
                        rs.getString("rule_type"),
                        rs.getString("severity"),
                        json(rs.getString("threshold_config")),
                        rs.getBoolean("active"),
                        instant(rs, "updated_at")
                ));
        if (updated.isEmpty()) {
            throw new IllegalArgumentException("Unknown risk rule: " + normalizedCode);
        }
        return updated.get(0);
    }

    @Transactional
    public RuleExecutionSummary runRules(
            List<String> requestedCodes,
            AuthenticatedUser authenticatedUser,
            HttpServletRequest request
    ) {
        List<RuleConfig> rules = activeRules(requestedCodes);
        List<RuleExecutionResult> results = new ArrayList<>();
        int totalSignalsTouched = 0;
        for (RuleConfig rule : rules) {
            int signalsTouched = execute(rule);
            totalSignalsTouched += signalsTouched;
            results.add(new RuleExecutionResult(rule.code(), signalsTouched));
        }
        RuleExecutionSummary summary = new RuleExecutionSummary(rules.size(), totalSignalsTouched, results);
        auditService.record(
                AuditService.RULE_ENGINE_RUN,
                actor(authenticatedUser),
                "rule_engine",
                null,
                request,
                Map.of(
                        "rulesExecuted", summary.rulesExecuted(),
                        "signalsTouched", summary.signalsTouched()
                )
        );
        return summary;
    }

    public List<RiskSignalResponse> listSignals(String ruleCode, UUID taxpayerId, String status, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ruleCode", blankToNull(ruleCode) == null ? null : normalize(ruleCode))
                .addValue("taxpayerId", taxpayerId)
                .addValue("status", blankToNull(status))
                .addValue("limit", Math.max(1, Math.min(limit, 200)));
        return namedJdbcTemplate.query("""
                SELECT rs.id, rs.taxpayer_id, t.kra_pin, t.legal_name, rr.code AS rule_code,
                       rs.signal_type, rs.tax_head, rs.period_start, rs.period_end,
                       rs.observed_amount, rs.declared_amount, rs.estimated_gap,
                       rs.confidence_score, rs.severity, rs.explanation, rs.evidence::text AS evidence,
                       rs.status, rs.created_at
                FROM risk_signals rs
                JOIN risk_rules rr ON rr.id = rs.risk_rule_id
                LEFT JOIN taxpayers t ON t.id = rs.taxpayer_id
                WHERE (CAST(:ruleCode AS text) IS NULL OR rr.code = CAST(:ruleCode AS text))
                  AND (CAST(:taxpayerId AS uuid) IS NULL OR rs.taxpayer_id = CAST(:taxpayerId AS uuid))
                  AND (CAST(:status AS text) IS NULL OR rs.status = CAST(:status AS text))
                ORDER BY rs.created_at DESC, rr.code ASC
                LIMIT :limit
                """, params, this::riskSignal);
    }

    private List<RuleConfig> activeRules(List<String> requestedCodes) {
        List<String> codes = requestedCodes == null || requestedCodes.isEmpty()
                ? catalog.supportedCodes()
                : requestedCodes.stream().map(this::normalize).distinct().toList();
        for (String code : codes) {
            if (!catalog.supports(code)) {
                throw new IllegalArgumentException("Unsupported deterministic rule: " + code);
            }
        }
        return namedJdbcTemplate.query("""
                SELECT id, code, severity, tax_head, threshold_config::text AS threshold_config
                FROM risk_rules
                WHERE active = TRUE AND code IN (:codes)
                ORDER BY code
                """,
                new MapSqlParameterSource().addValue("codes", codes),
                (rs, rowNum) -> new RuleConfig(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("severity"),
                        rs.getString("tax_head"),
                        json(rs.getString("threshold_config"))
                ));
    }

    private int execute(RuleConfig rule) {
        return switch (rule.code()) {
            case "VAT_OUTPUT_MISMATCH" -> vatOutputMismatch(rule);
            case "VAT_INPUT_MISMATCH" -> vatInputMismatch(rule);
            case "IMPORT_TO_SALES_MISMATCH" -> importToSalesMismatch(rule);
            case "WHT_INCOME_MISMATCH" -> whtIncomeMismatch(rule);
            case "NIL_FILER_ISSUING_INVOICES" -> nilFilerIssuingInvoices(rule);
            case "PAYE_RATIO_ANOMALY" -> payeRatioAnomaly(rule);
            case "PERMIT_ACTIVE_TAX_INACTIVE" -> permitActiveTaxInactive(rule);
            case "PAYMENT_SETTLEMENT_MISMATCH" -> paymentSettlementMismatch(rule);
            default -> throw new IllegalArgumentException("Unsupported deterministic rule: " + rule.code());
        };
    }

    private int vatOutputMismatch(RuleConfig rule) {
        return upsert(rule, params(rule)
                .addValue("minimumGap", money(rule, "minimumGap", "100000"))
                .addValue("minimumGapPercent", decimal(rule, "minimumGapPercent", "10"))
                .addValue("periodGraceDays", integer(rule, "periodGraceDays", 45)), """
                WITH invoice_totals AS (
                    SELECT supplier_taxpayer_id AS taxpayer_id,
                           date_trunc('month', invoice_date)::date AS period_start,
                           (date_trunc('month', invoice_date)::date + INTERVAL '1 month - 1 day')::date AS period_end,
                           sum(taxable_amount) AS observed_sales,
                           sum(tax_amount) AS observed_output_tax,
                           count(*) AS invoice_count
                    FROM invoices
                    WHERE supplier_taxpayer_id IS NOT NULL
                      AND upper(invoice_status) NOT IN ('CANCELLED', 'VOID')
                    GROUP BY supplier_taxpayer_id, date_trunc('month', invoice_date)::date
                ),
                return_totals AS (
                    SELECT taxpayer_id, period_start, period_end,
                           sum(coalesce(declared_sales, 0)) AS declared_sales,
                           sum(coalesce(declared_output_tax, 0)) AS declared_output_tax
                    FROM tax_returns
                    WHERE tax_head = 'VAT'
                    GROUP BY taxpayer_id, period_start, period_end
                ),
                flagged AS (
                    SELECT i.*, coalesce(r.declared_sales, 0) AS declared_sales,
                           i.observed_sales - coalesce(r.declared_sales, 0) AS gap
                    FROM invoice_totals i
                    LEFT JOIN return_totals r
                      ON r.taxpayer_id = i.taxpayer_id
                     AND r.period_start = i.period_start
                     AND r.period_end = i.period_end
                    WHERE i.period_end <= current_date - CAST(:periodGraceDays AS integer)
                      AND i.observed_sales - coalesce(r.declared_sales, 0) >= :minimumGap
                      AND ((i.observed_sales - coalesce(r.declared_sales, 0))
                          / greatest(coalesce(r.declared_sales, 0), 1) * 100) >= :minimumGapPercent
                )
                SELECT r.code || ':' || f.taxpayer_id || ':' || f.period_start || ':' || f.period_end,
                       f.taxpayer_id, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.observed_sales, f.declared_sales, f.gap, 95.00, r.severity,
                       'Invoice taxable sales exceed declared VAT sales by ' || f.gap,
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'invoiceCount', f.invoice_count,
                           'observedOutputTax', f.observed_output_tax,
                           'minimumGap', :minimumGap,
                           'minimumGapPercent', :minimumGapPercent,
                           'periodGraceDays', :periodGraceDays,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int vatInputMismatch(RuleConfig rule) {
        return upsert(rule, params(rule)
                .addValue("minimumGap", money(rule, "minimumGap", "50000"))
                .addValue("minimumGapPercent", decimal(rule, "minimumGapPercent", "10"))
                .addValue("periodGraceDays", integer(rule, "periodGraceDays", 45)), """
                WITH purchase_totals AS (
                    SELECT buyer_taxpayer_id AS taxpayer_id,
                           date_trunc('month', invoice_date)::date AS period_start,
                           (date_trunc('month', invoice_date)::date + INTERVAL '1 month - 1 day')::date AS period_end,
                           sum(tax_amount) AS observed_input_tax,
                           count(*) AS invoice_count
                    FROM invoices
                    WHERE buyer_taxpayer_id IS NOT NULL
                      AND upper(invoice_status) NOT IN ('CANCELLED', 'VOID')
                    GROUP BY buyer_taxpayer_id, date_trunc('month', invoice_date)::date
                ),
                return_totals AS (
                    SELECT taxpayer_id, period_start, period_end,
                           sum(coalesce(declared_input_tax, 0)) AS declared_input_tax
                    FROM tax_returns
                    WHERE tax_head = 'VAT'
                    GROUP BY taxpayer_id, period_start, period_end
                ),
                flagged AS (
                    SELECT p.*, coalesce(r.declared_input_tax, 0) AS declared_input_tax,
                           p.observed_input_tax - coalesce(r.declared_input_tax, 0) AS gap
                    FROM purchase_totals p
                    LEFT JOIN return_totals r
                      ON r.taxpayer_id = p.taxpayer_id
                     AND r.period_start = p.period_start
                     AND r.period_end = p.period_end
                    WHERE p.period_end <= current_date - CAST(:periodGraceDays AS integer)
                      AND p.observed_input_tax - coalesce(r.declared_input_tax, 0) >= :minimumGap
                      AND ((p.observed_input_tax - coalesce(r.declared_input_tax, 0))
                          / greatest(coalesce(r.declared_input_tax, 0), 1) * 100) >= :minimumGapPercent
                )
                SELECT r.code || ':' || f.taxpayer_id || ':' || f.period_start || ':' || f.period_end,
                       f.taxpayer_id, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.observed_input_tax, f.declared_input_tax, f.gap, 92.00, r.severity,
                       'Purchase invoice VAT exceeds declared input VAT by ' || f.gap,
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'invoiceCount', f.invoice_count,
                           'minimumGap', :minimumGap,
                           'minimumGapPercent', :minimumGapPercent,
                           'periodGraceDays', :periodGraceDays,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int importToSalesMismatch(RuleConfig rule) {
        return upsert(rule, params(rule)
                .addValue("minimumGap", money(rule, "minimumGap", "250000"))
                .addValue("minimumGapPercent", decimal(rule, "minimumGapPercent", "15"))
                .addValue("periodGraceDays", integer(rule, "periodGraceDays", 45)), """
                WITH import_totals AS (
                    SELECT taxpayer_id,
                           date_trunc('month', declaration_date)::date AS period_start,
                           (date_trunc('month', declaration_date)::date + INTERVAL '1 month - 1 day')::date AS period_end,
                           sum(coalesce(total_landed_cost, customs_value)) AS observed_import_value,
                           count(*) AS declaration_count
                    FROM customs_declarations
                    WHERE taxpayer_id IS NOT NULL
                    GROUP BY taxpayer_id, date_trunc('month', declaration_date)::date
                ),
                return_totals AS (
                    SELECT taxpayer_id, period_start, period_end,
                           sum(greatest(coalesce(declared_sales, 0), coalesce(declared_income, 0))) AS declared_activity
                    FROM tax_returns
                    WHERE tax_head IN ('VAT', 'INCOME_TAX')
                    GROUP BY taxpayer_id, period_start, period_end
                ),
                flagged AS (
                    SELECT i.*, coalesce(r.declared_activity, 0) AS declared_activity,
                           i.observed_import_value - coalesce(r.declared_activity, 0) AS gap
                    FROM import_totals i
                    LEFT JOIN return_totals r
                      ON r.taxpayer_id = i.taxpayer_id
                     AND r.period_start = i.period_start
                     AND r.period_end = i.period_end
                    WHERE i.period_end <= current_date - CAST(:periodGraceDays AS integer)
                      AND i.observed_import_value - coalesce(r.declared_activity, 0) >= :minimumGap
                      AND ((i.observed_import_value - coalesce(r.declared_activity, 0))
                          / greatest(coalesce(r.declared_activity, 0), 1) * 100) >= :minimumGapPercent
                )
                SELECT r.code || ':' || f.taxpayer_id || ':' || f.period_start || ':' || f.period_end,
                       f.taxpayer_id, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.observed_import_value, f.declared_activity, f.gap, 90.00, r.severity,
                       'Customs import value exceeds declared sales or income by ' || f.gap,
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'declarationCount', f.declaration_count,
                           'minimumGap', :minimumGap,
                           'minimumGapPercent', :minimumGapPercent,
                           'periodGraceDays', :periodGraceDays,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int whtIncomeMismatch(RuleConfig rule) {
        return upsert(rule, params(rule)
                .addValue("minimumGap", money(rule, "minimumGap", "50000"))
                .addValue("minimumGapPercent", decimal(rule, "minimumGapPercent", "10"))
                .addValue("periodGraceDays", integer(rule, "periodGraceDays", 45)), """
                WITH certificate_totals AS (
                    SELECT payee_taxpayer_id AS taxpayer_id,
                           date_trunc('month', coalesce(payment_period_end, certificate_date))::date AS period_start,
                           (date_trunc('month', coalesce(payment_period_end, certificate_date))::date
                               + INTERVAL '1 month - 1 day')::date AS period_end,
                           sum(gross_amount) AS observed_income,
                           sum(withheld_amount) AS withheld_amount,
                           count(*) AS certificate_count
                    FROM withholding_certificates
                    WHERE payee_taxpayer_id IS NOT NULL
                    GROUP BY payee_taxpayer_id, date_trunc('month', coalesce(payment_period_end, certificate_date))::date
                ),
                return_totals AS (
                    SELECT taxpayer_id, period_start, period_end,
                           sum(coalesce(declared_income, 0)) AS declared_income
                    FROM tax_returns
                    WHERE tax_head = 'INCOME_TAX'
                    GROUP BY taxpayer_id, period_start, period_end
                ),
                flagged AS (
                    SELECT c.*, coalesce(r.declared_income, 0) AS declared_income,
                           c.observed_income - coalesce(r.declared_income, 0) AS gap
                    FROM certificate_totals c
                    LEFT JOIN return_totals r
                      ON r.taxpayer_id = c.taxpayer_id
                     AND r.period_start = c.period_start
                     AND r.period_end = c.period_end
                    WHERE c.period_end <= current_date - CAST(:periodGraceDays AS integer)
                      AND c.observed_income - coalesce(r.declared_income, 0) >= :minimumGap
                      AND ((c.observed_income - coalesce(r.declared_income, 0))
                          / greatest(coalesce(r.declared_income, 0), 1) * 100) >= :minimumGapPercent
                )
                SELECT r.code || ':' || f.taxpayer_id || ':' || f.period_start || ':' || f.period_end,
                       f.taxpayer_id, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.observed_income, f.declared_income, f.gap, 88.00, r.severity,
                       'WHT certificate gross income exceeds declared income by ' || f.gap,
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'certificateCount', f.certificate_count,
                           'withheldAmount', f.withheld_amount,
                           'minimumGap', :minimumGap,
                           'minimumGapPercent', :minimumGapPercent,
                           'periodGraceDays', :periodGraceDays,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int nilFilerIssuingInvoices(RuleConfig rule) {
        return upsert(rule, params(rule)
                .addValue("minimumInvoiceSales", money(rule, "minimumInvoiceSales", "10000"))
                .addValue("periodGraceDays", integer(rule, "periodGraceDays", 45)), """
                WITH invoice_totals AS (
                    SELECT supplier_taxpayer_id AS taxpayer_id,
                           date_trunc('month', invoice_date)::date AS period_start,
                           (date_trunc('month', invoice_date)::date + INTERVAL '1 month - 1 day')::date AS period_end,
                           sum(taxable_amount) AS observed_sales,
                           count(*) AS invoice_count
                    FROM invoices
                    WHERE supplier_taxpayer_id IS NOT NULL
                      AND upper(invoice_status) NOT IN ('CANCELLED', 'VOID')
                    GROUP BY supplier_taxpayer_id, date_trunc('month', invoice_date)::date
                ),
                nil_returns AS (
                    SELECT taxpayer_id, period_start, period_end,
                           coalesce(sum(declared_sales), 0) AS declared_sales,
                           max(filing_status) AS filing_status
                    FROM tax_returns
                    WHERE tax_head = 'VAT'
                    GROUP BY taxpayer_id, period_start, period_end
                    HAVING coalesce(sum(declared_sales), 0) = 0
                       OR bool_or(upper(filing_status) IN ('NIL', 'NO_ACTIVITY'))
                ),
                flagged AS (
                    SELECT i.*, n.declared_sales, n.filing_status, i.observed_sales AS gap
                    FROM invoice_totals i
                    JOIN nil_returns n
                      ON n.taxpayer_id = i.taxpayer_id
                     AND n.period_start = i.period_start
                     AND n.period_end = i.period_end
                    WHERE i.period_end <= current_date - CAST(:periodGraceDays AS integer)
                      AND i.observed_sales >= :minimumInvoiceSales
                )
                SELECT r.code || ':' || f.taxpayer_id || ':' || f.period_start || ':' || f.period_end,
                       f.taxpayer_id, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.observed_sales, f.declared_sales, f.gap, 96.00, r.severity,
                       'Taxpayer filed nil VAT return but issued taxable invoices of ' || f.observed_sales,
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'invoiceCount', f.invoice_count,
                           'filingStatus', f.filing_status,
                           'minimumInvoiceSales', :minimumInvoiceSales,
                           'periodGraceDays', :periodGraceDays,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int payeRatioAnomaly(RuleConfig rule) {
        return upsert(rule, params(rule)
                .addValue("minimumGrossPay", money(rule, "minimumGrossPay", "100000"))
                .addValue("minimumPayeToGrossRatio", decimal(rule, "minimumPayeToGrossRatio", "0.08"))
                .addValue("minimumGap", money(rule, "minimumGap", "10000"))
                .addValue("periodGraceDays", integer(rule, "periodGraceDays", 45)), """
                WITH flagged AS (
                    SELECT taxpayer_id, period_start, period_end,
                           gross_pay,
                           coalesce(paye_due, 0) AS paye_due,
                           gross_pay * :minimumPayeToGrossRatio AS expected_paye,
                           gross_pay * :minimumPayeToGrossRatio - coalesce(paye_due, 0) AS gap,
                           employee_count
                    FROM payroll_returns
                    WHERE gross_pay >= :minimumGrossPay
                      AND period_end <= current_date - CAST(:periodGraceDays AS integer)
                      AND gross_pay > 0
                      AND coalesce(paye_due, 0) / gross_pay < :minimumPayeToGrossRatio
                      AND gross_pay * :minimumPayeToGrossRatio - coalesce(paye_due, 0) >= :minimumGap
                )
                SELECT r.code || ':' || f.taxpayer_id || ':' || f.period_start || ':' || f.period_end,
                       f.taxpayer_id, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.expected_paye, f.paye_due, f.gap, 82.00, r.severity,
                       'PAYE due is below the configured gross pay ratio by ' || f.gap,
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'grossPay', f.gross_pay,
                           'employeeCount', f.employee_count,
                           'minimumGrossPay', :minimumGrossPay,
                           'minimumPayeToGrossRatio', :minimumPayeToGrossRatio,
                           'minimumGap', :minimumGap,
                           'periodGraceDays', :periodGraceDays,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int permitActiveTaxInactive(RuleConfig rule) {
        List<String> requiredTaxHeads = requiredTaxHeads(rule);
        return upsert(rule, params(rule)
                .addValue("requiredTaxHeads", requiredTaxHeads)
                .addValue("requiredTaxHeadsEvidence", toJson(requiredTaxHeads))
                .addValue("minimumPermitFee", money(rule, "minimumPermitFee", "0")), """
                WITH flagged AS (
                    SELECT bp.taxpayer_id,
                           coalesce(bp.valid_from, current_date) AS period_start,
                           coalesce(bp.valid_to, current_date) AS period_end,
                           coalesce(bp.permit_fee, 0) AS permit_fee,
                           bp.permit_number,
                           bp.county,
                           bp.business_activity
                    FROM business_permits bp
                    WHERE bp.taxpayer_id IS NOT NULL
                      AND upper(bp.permit_status) = 'ACTIVE'
                      AND coalesce(bp.permit_fee, 0) >= :minimumPermitFee
                      AND NOT EXISTS (
                          SELECT 1
                          FROM tax_obligations o
                          WHERE o.taxpayer_id = bp.taxpayer_id
                            AND o.tax_head IN (:requiredTaxHeads)
                            AND upper(o.obligation_status) = 'ACTIVE'
                            AND (o.effective_from IS NULL OR o.effective_from <= coalesce(bp.valid_to, current_date))
                            AND (o.effective_to IS NULL OR o.effective_to >= coalesce(bp.valid_from, current_date))
                      )
                )
                SELECT r.code || ':' || f.taxpayer_id || ':' || f.permit_number,
                       f.taxpayer_id, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.permit_fee, 0::numeric, f.permit_fee, 78.00, r.severity,
                       'Taxpayer has an active business permit but no active core tax obligation',
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'permitNumber', f.permit_number,
                           'county', f.county,
                           'businessActivity', f.business_activity,
                           'requiredTaxHeads', CAST(:requiredTaxHeadsEvidence AS jsonb),
                           'minimumPermitFee', :minimumPermitFee,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int paymentSettlementMismatch(RuleConfig rule) {
        return upsert(rule, params(rule)
                .addValue("minimumGap", money(rule, "minimumGap", "10000"))
                .addValue("settlementDelayDays", integer(rule, "settlementDelayDays", 2)), """
                WITH payment_totals AS (
                    SELECT collecting_agency,
                           revenue_channel,
                           payment_date::date AS period_start,
                           payment_date::date AS period_end,
                           sum(amount) AS collected_amount,
                           count(*) AS payment_count
                    FROM payment_transactions
                    WHERE upper(payment_status) IN ('PAID', 'SUCCESS', 'SUCCESSFUL', 'COMPLETED')
                    GROUP BY collecting_agency, revenue_channel, payment_date::date
                ),
                reconciled AS (
                    SELECT p.collecting_agency, p.revenue_channel, p.period_start, p.period_end,
                           p.collected_amount, p.payment_count,
                           coalesce(sum(s.settled_amount), 0) AS settled_amount,
                           count(s.id) AS settlement_count
                    FROM payment_totals p
                    LEFT JOIN settlement_records s
                      ON lower(s.collecting_agency) = lower(p.collecting_agency)
                     AND lower(s.revenue_channel) = lower(p.revenue_channel)
                     AND s.settlement_date >= p.period_start
                     AND s.settlement_date <= p.period_start + CAST(:settlementDelayDays AS integer)
                     AND upper(s.settlement_status) IN ('SETTLED', 'SUCCESS', 'COMPLETED')
                    GROUP BY p.collecting_agency, p.revenue_channel, p.period_start, p.period_end,
                             p.collected_amount, p.payment_count
                ),
                flagged AS (
                    SELECT *, collected_amount - settled_amount AS gap
                    FROM reconciled
                    WHERE period_start <= current_date - (CAST(:settlementDelayDays AS integer) + 1)
                      AND collected_amount - settled_amount >= :minimumGap
                )
                SELECT r.code || ':' || lower(f.collecting_agency) || ':' || lower(f.revenue_channel) || ':' || f.period_start,
                       NULL::uuid, r.id, r.code, r.tax_head, f.period_start, f.period_end,
                       f.collected_amount, f.settled_amount, f.gap, 90.00, r.severity,
                       'Collected payment amount exceeds settled amount by ' || f.gap,
                       jsonb_build_object(
                           'ruleCode', r.code,
                           'collectingAgency', f.collecting_agency,
                           'revenueChannel', f.revenue_channel,
                           'paymentCount', f.payment_count,
                           'settlementCount', f.settlement_count,
                           'settlementDelayDays', :settlementDelayDays,
                           'minimumGap', :minimumGap,
                           'thresholds', r.threshold_config
                       ),
                       'OPEN'
                FROM flagged f
                JOIN risk_rules r ON r.code = :code
                """);
    }

    private int upsert(RuleConfig rule, MapSqlParameterSource params, String selectSql) {
        String sql = """
                INSERT INTO risk_signals (
                    deterministic_key, taxpayer_id, risk_rule_id, signal_type, tax_head,
                    period_start, period_end, observed_amount, declared_amount, estimated_gap,
                    confidence_score, severity, explanation, evidence, status
                )
                """ + selectSql + """
                ON CONFLICT (deterministic_key) DO UPDATE
                SET taxpayer_id = EXCLUDED.taxpayer_id,
                    risk_rule_id = EXCLUDED.risk_rule_id,
                    signal_type = EXCLUDED.signal_type,
                    tax_head = EXCLUDED.tax_head,
                    period_start = EXCLUDED.period_start,
                    period_end = EXCLUDED.period_end,
                    observed_amount = EXCLUDED.observed_amount,
                    declared_amount = EXCLUDED.declared_amount,
                    estimated_gap = EXCLUDED.estimated_gap,
                    confidence_score = EXCLUDED.confidence_score,
                    severity = EXCLUDED.severity,
                    explanation = EXCLUDED.explanation,
                    evidence = EXCLUDED.evidence,
                    status = EXCLUDED.status
                """;
        params.addValue("code", rule.code());
        return namedJdbcTemplate.update(sql, params);
    }

    private MapSqlParameterSource params(RuleConfig rule) {
        return new MapSqlParameterSource()
                .addValue("code", rule.code());
    }

    private RiskSignalResponse riskSignal(ResultSet rs, int rowNum) throws SQLException {
        return new RiskSignalResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("taxpayer_id", UUID.class),
                rs.getString("kra_pin"),
                rs.getString("legal_name"),
                rs.getString("rule_code"),
                rs.getString("signal_type"),
                rs.getString("tax_head"),
                rs.getObject("period_start", java.time.LocalDate.class),
                rs.getObject("period_end", java.time.LocalDate.class),
                rs.getBigDecimal("observed_amount"),
                rs.getBigDecimal("declared_amount"),
                rs.getBigDecimal("estimated_gap"),
                rs.getBigDecimal("confidence_score"),
                rs.getString("severity"),
                rs.getString("explanation"),
                json(rs.getString("evidence")),
                rs.getString("status"),
                instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid JSON from database", ex);
        }
    }

    private String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private BigDecimal money(RuleConfig rule, String field, String defaultValue) {
        JsonNode value = rule.thresholds().path(field);
        return value.isMissingNode() || value.isNull()
                ? new BigDecimal(defaultValue)
                : value.decimalValue();
    }

    private BigDecimal decimal(RuleConfig rule, String field, String defaultValue) {
        return money(rule, field, defaultValue);
    }

    private int integer(RuleConfig rule, String field, int defaultValue) {
        JsonNode value = rule.thresholds().path(field);
        return value.isInt() || value.isLong() ? value.asInt() : defaultValue;
    }

    private List<String> requiredTaxHeads(RuleConfig rule) {
        JsonNode node = rule.thresholds().path("requiredTaxHeads");
        if (!node.isArray() || node.isEmpty()) {
            return List.of("VAT", "INCOME_TAX");
        }
        List<String> taxHeads = new ArrayList<>();
        node.forEach(item -> taxHeads.add(item.asText()));
        return taxHeads;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize evidence value", ex);
        }
    }

    private Optional<AppUser> actor(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            return Optional.empty();
        }
        UUID userId = authenticatedUser.getUserId();
        return userId == null ? Optional.empty() : appUserRepository.findById(userId);
    }
}
