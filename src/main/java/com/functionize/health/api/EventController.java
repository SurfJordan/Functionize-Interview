package com.functionize.health.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.functionize.health.classification.Classification;
import com.functionize.health.classification.ClassificationResult.Evidence;
import com.functionize.health.classification.FailureMode;
import com.functionize.health.event.EventService;
import com.functionize.health.event.ExecutionEvent;
import com.functionize.health.event.IngestResult;
import com.functionize.health.event.RepositoryException;
import io.javalin.config.RoutesConfig;
import io.javalin.http.BadRequestResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EventController {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventController.class);

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    public void register(RoutesConfig routes) {
        routes.post("/events", context -> {
            ExecutionEvent event = context.body().trim().equals("null")
                    ? null
                    : context.bodyAsClass(ExecutionEvent.class);
            var result = service.ingest(event);
            var status = result == IngestResult.CREATED ? 201 : 200;
            context.status(status)
                    .header("Location", "/tests/" + encodePathSegment(event.testId()))
                    .json(new IngestResponse(
                            event.runId(), event.testId(), result.name().toLowerCase(Locale.ROOT)));
        });
        routes.get("/tests/{test_id}", context -> {
            var health = service.getTestHealth(context.pathParam("test_id"));
            var result = health.result();
            context.json(new TestHealthResponse(
                    health.testId(),
                    result.classification(),
                    result.failureMode(),
                    result.confidence(),
                    result.reasoning(),
                    result.evidence()));
        });

        routes.exception(EventService.ValidationException.class, (exception, context) -> context.status(400)
                .json(problem("validation_error", exception.getMessage(), exception.errors())));
        routes.exception(EventService.ConflictingRunException.class, (exception, context) -> context.status(409)
                .json(problem("run_id_conflict", exception.getMessage(), List.of())));
        routes.exception(EventService.TestNotFoundException.class, (exception, context) -> context.status(404)
                .json(problem("test_not_found", exception.getMessage(), List.of())));
        routes.exception(JsonProcessingException.class, (exception, context) -> context.status(400)
                .json(problem("invalid_json", "Request body is not a valid event", List.of())));
        routes.exception(BadRequestResponse.class, (exception, context) -> context.status(400)
                .json(problem("invalid_request", "Request body is not a valid event", List.of())));
        routes.exception(RepositoryException.class, (exception, context) -> {
            LOGGER.error("Database operation failed", exception);
            context.status(503)
                    .json(problem("service_unavailable", "Persistent storage is temporarily unavailable", List.of()));
        });
        routes.exception(Exception.class, (exception, context) -> {
            LOGGER.error("Unhandled request failure", exception);
            context.status(500).json(problem("internal_error", "An unexpected error occurred", List.of()));
        });
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Map<String, Object> problem(String code, String message, List<String> details) {
        return Map.of("error", new ErrorResponse(code, message, details));
    }

    private record IngestResponse(
            @JsonProperty("run_id") String runId,
            @JsonProperty("test_id") String testId,
            String result) {}

    private record TestHealthResponse(
            @JsonProperty("test_id") String testId,
            Classification classification,
            @JsonProperty("failure_mode") FailureMode failureMode,
            double confidence,
            String reasoning,
            Evidence evidence) {}

    private record ErrorResponse(String code, String message, List<String> details) {}
}
