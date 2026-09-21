package com.functionize.health.event;

import java.util.List;

public interface EventRepository {
    InsertResult insert(ExecutionEvent event);

    List<ExecutionEvent> latestDecisiveEvents(String testId, int limit);

    boolean existsForTest(String testId);

    enum InsertResult {
        CREATED,
        IDENTICAL,
        CONFLICT
    }
}

