CREATE TABLE tax_gap_estimates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deterministic_key VARCHAR(360) NOT NULL UNIQUE,
    taxpayer_id UUID NOT NULL REFERENCES taxpayers(id),
    tax_head VARCHAR(80) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    declared_amount NUMERIC(18,2),
    observed_amount NUMERIC(18,2),
    estimated_gap NUMERIC(18,2) NOT NULL,
    estimated_recoverable_tax NUMERIC(18,2),
    estimated_penalty NUMERIC(18,2),
    estimated_interest NUMERIC(18,2),
    estimated_total_due NUMERIC(18,2),
    confidence_score NUMERIC(5,2) NOT NULL,
    evidence JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tax_gap_estimates_taxpayer_period
    ON tax_gap_estimates(taxpayer_id, period_start, period_end);

CREATE INDEX idx_tax_gap_estimates_tax_head_period
    ON tax_gap_estimates(tax_head, period_start, period_end);

CREATE INDEX idx_tax_gap_estimates_recoverable
    ON tax_gap_estimates(estimated_recoverable_tax DESC, confidence_score DESC);

CREATE INDEX idx_risk_scores_taxpayer_period
    ON risk_scores(taxpayer_id, scoring_period_start, scoring_period_end);
