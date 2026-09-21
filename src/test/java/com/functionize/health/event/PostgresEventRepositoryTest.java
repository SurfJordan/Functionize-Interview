package com.functionize.health.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
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

    private static ExecutionEvent event(String testId, String runId, Status status, String startedAt) {
        return new ExecutionEvent(testId, runId, status, 100L, Instant.parse(startedAt), null);
    }
}
