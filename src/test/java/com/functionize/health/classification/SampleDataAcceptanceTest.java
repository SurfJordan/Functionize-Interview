package com.functionize.health.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.functionize.health.event.ExecutionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SampleDataAcceptanceTest {
    @Test
    void classifiesTheSuppliedDatasetWithTheDocumentedPolicy() throws Exception {
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        var byTest = new HashMap<String, ArrayList<ExecutionEvent>>();
        try (var lines = Files.lines(Path.of("data/sample_data.jsonl"))) {
            for (var line : lines.toList()) {
                var event = mapper.readValue(line, ExecutionEvent.class);
                byTest.computeIfAbsent(event.testId(), ignored -> new ArrayList<>()).add(event);
            }
        }

        var classifier = new TestClassifier();
        var classifications = new HashMap<String, Classification>();
        byTest.forEach((testId, events) -> {
            events.sort(Comparator.comparing(ExecutionEvent::startedAt).reversed()
                    .thenComparing(ExecutionEvent::runId, Comparator.reverseOrder()));
            classifications.put(testId, classifier.classify(events).classification());
        });

        assertEquals(100, classifications.size());
        assertEquals(
                Map.of(Classification.HEALTHY, 62L, Classification.FLAKY, 24L, Classification.BROKEN, 14L),
                classifications.values().stream()
                        .collect(java.util.stream.Collectors.groupingBy(value -> value, java.util.stream.Collectors.counting())));
        assertEquals(Classification.HEALTHY, classifications.get("billing.with_special_chars"));
        assertEquals(Classification.FLAKY, classifications.get("signup.concurrent_users"));
        assertEquals(Classification.BROKEN, classifications.get("two_factor.rate_limited"));
    }
}

