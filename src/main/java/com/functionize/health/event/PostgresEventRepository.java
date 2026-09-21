package com.functionize.health.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public final class PostgresEventRepository implements EventRepository {
    private static final String INSERT_SQL = """
            INSERT INTO execution_events (run_id, test_id, status, duration_ms, started_at, error_message)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (run_id) DO NOTHING
            """;
    private static final String FIND_RUN_SQL = """
            SELECT run_id, test_id, status, duration_ms, started_at, error_message
            FROM execution_events WHERE run_id = ?
            """;
    private static final String HISTORY_SQL = """
            SELECT run_id, test_id, status, duration_ms, started_at, error_message
            FROM execution_events
            WHERE test_id = ? AND status <> 'skipped'
            ORDER BY started_at DESC, run_id DESC
            LIMIT ?
            """;
    private static final String EXISTS_SQL = "SELECT 1 FROM execution_events WHERE test_id = ? LIMIT 1";

    private final DataSource dataSource;

    public PostgresEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public InsertResult insert(ExecutionEvent event) {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement(INSERT_SQL)) {
                bind(statement, event);
                if (statement.executeUpdate() == 1) {
                    return InsertResult.CREATED;
                }
            }

            try (var statement = connection.prepareStatement(FIND_RUN_SQL)) {
                statement.setString(1, event.runId());
                try (var resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new RepositoryException("Event disappeared during duplicate resolution");
                    }
                    return event.equals(readEvent(resultSet)) ? InsertResult.IDENTICAL : InsertResult.CONFLICT;
                }
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not persist event", exception);
        }
    }

    @Override
    public List<ExecutionEvent> latestDecisiveEvents(String testId, int limit) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(HISTORY_SQL)) {
            statement.setString(1, testId);
            statement.setInt(2, limit);
            try (var resultSet = statement.executeQuery()) {
                var events = new ArrayList<ExecutionEvent>();
                while (resultSet.next()) {
                    events.add(readEvent(resultSet));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not load test history", exception);
        }
    }

    @Override
    public boolean existsForTest(String testId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(EXISTS_SQL)) {
            statement.setString(1, testId);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not check test history", exception);
        }
    }

    private static void bind(java.sql.PreparedStatement statement, ExecutionEvent event) throws SQLException {
        statement.setString(1, event.runId());
        statement.setString(2, event.testId());
        statement.setString(3, event.status().wireValue());
        statement.setLong(4, event.durationMs());
        statement.setTimestamp(5, Timestamp.from(event.startedAt()));
        statement.setString(6, event.errorMessage());
    }

    private static ExecutionEvent readEvent(ResultSet resultSet) throws SQLException {
        return new ExecutionEvent(
                resultSet.getString("test_id"),
                resultSet.getString("run_id"),
                Status.fromWireValue(resultSet.getString("status")),
                resultSet.getLong("duration_ms"),
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getString("error_message"));
    }
}

