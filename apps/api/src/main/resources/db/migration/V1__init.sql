CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE data_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    owner_agency VARCHAR(160),
    ingestion_method VARCHAR(80) NOT NULL,
    schema_version VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source_id UUID NOT NULL REFERENCES data_sources(id),
    file_name VARCHAR(300),
    status VARCHAR(40) NOT NULL,
    records_received BIGINT NOT NULL DEFAULT 0,
    records_valid BIGINT NOT NULL DEFAULT 0,
    records_invalid BIGINT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    error_summary TEXT,
    created_by UUID
);

CREATE TABLE data_quality_issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_job_id UUID REFERENCES ingestion_jobs(id),
    data_source_id UUID REFERENCES data_sources(id),
    severity VARCHAR(40) NOT NULL,
    issue_type VARCHAR(120) NOT NULL,
    record_reference VARCHAR(200),
    field_name VARCHAR(120),
    issue_message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE taxpayers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    kra_pin VARCHAR(40) UNIQUE,
    taxpayer_type VARCHAR(40) NOT NULL,
    legal_name VARCHAR(240) NOT NULL,
    trading_name VARCHAR(240),
    registration_number VARCHAR(80),
    sector_code VARCHAR(80),
    sector_name VARCHAR(160),
    tax_office VARCHAR(160),
    county VARCHAR(120),
    status VARCHAR(60) NOT NULL,
    registered_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE taxpayer_identifiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    identifier_type VARCHAR(80) NOT NULL,
    identifier_value VARCHAR(200) NOT NULL,
    source VARCHAR(120),
    confidence_score NUMERIC(5,2) NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(identifier_type, identifier_value)
);

CREATE TABLE taxpayer_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    target_taxpayer_id UUID REFERENCES taxpayers(id),
    related_person_name VARCHAR(240),
    relationship_type VARCHAR(80) NOT NULL,
    source VARCHAR(120),
    confidence_score NUMERIC(5,2) NOT NULL,
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tax_obligations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    tax_head VARCHAR(80) NOT NULL,
    obligation_status VARCHAR(60) NOT NULL,
    effective_from DATE,
    effective_to DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tax_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    tax_head VARCHAR(80) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    return_reference VARCHAR(120),
    declared_sales NUMERIC(18,2),
    declared_income NUMERIC(18,2),
    declared_tax_due NUMERIC(18,2),
    declared_input_tax NUMERIC(18,2),
    declared_output_tax NUMERIC(18,2),
    filing_status VARCHAR(60) NOT NULL,
    filed_at TIMESTAMPTZ,
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(120) NOT NULL,
    supplier_taxpayer_id UUID REFERENCES taxpayers(id),
    buyer_taxpayer_id UUID REFERENCES taxpayers(id),
    supplier_pin VARCHAR(40),
    buyer_pin VARCHAR(40),
    invoice_date DATE NOT NULL,
    invoice_type VARCHAR(60) NOT NULL,
    invoice_status VARCHAR(60) NOT NULL,
    taxable_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency VARCHAR(10) NOT NULL DEFAULT 'KES',
    etims_reference VARCHAR(160),
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(invoice_number, supplier_pin, invoice_date)
);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    line_number INTEGER NOT NULL,
    item_description TEXT,
    quantity NUMERIC(18,4),
    unit_price NUMERIC(18,4),
    taxable_amount NUMERIC(18,2),
    tax_amount NUMERIC(18,2),
    hs_code VARCHAR(40),
    product_code VARCHAR(80)
);

