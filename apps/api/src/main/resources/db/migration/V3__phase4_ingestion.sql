ALTER TABLE ingestion_jobs
    ADD COLUMN target_table VARCHAR(120),
    ADD COLUMN file_sha256 VARCHAR(64);

ALTER TABLE data_quality_issues
    ADD COLUMN record_payload JSONB;

CREATE INDEX idx_ingestion_jobs_file_hash ON ingestion_jobs(data_source_id, target_table, file_sha256);
CREATE INDEX idx_invoices_source_job ON invoices(source_job_id);
CREATE INDEX idx_tax_returns_source_job ON tax_returns(source_job_id);
