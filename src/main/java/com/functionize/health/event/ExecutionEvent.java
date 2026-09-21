package com.functionize.health.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ExecutionEvent(
        @JsonProperty("test_id") String testId,
        @JsonProperty("run_id") String runId,
        Status status,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("error_message") String errorMessage) {}