CREATE TABLE customs_declarations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID REFERENCES taxpayers(id),
    importer_pin VARCHAR(40),
    declaration_number VARCHAR(120) NOT NULL UNIQUE,
    declaration_type VARCHAR(80) NOT NULL,
    declaration_date DATE NOT NULL,
    hs_code VARCHAR(40),
    goods_description TEXT,
    country_of_origin VARCHAR(120),
    customs_value NUMERIC(18,2) NOT NULL DEFAULT 0,
    duty_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    vat_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_landed_cost NUMERIC(18,2),
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE withholding_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    certificate_number VARCHAR(120) NOT NULL UNIQUE,
    payer_taxpayer_id UUID REFERENCES taxpayers(id),
    payee_taxpayer_id UUID REFERENCES taxpayers(id),
    payer_pin VARCHAR(40),
    payee_pin VARCHAR(40),
    certificate_date DATE NOT NULL,
    payment_period_start DATE,
    payment_period_end DATE,
    gross_amount NUMERIC(18,2) NOT NULL,
    withheld_amount NUMERIC(18,2) NOT NULL,
    tax_rate NUMERIC(8,4),
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payroll_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    employee_count INTEGER,
    gross_pay NUMERIC(18,2),
    paye_due NUMERIC(18,2),
    paye_paid NUMERIC(18,2),
    filing_status VARCHAR(60) NOT NULL,
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE business_permits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID REFERENCES taxpayers(id),
    permit_number VARCHAR(120) NOT NULL,
    county VARCHAR(120) NOT NULL,
    business_activity VARCHAR(200),
    premises_location VARCHAR(240),
    valid_from DATE,
    valid_to DATE,
    permit_fee NUMERIC(18,2),
    permit_status VARCHAR(60) NOT NULL,
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE properties (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_reference VARCHAR(120) NOT NULL UNIQUE,
    owner_taxpayer_id UUID REFERENCES taxpayers(id),
    owner_pin VARCHAR(40),
    county VARCHAR(120),
    location_description VARCHAR(240),
    property_type VARCHAR(80),
    valuation_amount NUMERIC(18,2),
    estimated_monthly_rent NUMERIC(18,2),
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_reference VARCHAR(160) NOT NULL UNIQUE,
    payer_taxpayer_id UUID REFERENCES taxpayers(id),
    payer_pin VARCHAR(40),
    collecting_agency VARCHAR(160) NOT NULL,
    revenue_channel VARCHAR(120) NOT NULL,
    service_code VARCHAR(120),
    payment_date TIMESTAMPTZ NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KES',
    payment_status VARCHAR(60) NOT NULL,
    provider_reference VARCHAR(160),
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE settlement_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_reference VARCHAR(160) NOT NULL UNIQUE,
    collecting_agency VARCHAR(160) NOT NULL,
    revenue_channel VARCHAR(120) NOT NULL,
    settlement_account VARCHAR(160),
    settlement_date DATE NOT NULL,
    settled_amount NUMERIC(18,2) NOT NULL,
    transaction_count INTEGER,
    settlement_status VARCHAR(60) NOT NULL,
    source_job_id UUID REFERENCES ingestion_jobs(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE risk_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    tax_head VARCHAR(80),
    rule_type VARCHAR(80) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    threshold_config JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_name VARCHAR(160) NOT NULL,
    version VARCHAR(80) NOT NULL,
    model_type VARCHAR(80) NOT NULL,
    training_data_summary TEXT,
    metrics JSONB,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(model_name, version)
);

CREATE TABLE risk_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID REFERENCES taxpayers(id),
    risk_rule_id UUID REFERENCES risk_rules(id),
    signal_type VARCHAR(120) NOT NULL,
    tax_head VARCHAR(80),
    period_start DATE,
    period_end DATE,
    observed_amount NUMERIC(18,2),
    declared_amount NUMERIC(18,2),
    estimated_gap NUMERIC(18,2),
    confidence_score NUMERIC(5,2) NOT NULL,
    severity VARCHAR(40) NOT NULL,
    explanation TEXT NOT NULL,
    evidence JSONB NOT NULL,
    status VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE risk_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    score NUMERIC(5,2) NOT NULL,
    confidence_score NUMERIC(5,2) NOT NULL,
    scoring_period_start DATE,
    scoring_period_end DATE,
    main_factors JSONB NOT NULL,
    model_version_id UUID REFERENCES model_versions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_identity_id VARCHAR(160),
    email VARCHAR(240) NOT NULL UNIQUE,
    full_name VARCHAR(240) NOT NULL,
    department VARCHAR(160),
    status VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    description TEXT
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES app_users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_number VARCHAR(120) NOT NULL UNIQUE,
    taxpayer_id UUID REFERENCES taxpayers(id),
    title VARCHAR(240) NOT NULL,
    case_type VARCHAR(80) NOT NULL,
    priority VARCHAR(40) NOT NULL,
    status VARCHAR(60) NOT NULL,
    estimated_recoverable_amount NUMERIC(18,2),
    assigned_to UUID REFERENCES app_users(id),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    closure_reason VARCHAR(160)
);

CREATE TABLE case_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases(id),
    event_type VARCHAR(80) NOT NULL,
    event_note TEXT,
    created_by UUID REFERENCES app_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evidence_packs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases(id),
    version INTEGER NOT NULL,
    summary TEXT NOT NULL,
    evidence_json JSONB NOT NULL,
    file_uri VARCHAR(500),
    generated_by UUID REFERENCES app_users(id),
    generated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(case_id, version)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    taxpayer_id UUID REFERENCES taxpayers(id),
    case_id UUID REFERENCES cases(id),
    channel VARCHAR(40) NOT NULL,
    template_code VARCHAR(120) NOT NULL,
    recipient VARCHAR(240),
    subject VARCHAR(240),
    message_body TEXT NOT NULL,
    status VARCHAR(60) NOT NULL,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE recovery_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases(id),
    assessed_amount NUMERIC(18,2),
    agreed_amount NUMERIC(18,2),
    collected_amount NUMERIC(18,2),
    collection_date DATE,
    recovery_status VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model_predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_version_id UUID NOT NULL REFERENCES model_versions(id),
    taxpayer_id UUID REFERENCES taxpayers(id),
    prediction_type VARCHAR(120) NOT NULL,
    score NUMERIC(8,4) NOT NULL,
    explanation JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id UUID REFERENCES app_users(id),
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(120),
    entity_id UUID,
    ip_address VARCHAR(80),
    user_agent TEXT,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ingestion_jobs_data_source_status ON ingestion_jobs(data_source_id, status);
