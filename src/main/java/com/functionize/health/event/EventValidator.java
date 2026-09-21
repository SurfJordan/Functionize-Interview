package com.functionize.health.event;

import java.util.ArrayList;
import java.util.List;

public final class EventValidator {
    private static final int MAX_IDENTIFIER_LENGTH = 255;
    private static final int MAX_ERROR_LENGTH = 16 * 1024;

    public List<String> validate(ExecutionEvent event) {
        if (event == null) {
            return List.of("request body must contain an event");
        }

        var errors = new ArrayList<String>();
        validateTestId(event.testId(), errors);
        validateIdentifier("run_id", event.runId(), errors);
        if (event.status() == null) {
            errors.add("status is required");
        }
        if (event.durationMs() == null) {
            errors.add("duration_ms is required");
        } else if (event.durationMs() < 0) {
            errors.add("duration_ms must be nonnegative");
        }
        if (event.startedAt() == null) {
            errors.add("started_at is required and must be an RFC 3339 timestamp");
        }
        if (event.errorMessage() != null && event.errorMessage().length() > MAX_ERROR_LENGTH) {
            errors.add("error_message must not exceed 16384 characters");
        }
        return List.copyOf(errors);
    }

    private static void validateTestId(String value, List<String> errors) {
        validateIdentifier("test_id", value, errors);
        if (value != null && value.contains("/")) {
            errors.add("test_id must not contain '/'");
        }
    }

    private static void validateIdentifier(String field, String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
        } else if (value.length() > MAX_IDENTIFIER_LENGTH) {
            errors.add(field + " must not exceed 255 characters");
        }
    }
}
