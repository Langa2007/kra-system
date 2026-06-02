CREATE INDEX idx_model_versions_active
    ON model_versions(active, created_at DESC);

CREATE INDEX idx_model_predictions_taxpayer_created
    ON model_predictions(taxpayer_id, created_at DESC);

CREATE INDEX idx_model_predictions_model_score
    ON model_predictions(model_version_id, score DESC);
