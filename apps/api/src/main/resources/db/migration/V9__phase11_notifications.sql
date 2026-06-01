CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(120) NOT NULL UNIQUE,
    channel VARCHAR(40) NOT NULL,
    subject_template VARCHAR(240),
    body_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_taxpayer_id_fkey,
    DROP CONSTRAINT IF EXISTS notifications_case_id_fkey,
    ADD CONSTRAINT notifications_taxpayer_id_fkey
        FOREIGN KEY (taxpayer_id) REFERENCES taxpayers(id) ON DELETE SET NULL,
    ADD CONSTRAINT notifications_case_id_fkey
        FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL;

ALTER TABLE notifications
    ADD COLUMN risk_signal_id UUID REFERENCES risk_signals(id) ON DELETE SET NULL,
    ADD COLUMN delivery_provider VARCHAR(120),
    ADD COLUMN delivery_reference VARCHAR(160),
    ADD COLUMN response_status VARCHAR(60),
    ADD COLUMN response_body TEXT,
    ADD COLUMN responded_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE INDEX idx_notifications_case_created
    ON notifications(case_id, created_at DESC);

CREATE INDEX idx_notifications_risk_signal_created
    ON notifications(risk_signal_id, created_at DESC);

CREATE INDEX idx_notifications_taxpayer_created
    ON notifications(taxpayer_id, created_at DESC);

CREATE INDEX idx_notification_templates_active
    ON notification_templates(active, channel, code);

INSERT INTO notification_templates (code, channel, subject_template, body_template)
VALUES
    (
        'SOFT_COMPLIANCE_EMAIL',
        'EMAIL',
        'Voluntary correction request for {{taxpayerName}}',
        'Dear {{taxpayerName}}, our review identified a {{ruleCode}} concern for {{periodStart}} to {{periodEnd}} with an estimated variance of KES {{estimatedGap}}. Please review your records and provide a correction or explanation through your officer before formal enforcement begins.'
    ),
    (
        'SOFT_COMPLIANCE_SMS',
        'SMS',
        NULL,
        'KRA notice: Please review {{ruleCode}} for {{periodStart}} to {{periodEnd}}. Estimated variance KES {{estimatedGap}}. Contact your officer with correction or explanation.'
    ),
    (
        'CASE_FOLLOW_UP_EMAIL',
        'EMAIL',
        'Case {{caseNumber}} voluntary compliance follow-up',
        'Dear {{taxpayerName}}, case {{caseNumber}} is open for {{ruleCode}}. You may submit supporting records, a corrected return, or an explanation for officer review. This message supports voluntary correction before escalation.'
    )
ON CONFLICT (code) DO NOTHING;
