package com.functionize.health.event;

import com.functionize.health.classification.ClassificationResult;
import com.functionize.health.classification.TestClassifier;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class EventService {
    private final EventRepository repository;
    private final EventValidator validator;
    private final TestClassifier classifier;

    public EventService(EventRepository repository, EventValidator validator, TestClassifier classifier) {
        this.repository = repository;
        this.validator = validator;
        this.classifier = classifier;
    }

    public IngestResult ingest(ExecutionEvent event) {
        var errors = validator.validate(event);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        var canonicalEvent = new ExecutionEvent(
                event.testId(),
                event.runId(),
                event.status(),
                event.durationMs(),
                event.startedAt().truncatedTo(ChronoUnit.MICROS),
                event.errorMessage());

        return switch (repository.insert(canonicalEvent)) {
            case CREATED -> IngestResult.CREATED;
            case IDENTICAL -> IngestResult.DUPLICATE;
            case CONFLICT -> throw new ConflictingRunException(event.runId());
        };
    }

    public TestHealth getTestHealth(String testId) {
        var events = repository.latestDecisiveEvents(testId, TestClassifier.WINDOW_SIZE);
        if (events.isEmpty() && !repository.existsForTest(testId)) {
            throw new TestNotFoundException(testId);
        }
        return new TestHealth(testId, classifier.classify(events));
    }

    public record TestHealth(String testId, ClassificationResult result) {}

    @SuppressWarnings("serial")
    public static final class ValidationException extends RuntimeException {
        private final List<String> errors;

        public ValidationException(List<String> errors) {
            super("Event validation failed");
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }

    @SuppressWarnings("serial")
    public static final class ConflictingRunException extends RuntimeException {
        public ConflictingRunException(String runId) {
            super("run_id already exists with different event data: " + runId);
        }
    }

    @SuppressWarnings("serial")
    public static final class TestNotFoundException extends RuntimeException {
        public TestNotFoundException(String testId) {
            super("No events found for test_id: " + testId);
        }
    }
}
