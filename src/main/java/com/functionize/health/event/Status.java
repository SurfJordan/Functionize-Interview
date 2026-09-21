package com.functionize.health.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
    PASSED("passed"),
    FAILED("failed"),
    SKIPPED("skipped"),
    ERRORED("errored");

    private final String wireValue;

    Status(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static Status fromWireValue(String value) {
        for (var status : values()) {
            if (status.wireValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("status must be one of: passed, failed, skipped, errored");
    }
}