CREATE INDEX idx_data_quality_issues_job ON data_quality_issues(ingestion_job_id);
CREATE INDEX idx_taxpayers_kra_pin ON taxpayers(kra_pin);
CREATE INDEX idx_taxpayers_registration_number ON taxpayers(registration_number);
CREATE INDEX idx_taxpayer_identifiers_type_value ON taxpayer_identifiers(identifier_type, identifier_value);
CREATE INDEX idx_tax_returns_taxpayer_period ON tax_returns(taxpayer_id, period_start, period_end);
CREATE INDEX idx_invoices_supplier_date ON invoices(supplier_taxpayer_id, invoice_date);
CREATE INDEX idx_invoices_buyer_date ON invoices(buyer_taxpayer_id, invoice_date);
CREATE INDEX idx_invoices_supplier_pin_date ON invoices(supplier_pin, invoice_date);
CREATE INDEX idx_customs_taxpayer_date ON customs_declarations(taxpayer_id, declaration_date);
CREATE INDEX idx_wht_payee_date ON withholding_certificates(payee_taxpayer_id, certificate_date);
CREATE INDEX idx_payment_transactions_channel_date ON payment_transactions(revenue_channel, payment_date);
CREATE INDEX idx_settlement_records_channel_date ON settlement_records(revenue_channel, settlement_date);
CREATE INDEX idx_risk_signals_taxpayer_period ON risk_signals(taxpayer_id, period_start, period_end);
CREATE INDEX idx_risk_signals_status_severity ON risk_signals(status, severity);
CREATE INDEX idx_cases_status_priority ON cases(status, priority);
CREATE INDEX idx_cases_taxpayer ON cases(taxpayer_id);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs(actor_user_id, created_at);

INSERT INTO roles (code, name, description)
VALUES
    ('ADMIN', 'Administrator', 'Manages platform configuration, users, roles, and system settings.'),
    ('EXECUTIVE', 'Executive', 'Views executive dashboards, revenue gap reports, and recovery summaries.'),
    ('OFFICER', 'Compliance Officer', 'Reviews risk signals, manages cases, and records outcomes.'),
    ('ANALYST', 'Revenue Analyst', 'Reviews data quality, risk models, rules, and analytical outputs.'),
    ('AUDITOR', 'Auditor', 'Reviews audit logs, evidence packs, and governance controls.');

INSERT INTO data_sources (code, name, source_type, owner_agency, ingestion_method, schema_version)
VALUES
    ('ETIMS_SYNTHETIC', 'Synthetic eTIMS Invoices', 'INVOICE', 'Project Demo', 'BATCH_UPLOAD', 'v1'),
    ('ITAX_RETURNS_SYNTHETIC', 'Synthetic iTax Returns', 'TAX_RETURN', 'Project Demo', 'BATCH_UPLOAD', 'v1'),
    ('CUSTOMS_SYNTHETIC', 'Synthetic Customs Declarations', 'CUSTOMS', 'Project Demo', 'BATCH_UPLOAD', 'v1'),
    ('WHT_SYNTHETIC', 'Synthetic Withholding Certificates', 'WITHHOLDING_TAX', 'Project Demo', 'BATCH_UPLOAD', 'v1'),
    ('PAYMENTS_SYNTHETIC', 'Synthetic Government Payments', 'PAYMENT', 'Project Demo', 'BATCH_UPLOAD', 'v1');

INSERT INTO risk_rules (code, name, description, tax_head, rule_type, severity, threshold_config)
VALUES
    (
        'VAT_OUTPUT_MISMATCH',
        'VAT Output Sales Mismatch',
        'Flags taxpayers whose invoice sales materially exceed declared VAT sales.',
        'VAT',
        'DETERMINISTIC',
        'HIGH',
        '{"minimumGap": 100000, "minimumGapPercent": 10}'::jsonb
    ),
    (
        'IMPORT_TO_SALES_MISMATCH',
        'Import to Sales Mismatch',
        'Flags importers whose customs landed cost materially exceeds declared domestic sales.',
        'INCOME_TAX',
        'DETERMINISTIC',
        'HIGH',
        '{"minimumGap": 250000, "minimumGapPercent": 15}'::jsonb
    ),
    (
        'WHT_INCOME_MISMATCH',
        'Withholding Certificate Income Mismatch',
        'Flags payees whose withholding certificate income exceeds declared income.',
        'WITHHOLDING_TAX',
        'DETERMINISTIC',
        'MEDIUM',
        '{"minimumGap": 50000, "minimumGapPercent": 10}'::jsonb
    ),
    (
        'PAYMENT_SETTLEMENT_MISMATCH',
        'Payment Settlement Mismatch',
        'Flags government payment channels where collected amounts do not reconcile to settlements.',
        'REVENUE_ASSURANCE',
        'DETERMINISTIC',
        'HIGH',
        '{"minimumGap": 10000, "settlementDelayDays": 2}'::jsonb
    );
