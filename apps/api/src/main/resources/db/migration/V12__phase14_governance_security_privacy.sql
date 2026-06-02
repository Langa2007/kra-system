CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(120) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE data_retention_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_category VARCHAR(120) NOT NULL UNIQUE,
    retention_days INTEGER NOT NULL,
    policy_reason TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE privacy_impact_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_category VARCHAR(120) NOT NULL UNIQUE,
    purpose TEXT NOT NULL,
    lawful_basis TEXT NOT NULL,
    data_minimization_note TEXT NOT NULL,
    masking_required BOOLEAN NOT NULL DEFAULT TRUE,
    completed BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);
CREATE INDEX idx_data_retention_policies_active ON data_retention_policies(active, data_category);
CREATE INDEX idx_privacy_impact_items_completed ON privacy_impact_items(completed, data_category);

INSERT INTO permissions (code, description)
VALUES
    ('ADMIN_MANAGE_ROLES', 'Create and manage platform roles for pilot users.'),
    ('ADMIN_MANAGE_PERMISSIONS', 'Assign explicit permissions to roles.'),
    ('ADMIN_DATA_SOURCES', 'Administer data-source configuration and ownership metadata.'),
    ('ADMIN_RULES', 'Administer deterministic revenue-risk rules.'),
    ('ADMIN_MODELS', 'Administer model versions and activation state.'),
    ('AUDIT_LOG_VIEW', 'View immutable audit logs and sensitive access history.'),
    ('DATA_RETENTION_MANAGE', 'Manage retention windows by data category.'),
    ('EXPORT_SENSITIVE_DATA', 'Approve or run sensitive bulk exports.'),
    ('PRIVACY_IMPACT_REVIEW', 'Review privacy impact checklist items.'),
    ('KEYCLOAK_MFA_CONFIGURE', 'Configure the Keycloak MFA path for pilot and enterprise deployments.')
ON CONFLICT (code) DO UPDATE SET description = EXCLUDED.description;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'ADMIN_MANAGE_ROLES',
    'ADMIN_MANAGE_PERMISSIONS',
    'ADMIN_DATA_SOURCES',
    'ADMIN_RULES',
    'ADMIN_MODELS',
    'AUDIT_LOG_VIEW',
    'DATA_RETENTION_MANAGE',
    'EXPORT_SENSITIVE_DATA',
    'PRIVACY_IMPACT_REVIEW',
    'KEYCLOAK_MFA_CONFIGURE'
)
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('AUDIT_LOG_VIEW', 'EXPORT_SENSITIVE_DATA', 'PRIVACY_IMPACT_REVIEW')
WHERE r.code = 'AUDITOR'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('ADMIN_RULES', 'ADMIN_MODELS')
WHERE r.code = 'ANALYST'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('ADMIN_DATA_SOURCES')
WHERE r.code = 'OFFICER'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('AUDIT_LOG_VIEW')
WHERE r.code = 'EXECUTIVE'
ON CONFLICT DO NOTHING;

INSERT INTO data_retention_policies (data_category, retention_days, policy_reason, active)
VALUES
    ('taxpayers', 2555, 'Keep taxpayer master records for compliance history during pilot review.', TRUE),
    ('invoices', 2555, 'Retain invoice evidence long enough to support VAT and income-tax review.', TRUE),
    ('returns', 2555, 'Retain declared return facts for statutory comparison and recovery workflows.', TRUE),
    ('customs', 2555, 'Retain import declarations for cross-source mismatch analytics.', TRUE),
    ('payments', 2190, 'Retain payment and settlement records for reconciliation and assurance.', TRUE),
    ('settlements', 2190, 'Retain treasury/bank settlement evidence for public finance assurance.', TRUE),
    ('cases', 3650, 'Retain investigation workflow records for accountability and appeals.', TRUE),
    ('evidence', 3650, 'Retain generated evidence packs for audit traceability.', TRUE),
    ('notifications', 1095, 'Retain nudges and taxpayer responses for voluntary-compliance tracking.', TRUE),
    ('audit_logs', 3650, 'Retain immutable access and action logs for governance review.', TRUE),
    ('model_predictions', 1095, 'Retain model outputs for explainability, monitoring, and challenge handling.', TRUE),
    ('graph_edges', 1095, 'Retain relationship intelligence while pilot risk clusters remain reviewable.', TRUE)
ON CONFLICT (data_category) DO UPDATE SET
    retention_days = EXCLUDED.retention_days,
    policy_reason = EXCLUDED.policy_reason,
    active = EXCLUDED.active;

INSERT INTO privacy_impact_items (
    data_category,
    purpose,
    lawful_basis,
    data_minimization_note,
    masking_required,
    completed
)
VALUES
    ('taxpayers', 'Taxpayer identity resolution and compliance prioritization.', 'Public revenue administration mandate.', 'Store only identifiers required for matching and case workflows.', TRUE, TRUE),
    ('invoices', 'VAT and income-tax mismatch detection.', 'Public revenue administration mandate.', 'Use invoice totals and references needed for explainable evidence.', TRUE, TRUE),
    ('returns', 'Declared-vs-observed tax comparison.', 'Public revenue administration mandate.', 'Use declared tax fields required by risk rules.', TRUE, TRUE),
    ('customs', 'Import-to-sales and duty assurance analytics.', 'Public revenue administration mandate.', 'Use declaration values and commodity metadata needed for matching.', TRUE, TRUE),
    ('payments', 'Collection assurance and settlement reconciliation.', 'Public finance assurance mandate.', 'Use transaction values, dates, channels, and account references only.', TRUE, TRUE),
    ('settlements', 'Treasury/bank reconciliation and exception detection.', 'Public finance assurance mandate.', 'Keep settlement references and balances needed for reconciliation.', TRUE, TRUE),
    ('cases', 'Officer workflow, accountability, and recovery tracking.', 'Public revenue administration mandate.', 'Store case facts and officer actions relevant to review.', TRUE, TRUE),
    ('evidence', 'Explainable evidence packs for serious risk cases.', 'Public revenue administration mandate.', 'Package only source facts used by the case rationale.', TRUE, TRUE),
    ('notifications', 'Voluntary compliance nudges and response handling.', 'Public revenue administration mandate.', 'Retain recipient and response data only for compliance follow-up.', TRUE, TRUE),
    ('audit_logs', 'Security, accountability, and sensitive access monitoring.', 'Governance and security obligation.', 'Record actor, action, time, and request context without excess payload.', FALSE, TRUE),
    ('model_predictions', 'Explainable model-assisted prioritization.', 'Public revenue administration mandate with human review.', 'Store model score and explanation features needed for review.', TRUE, TRUE),
    ('graph_edges', 'Relationship intelligence and linked-risk detection.', 'Public revenue administration mandate.', 'Store relationship edges with evidence source and confidence only.', TRUE, TRUE)
ON CONFLICT (data_category) DO UPDATE SET
    purpose = EXCLUDED.purpose,
    lawful_basis = EXCLUDED.lawful_basis,
    data_minimization_note = EXCLUDED.data_minimization_note,
    masking_required = EXCLUDED.masking_required,
    completed = EXCLUDED.completed;
