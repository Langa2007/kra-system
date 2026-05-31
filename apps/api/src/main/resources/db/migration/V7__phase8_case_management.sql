ALTER TABLE cases
    ADD COLUMN risk_signal_id UUID REFERENCES risk_signals(id);

CREATE INDEX idx_cases_risk_signal
    ON cases(risk_signal_id);

CREATE INDEX idx_case_events_case_created
    ON case_events(case_id, created_at);

CREATE INDEX idx_evidence_packs_case_version
    ON evidence_packs(case_id, version DESC);

CREATE INDEX idx_recovery_records_case_created
    ON recovery_records(case_id, created_at);
