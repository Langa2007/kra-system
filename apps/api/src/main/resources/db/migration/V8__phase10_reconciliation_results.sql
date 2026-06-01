CREATE TABLE reconciliation_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deterministic_key VARCHAR(360) NOT NULL UNIQUE,
    reconciliation_date DATE NOT NULL,
    collecting_agency VARCHAR(160) NOT NULL,
    revenue_channel VARCHAR(120) NOT NULL,
    expected_amount NUMERIC(18,2) NOT NULL,
    settled_amount NUMERIC(18,2) NOT NULL,
    variance_amount NUMERIC(18,2) NOT NULL,
    transaction_count INTEGER,
    settlement_count INTEGER,
    settlement_status VARCHAR(60) NOT NULL,
    expected_settlement_account VARCHAR(160),
    settlement_account VARCHAR(160),
    max_settlement_lag_days INTEGER,
    evidence JSONB NOT NULL,
    risk_signal_id UUID REFERENCES risk_signals(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_reconciliation_results_date_channel
    ON reconciliation_results(reconciliation_date DESC, collecting_agency, revenue_channel);

CREATE INDEX idx_reconciliation_results_status
    ON reconciliation_results(settlement_status, reconciliation_date DESC);

CREATE INDEX idx_reconciliation_results_signal
    ON reconciliation_results(risk_signal_id);
