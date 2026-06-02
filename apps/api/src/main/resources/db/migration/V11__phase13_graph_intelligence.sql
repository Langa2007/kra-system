CREATE OR REPLACE FUNCTION stable_uuid(value TEXT)
RETURNS UUID
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT (
        substr(md5(value), 1, 8) || '-' ||
        substr(md5(value), 9, 4) || '-' ||
        substr(md5(value), 13, 4) || '-' ||
        substr(md5(value), 17, 4) || '-' ||
        substr(md5(value), 21, 12)
    )::uuid
$$;

CREATE TABLE graph_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type VARCHAR(80) NOT NULL,
    source_id UUID NOT NULL,
    target_type VARCHAR(80) NOT NULL,
    target_id UUID NOT NULL,
    edge_type VARCHAR(80) NOT NULL,
    weight NUMERIC(8,4) NOT NULL DEFAULT 1,
    source VARCHAR(120),
    evidence JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_type, source_id, target_type, target_id, edge_type, source)
);

CREATE INDEX idx_graph_edges_source
    ON graph_edges(source_type, source_id, edge_type);

CREATE INDEX idx_graph_edges_target
    ON graph_edges(target_type, target_id, edge_type);

CREATE INDEX idx_graph_edges_type_weight
    ON graph_edges(edge_type, weight DESC);
