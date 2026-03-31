CREATE TABLE http_tools (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(64) NOT NULL UNIQUE,
    description         VARCHAR(1000) NOT NULL,
    endpoint_url        VARCHAR(2048) NOT NULL,
    http_method         VARCHAR(10) NOT NULL DEFAULT 'GET',
    headers             JSONB NOT NULL DEFAULT '{}',
    parameters          JSONB NOT NULL DEFAULT '[]',
    timeout_seconds     INTEGER NOT NULL DEFAULT 30,
    max_response_length INTEGER NOT NULL DEFAULT 8000,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
