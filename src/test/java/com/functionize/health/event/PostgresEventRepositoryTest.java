package com.functionize.health.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.postgresql.ds.PGSimpleDataSource;

@Testcontainers(disabledWithoutDocker = true)
class PostgresEventRepositoryTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private PostgresEventRepository repository;

    @BeforeEach
    void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        repository = new PostgresEventRepository(dataSource);
    }

    @Test
    void handlesIdempotentAndConflictingRunIds() {
        var original = event("test-a", "run-1", Status.PASSED, "2026-01-01T00:00:00Z");

        assertEquals(EventRepository.InsertResult.CREATED, repository.insert(original));
        assertEquals(EventRepository.InsertResult.IDENTICAL, repository.insert(original));
        assertEquals(
                EventRepository.InsertResult.CONFLICT,
                repository.insert(event("test-a", "run-1", Status.FAILED, "2026-01-01T00:00:00Z")));
    }

    @Test
    void returnsLatestDecisiveRunsInChronologicalOrder() {
        repository.insert(event("test-a", "old", Status.FAILED, "2026-01-01T00:00:00Z"));
        repository.insert(event("test-a", "new", Status.PASSED, "2026-01-03T00:00:00Z"));
        repository.insert(event("test-a", "skip", Status.SKIPPED, "2026-01-04T00:00:00Z"));
        repository.insert(event("other", "other", Status.PASSED, "2026-01-05T00:00:00Z"));

        var history = repository.latestDecisiveEvents("test-a", 20);

        assertEquals(java.util.List.of("new", "old"), history.stream().map(ExecutionEvent::runId).toList());
    }

    @Test
    void acceptsExactlyOneInsertDuringConcurrentIdenticalRetries() throws Exception {
        var attempts = 12;
        var event = event("test-a", "concurrent-run", Status.PASSED, "2026-01-01T00:00:00Z");
        var ready = new CountDownLatch(attempts);
        var start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<EventRepository.InsertResult>>();
            for (var attempt = 0; attempt < attempts; attempt++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent insert start timed out");
                    }
                    return repository.insert(event);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var results = futures.stream().map(future -> get(future, 10, TimeUnit.SECONDS)).toList();

            assertEquals(1, results.stream().filter(result -> result == EventRepository.InsertResult.CREATED).count());
            assertEquals(
                    attempts - 1,
                    results.stream().filter(result -> result == EventRepository.InsertResult.IDENTICAL).count());
        }
    }

    @Test
    void resolvesConcurrentConflictingRetriesWithoutTransientErrors() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var first = event("test-a", "conflicting-run", Status.PASSED, "2026-01-01T00:00:00Z");
        var second = event("test-a", "conflicting-run", Status.FAILED, "2026-01-01T00:00:00Z");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(
                    executor.submit(() -> insertWhenReleased(first, ready, start)),
                    executor.submit(() -> insertWhenReleased(second, ready, start)));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            var results = futures.stream().map(future -> get(future, 10, TimeUnit.SECONDS)).toList();

            assertEquals(1, results.stream().filter(result -> result == EventRepository.InsertResult.CREATED).count());
            assertEquals(1, results.stream().filter(result -> result == EventRepository.InsertResult.CONFLICT).count());
        }
    }

    private EventRepository.InsertResult insertWhenReleased(
            ExecutionEvent event, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent insert start timed out");
        }
        return repository.insert(event);
    }

    private static <T> T get(Future<T> future, long timeout, TimeUnit unit) {
        try {
            return future.get(timeout, unit);
        } catch (Exception exception) {
            throw new AssertionError("Concurrent operation failed", exception);
        }
    }

    private static ExecutionEvent event(String testId, String runId, Status status, String startedAt) {
        return new ExecutionEvent(testId, runId, status, 100L, Instant.parse(startedAt), null);
    }
}
