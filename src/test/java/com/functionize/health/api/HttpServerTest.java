package com.functionize.health.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void oneFailureBecomesFlakyOnlyAfterEnoughPassingEvidence() throws Exception {
        assertEquals(201, post(eventJson("run-1", "failed", 100)).statusCode());

        var insufficient = new ObjectMapper().readTree(get("/tests/example").body());
        assertEquals("insufficient_data", insufficient.path("classification").asText());
        assertEquals("1 decisive run available; at least 5 are required.", insufficient.path("reasoning").asText());

        for (var run = 2; run <= 5; run++) {
            assertEquals(201, post(eventJson("run-" + run, "passed", 100)).statusCode());
        }

        var flaky = new ObjectMapper().readTree(get("/tests/example").body());
        assertEquals("flaky", flaky.path("classification").asText());
        assertEquals("assertion", flaky.path("failure_mode").asText());
        assertEquals(5, flaky.path("evidence").path("window_size").asInt());
        assertEquals(0.2, flaky.path("evidence").path("non_pass_rate").asDouble());
    }

    @Test
    void servesInteractiveApiDocumentationFromTheRootShortcut() throws Exception {
        var root = get("/");
        var specification = get("/openapi.json");
        var documentation = get("/docs");

        assertEquals(302, root.statusCode());
        assertEquals("/docs", root.headers().firstValue("Location").orElseThrow());
        assertEquals(200, specification.statusCode());
        assertTrue(specification.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        var openApi = new ObjectMapper().readTree(specification.body());
        assertEquals("3.1.0", openApi.path("openapi").asText());
        assertTrue(openApi.at("/paths/~1events/post/responses/201").isObject());
        assertTrue(openApi.at("/paths/~1tests~1{test_id}/get/responses/200").isObject());
        assertEquals(200, documentation.statusCode());
        assertTrue(documentation.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
        assertTrue(documentation.body().contains("/openapi.json?v=default"));
        assertEquals(200, get("/webjars/swagger-ui/5.31.2/swagger-ui.css").statusCode());
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
    void acceptedTestIdsRoundTripThroughTheLocationHeader() throws Exception {
        var testIds = List.of("checkout flow", "a+b", "100%", "snow_雪");
        for (var index = 0; index < testIds.size(); index++) {
            var testId = testIds.get(index);
            var event = eventJson("run-location-" + index, "passed", 100).replace("example", testId);

            var response = post(event);

            assertEquals(201, response.statusCode());
            var location = response.headers().firstValue("Location").orElseThrow();
            assertEquals(200, get(location).statusCode(), testId);
        }
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
    void rejectsEmptyAndMalformedJsonBodies() throws Exception {
        var empty = post("");
        var malformed = post("{not-json}");

        assertEquals(400, empty.statusCode());
        assertEquals(400, malformed.statusCode());
        assertTrue(empty.body().contains("\"error\""), empty.body());
        assertTrue(malformed.body().contains("\"error\""), malformed.body());
    }

    @Test
    void enforcesContentTypeAndRequestSizeLimits() throws Exception {
        var event = eventJson("run-protocol", "passed", 100);
        var missingContentType = post(event, null);
        var wrongContentType = post(event, "text/plain");
        var oversized = post("{\"padding\":\"" + "x".repeat(64 * 1024) + "\"}");

        assertEquals(400, missingContentType.statusCode());
        assertEquals(400, wrongContentType.statusCode());
        assertTrue(missingContentType.body().contains("\"code\":\"invalid_request\""));
        assertTrue(wrongContentType.body().contains("\"code\":\"invalid_request\""));
        assertEquals(413, oversized.statusCode());
    }

    @Test
    void rejectsNullEventBodiesAsValidationErrors() throws Exception {
        var response = post("null");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"validation_error\""), response.body());
        assertTrue(response.body().contains("request body must contain an event"), response.body());
    }

    @Test
    void rejectsTestIdsThatCannotRoundTripThroughTheClassificationRoute() throws Exception {
        var invalidTestIds = List.of("folder/test", ".", "..");
        for (var index = 0; index < invalidTestIds.size(); index++) {
            var event = eventJson("run-invalid-location-" + index, "passed", 100)
                    .replace("example", invalidTestIds.get(index));

            var response = post(event);

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("\"code\":\"validation_error\""), response.body());
        }
    }

    @Test
    void returnsInsufficientDataForATestWithOnlySkippedRuns() throws Exception {
        assertEquals(201, post(eventJson("run-skipped", "skipped", 100)).statusCode());

        var response = get("/tests/example");
        var health = new ObjectMapper().readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals("insufficient_data", health.path("classification").asText());
        assertEquals("none", health.path("failure_mode").asText());
        assertEquals(0, health.path("evidence").path("window_size").asInt());
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
        return post(body, "application/json");
    }

    private HttpResponse<String> post(String body, String contentType) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + "/events"))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
