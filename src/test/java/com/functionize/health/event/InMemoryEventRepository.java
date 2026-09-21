package com.functionize.health.event;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryEventRepository implements EventRepository {
    private final ConcurrentHashMap<String, ExecutionEvent> events = new ConcurrentHashMap<>();

    @Override
    public InsertResult insert(ExecutionEvent event) {
        var existing = events.putIfAbsent(event.runId(), event);
        if (existing == null) {
            return InsertResult.CREATED;
        }
        return existing.equals(event) ? InsertResult.IDENTICAL : InsertResult.CONFLICT;
    }

    @Override
    public List<ExecutionEvent> latestDecisiveEvents(String testId, int limit) {
        return events.values().stream()
                .filter(event -> event.testId().equals(testId) && event.status() != Status.SKIPPED)
                .sorted(Comparator.comparing(ExecutionEvent::startedAt)
                        .thenComparing(ExecutionEvent::runId)
                        .reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public boolean existsForTest(String testId) {
        return events.values().stream().anyMatch(event -> event.testId().equals(testId));
    }
}

