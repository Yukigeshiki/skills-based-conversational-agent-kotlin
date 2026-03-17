CREATE TABLE graph_checkpoints (
    id         BIGSERIAL PRIMARY KEY,
    thread_id  VARCHAR(255) NOT NULL,
    checkpoint_id VARCHAR(255) NOT NULL,
    node_id    VARCHAR(255),
    next_node_id VARCHAR(255),
    state      JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (thread_id, checkpoint_id)
);

CREATE INDEX idx_graph_checkpoints_thread_id ON graph_checkpoints (thread_id);
