package com.functionize.health.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.functionize.health.classification.TestClassifier;
import com.functionize.health.event.EventRepository;
import com.functionize.health.event.EventService;
import com.functionize.health.event.EventValidator;
import com.functionize.health.event.ExecutionEvent;
import com.functionize.health.event.InMemoryEventRepository;
import com.functionize.health.event.RepositoryException;
import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpServerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private Javalin app;
    private String baseUrl;

    @BeforeEach
    void startServer() {
        var service = new EventService(new InMemoryEventRepository(), new EventValidator(), new TestClassifier());
        app = HttpServer.create(new EventController(service));
        app.start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterEach
    void stopServer() {
        app.stop();
    }

    @Test
    void ingestsThenClassifiesAnEvent() throws Exception {
        var created = post(eventJson("run-1", "passed", 100));

        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"run_id\":\"run-1\""));
        assertEquals("/tests/example", created.headers().firstValue("Location").orElseThrow());

        var health = get("/tests/example");
        assertEquals(200, health.statusCode());
        assertTrue(health.body().contains("\"classification\":\"insufficient_data\""));
        assertTrue(health.body().contains("\"failure_mode\":\"none\""));
    }

    @Test
    void treatsIdenticalReplayAsDuplicateAndDifferentReplayAsConflict() throws Exception {
        assertEquals(201, post(eventJson("run-1", "passed", 100)).statusCode());

        var duplicate = post(eventJson("run-1", "passed", 100));
        var conflict = post(eventJson("run-1", "failed", 100));

        assertEquals(200, duplicate.statusCode());
        assertTrue(duplicate.body().contains("\"result\":\"duplicate\""));
        assertEquals(409, conflict.statusCode());
        assertTrue(conflict.body().contains("\"code\":\"run_id_conflict\""));
    }

    @Test
    void normalizesTimestampPrecisionForIdempotentReplays() throws Exception {
        var event = eventJson("run-nanos", "passed", 100)
                .replace("2026-04-12T14:02:11Z", "2026-04-12T14:02:11.123456789Z");

        assertEquals(201, post(event).statusCode());
        assertEquals(200, post(event).statusCode());
    }

    @Test
    void encodesTheTestIdInTheLocationHeader() throws Exception {
        var event = eventJson("run-location", "passed", 100).replace("example", "checkout flow");

        var response = post(event);

        assertEquals(201, response.statusCode());
        assertEquals("/tests/checkout%20flow", response.headers().firstValue("Location").orElseThrow());
    }

    @Test
    void rejectsInvalidAndUnknownFields() throws Exception {
        var negativeDuration = post(eventJson("run-1", "passed", -1));
        var unknownField = post(eventJson("run-2", "passed", 100).replace("}", ",\"surprise\":true}"));

        assertEquals(400, negativeDuration.statusCode());
        assertTrue(negativeDuration.body().contains("duration_ms must be nonnegative"));
        assertEquals(400, unknownField.statusCode());
        assertTrue(unknownField.body().contains("invalid_json"), unknownField.body());
    }

    @Test
    void returnsStructuredNotFoundResponse() throws Exception {
        var response = get("/tests/unknown");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"test_not_found\""), response.body());
    }

    @Test
    void mapsStorageFailuresToServiceUnavailable() throws Exception {
        app.stop();
        var failingRepository = new EventRepository() {
            @Override
            public InsertResult insert(ExecutionEvent event) {
                throw new RepositoryException("sensitive database detail");
            }

            @Override
            public List<ExecutionEvent> latestDecisiveEvents(String testId, int limit) {
                throw new RepositoryException("sensitive database detail");
            }

            @Override
            public boolean existsForTest(String testId) {
                throw new RepositoryException("sensitive database detail");
            }
        };
        var service = new EventService(failingRepository, new EventValidator(), new TestClassifier());
        app = HttpServer.create(new EventController(service));
        app.start(0);
        baseUrl = "http://localhost:" + app.port();

        var response = get("/tests/example");

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"service_unavailable\""));
        assertTrue(!response.body().contains("sensitive database detail"));
    }

    private HttpResponse<String> post(String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/events"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String eventJson(String runId, String status, long duration) {
        return """
                {
                  "test_id": "example",
                  "run_id": "%s",
                  "status": "%s",
                  "duration_ms": %d,
                  "started_at": "2026-04-12T14:02:11Z"
                }
                """.formatted(runId, status, duration);
    }
}
