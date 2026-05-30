CREATE TABLE auth_credentials (
    app_user_id UUID PRIMARY KEY REFERENCES app_users(id),
    password_hash VARCHAR(200) NOT NULL,
    password_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_app_users_status ON app_users(status);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_audit_logs_action_created ON audit_logs(action, created_at);
