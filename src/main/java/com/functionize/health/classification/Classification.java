package com.functionize.health.classification;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Classification {
    INSUFFICIENT_DATA("insufficient_data"),
    HEALTHY("healthy"),
    FLAKY("flaky"),
    BROKEN("broken");

    private final String wireValue;

    Classification(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}

