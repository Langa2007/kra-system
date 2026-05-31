ALTER TABLE risk_signals
    ADD COLUMN deterministic_key VARCHAR(320);

CREATE UNIQUE INDEX idx_risk_signals_deterministic_key
    ON risk_signals(deterministic_key);

CREATE INDEX idx_risk_signals_rule_created
    ON risk_signals(risk_rule_id, created_at);

INSERT INTO risk_rules (code, name, description, tax_head, rule_type, severity, threshold_config)
VALUES
    (
        'VAT_INPUT_MISMATCH',
        'VAT Input Tax Mismatch',
        'Flags taxpayers whose purchase invoice VAT materially exceeds declared input VAT.',
        'VAT',
        'DETERMINISTIC',
        'HIGH',
        '{"minimumGap": 50000, "minimumGapPercent": 10, "periodGraceDays": 45}'::jsonb
    ),
    (
        'NIL_FILER_ISSUING_INVOICES',
        'Nil Filer Issuing Invoices',
        'Flags nil or zero-sales VAT filers that issued taxable invoices in the same period.',
        'VAT',
        'DETERMINISTIC',
        'HIGH',
        '{"minimumInvoiceSales": 10000, "periodGraceDays": 45}'::jsonb
    ),
    (
        'PAYE_RATIO_ANOMALY',
        'PAYE Ratio Anomaly',
        'Flags payroll returns where PAYE due is materially below a simple gross-pay ratio.',
        'PAYE',
        'DETERMINISTIC',
        'MEDIUM',
        '{"minimumGrossPay": 100000, "minimumPayeToGrossRatio": 0.08, "minimumGap": 10000, "periodGraceDays": 45}'::jsonb
    ),
    (
        'PERMIT_ACTIVE_TAX_INACTIVE',
        'Active Permit Without Active Tax Obligation',
        'Flags taxpayers with active business permits but no active core tax obligation.',
        'INCOME_TAX',
        'DETERMINISTIC',
        'MEDIUM',
        '{"requiredTaxHeads": ["VAT", "INCOME_TAX"], "minimumPermitFee": 0}'::jsonb
    )
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    tax_head = EXCLUDED.tax_head,
    rule_type = EXCLUDED.rule_type,
    severity = EXCLUDED.severity,
    threshold_config = risk_rules.threshold_config || EXCLUDED.threshold_config,
    updated_at = now();

UPDATE risk_rules
SET threshold_config = threshold_config || '{"periodGraceDays": 45}'::jsonb,
    updated_at = now()
WHERE code IN ('VAT_OUTPUT_MISMATCH', 'IMPORT_TO_SALES_MISMATCH', 'WHT_INCOME_MISMATCH');

UPDATE risk_rules
SET threshold_config = threshold_config || '{"settlementDelayDays": 2, "minimumGap": 10000}'::jsonb,
    updated_at = now()
WHERE code = 'PAYMENT_SETTLEMENT_MISMATCH';
