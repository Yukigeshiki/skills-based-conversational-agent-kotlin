CREATE TABLE pending_approvals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id VARCHAR(255) NOT NULL,
    thread_id       VARCHAR(255) NOT NULL,
    skill_name      VARCHAR(255) NOT NULL,
    tool_calls      JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_pending_approvals_conversation ON pending_approvals (conversation_id);
