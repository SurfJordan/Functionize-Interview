package com.functionize.health.classification;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClassificationResult(
        Classification classification,
        @JsonProperty("failure_mode") FailureMode failureMode,
        double confidence,
        String reasoning,
        Evidence evidence) {

    public record Evidence(
            @JsonProperty("window_size") int windowSize,
            int passed,
            int failed,
            int errored,
            @JsonProperty("non_pass_rate") double nonPassRate,
            @JsonProperty("consecutive_non_passes") int consecutiveNonPasses,
            @JsonProperty("wilson_interval") WilsonInterval wilsonInterval) {}

    public record WilsonInterval(double lower, double upper) {}
}

