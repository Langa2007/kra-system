INSERT INTO risk_rules (code, name, description, tax_head, rule_type, severity, threshold_config)
VALUES
    (
        'RENTAL_INCOME_MISMATCH',
        'Rental Income Mismatch',
        'Flags property owners whose estimated rental income materially exceeds declared rental or income-tax income.',
        'RENTAL_INCOME',
        'DETERMINISTIC',
        'HIGH',
        '{"minimumGap": 50000, "minimumProperties": 1, "annualizationMonths": 12}'::jsonb
    ),
    (
        'SECTOR_MARGIN_DEVIATION',
        'Sector Margin Deviation',
        'Flags taxpayers whose declared income margin is materially below peers in the same sector and period.',
        'INCOME_TAX',
        'DETERMINISTIC',
        'MEDIUM',
        '{"minimumSales": 100000, "minimumGap": 50000, "minimumPeerCount": 2, "minimumMarginShortfallPercent": 20}'::jsonb
    ),
    (
        'EXPENSE_FROM_NON_COMPLIANT_SUPPLIER',
        'Expense From Non-Compliant Supplier',
        'Flags material purchases or input claims from suppliers without active compliance indicators.',
        'VAT',
        'DETERMINISTIC',
        'MEDIUM',
        '{"minimumPurchaseAmount": 50000, "periodGraceDays": 45}'::jsonb
    )
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    tax_head = EXCLUDED.tax_head,
    rule_type = EXCLUDED.rule_type,
    severity = EXCLUDED.severity,
    threshold_config = risk_rules.threshold_config || EXCLUDED.threshold_config,
    updated_at = now();

CREATE MATERIALIZED VIEW mv_tax_gap_by_sector AS
SELECT
    COALESCE(t.sector_code, 'UNSPECIFIED') AS sector_code,
    COALESCE(t.sector_name, 'Unspecified') AS sector_name,
    e.tax_head,
    COUNT(*) AS estimate_count,
    COUNT(DISTINCT e.taxpayer_id) AS taxpayer_count,
    COALESCE(SUM(e.estimated_gap), 0) AS estimated_gap,
    COALESCE(SUM(e.estimated_recoverable_tax), 0) AS estimated_recoverable_tax,
    COALESCE(SUM(e.estimated_total_due), 0) AS estimated_total_due,
    COALESCE(ROUND(AVG(e.confidence_score), 2), 0) AS average_confidence
FROM tax_gap_estimates e
JOIN taxpayers t ON t.id = e.taxpayer_id
GROUP BY COALESCE(t.sector_code, 'UNSPECIFIED'), COALESCE(t.sector_name, 'Unspecified'), e.tax_head
WITH NO DATA;

CREATE UNIQUE INDEX idx_mv_tax_gap_by_sector_key
    ON mv_tax_gap_by_sector(sector_code, sector_name, tax_head);

CREATE MATERIALIZED VIEW mv_tax_gap_by_region AS
SELECT
    COALESCE(t.county, 'Unspecified') AS region,
    e.tax_head,
    COUNT(*) AS estimate_count,
    COUNT(DISTINCT e.taxpayer_id) AS taxpayer_count,
    COALESCE(SUM(e.estimated_gap), 0) AS estimated_gap,
    COALESCE(SUM(e.estimated_recoverable_tax), 0) AS estimated_recoverable_tax,
    COALESCE(SUM(e.estimated_total_due), 0) AS estimated_total_due,
    COALESCE(ROUND(AVG(e.confidence_score), 2), 0) AS average_confidence
FROM tax_gap_estimates e
JOIN taxpayers t ON t.id = e.taxpayer_id
GROUP BY COALESCE(t.county, 'Unspecified'), e.tax_head
WITH NO DATA;

CREATE UNIQUE INDEX idx_mv_tax_gap_by_region_key
    ON mv_tax_gap_by_region(region, tax_head);
