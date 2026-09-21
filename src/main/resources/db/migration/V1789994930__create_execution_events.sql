CREATE TABLE execution_events (
    run_id VARCHAR(255) PRIMARY KEY,
    test_id VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('passed', 'failed', 'skipped', 'errored')),
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    started_at TIMESTAMPTZ NOT NULL,
    error_message TEXT,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX execution_events_test_history_idx
    ON execution_events (test_id, started_at DESC, run_id DESC)
    WHERE status <> 'skipped';
