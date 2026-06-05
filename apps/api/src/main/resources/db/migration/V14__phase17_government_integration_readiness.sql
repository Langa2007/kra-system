CREATE TABLE source_schema_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source_id UUID NOT NULL REFERENCES data_sources(id),
    source_schema JSONB NOT NULL,
    target_entity VARCHAR(120) NOT NULL,
    mapping_config JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_source_schema_mappings_source
    ON source_schema_mappings(data_source_id, target_entity, active);

ALTER TABLE data_sources
    ADD COLUMN connection_profile JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN expected_freshness_minutes INTEGER NOT NULL DEFAULT 1440,
    ADD COLUMN raw_archive_bucket VARCHAR(160),
    ADD COLUMN last_successful_ingestion_at TIMESTAMPTZ,
    ADD COLUMN integration_status VARCHAR(40) NOT NULL DEFAULT 'NOT_CONFIGURED';

ALTER TABLE ingestion_jobs
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN max_retries INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN next_retry_at TIMESTAMPTZ;

CREATE INDEX idx_data_sources_integration_status
    ON data_sources(integration_status, active);

CREATE INDEX idx_ingestion_jobs_retry
    ON ingestion_jobs(status, next_retry_at);
