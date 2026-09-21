package com.functionize.health.classification;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FailureMode {
    NONE("none"),
    ASSERTION("assertion"),
    EXECUTION("execution"),
    MIXED("mixed");

    private final String wireValue;

    FailureMode(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}

