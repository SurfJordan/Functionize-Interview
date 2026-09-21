package com.functionize.health.classification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.functionize.health.event.ExecutionEvent;
import com.functionize.health.event.Status;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestClassifierTest {
    private final TestClassifier classifier = new TestClassifier();

    @Test
    void requiresFiveDecisiveRuns() {
        var result = classifier.classify(events(Status.PASSED, Status.PASSED, Status.SKIPPED, Status.FAILED));

        assertEquals(Classification.INSUFFICIENT_DATA, result.classification());
        assertEquals(0.0, result.confidence());
        assertEquals(3, result.evidence().windowSize());
        assertEquals("3 decisive runs available; at least 5 are required.", result.reasoning());
    }

    @Test
    void usesSingularReasoningForOneDecisiveRun() {
        var result = classifier.classify(events(Status.FAILED));

        assertEquals("1 decisive run available; at least 5 are required.", result.reasoning());
    }

    @Test
    void classifiesHealthyAtTenPercentBoundary() {
        var statuses = new ArrayList<Status>();
        statuses.add(Status.FAILED);
        statuses.addAll(java.util.Collections.nCopies(9, Status.PASSED));

        var result = classifier.classify(events(statuses.toArray(Status[]::new)));

        assertEquals(Classification.HEALTHY, result.classification());
        assertEquals(FailureMode.ASSERTION, result.failureMode());
        assertEquals(0.1, result.evidence().nonPassRate());
    }

    @Test
    void classifiesMixedOutcomesAsFlaky() {
        var statuses = new ArrayList<Status>();
        statuses.addAll(java.util.Collections.nCopies(4, Status.FAILED));
        statuses.addAll(java.util.Collections.nCopies(16, Status.PASSED));

        var result = classifier.classify(events(statuses.toArray(Status[]::new)));

        assertEquals(Classification.FLAKY, result.classification());
        assertEquals(FailureMode.ASSERTION, result.failureMode());
    }

    @Test
    void classifiesEightyPercentNonPassingAsBroken() {
        var statuses = new ArrayList<Status>();
        statuses.add(Status.PASSED);
        statuses.addAll(java.util.Collections.nCopies(16, Status.ERRORED));
        statuses.addAll(java.util.Collections.nCopies(3, Status.PASSED));

        var result = classifier.classify(events(statuses.toArray(Status[]::new)));

        assertEquals(Classification.BROKEN, result.classification());
        assertEquals(FailureMode.EXECUTION, result.failureMode());
    }

    @Test
    void recentFiveRunRegressionOverridesWindowRate() {
        var statuses = new ArrayList<Status>();
        statuses.addAll(java.util.Collections.nCopies(5, Status.FAILED));
        statuses.addAll(java.util.Collections.nCopies(15, Status.PASSED));

        var result = classifier.classify(events(statuses.toArray(Status[]::new)));

        assertEquals(Classification.BROKEN, result.classification());
        assertEquals(5, result.evidence().consecutiveNonPasses());
        assertTrue(result.reasoning().contains("latest 5"));
    }

    @Test
    void ignoresSkippedRunsAndLimitsWindow() {
        var statuses = new ArrayList<Status>();
        statuses.add(Status.SKIPPED);
        statuses.addAll(java.util.Collections.nCopies(20, Status.PASSED));
        statuses.addAll(java.util.Collections.nCopies(5, Status.FAILED));

        var result = classifier.classify(events(statuses.toArray(Status[]::new)));

        assertEquals(Classification.HEALTHY, result.classification());
        assertEquals(20, result.evidence().windowSize());
        assertEquals(0, result.evidence().failed());
    }

    @Test
    void reportsMixedFailureMode() {
        var result = classifier.classify(events(
                Status.FAILED, Status.ERRORED, Status.PASSED, Status.PASSED, Status.PASSED));

        assertEquals(FailureMode.MIXED, result.failureMode());
        assertEquals(Classification.FLAKY, result.classification());
    }

    @Test
    void confidenceGrowsWithEquivalentLargerSample() {
        var five = classifier.classify(events(
                Status.PASSED, Status.PASSED, Status.PASSED, Status.PASSED, Status.PASSED));
        var twenty = classifier.classify(events(java.util.Collections.nCopies(20, Status.PASSED).toArray(Status[]::new)));

        assertTrue(twenty.confidence() > five.confidence());
        assertTrue(twenty.evidence().wilsonInterval().upper() < five.evidence().wilsonInterval().upper());
    }

    private static List<ExecutionEvent> events(Status... statuses) {
        var events = new ArrayList<ExecutionEvent>();
        for (var index = 0; index < statuses.length; index++) {
            events.add(new ExecutionEvent(
                    "test", "run-" + index, statuses[index], 100L, Instant.EPOCH.minusSeconds(index), null));
        }
        return events;
    }
}
