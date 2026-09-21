package com.functionize.health.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class HttpServer {
    private static final long MAX_REQUEST_SIZE = 64 * 1024;
    private static final String OPEN_API_SPEC = loadOpenApiSpec();

    private HttpServer() {}

    public static Javalin create(EventController controller) {
        return create(controller, () -> {});
    }

    public static Javalin create(EventController controller, Runnable onServerStopped) {
        return Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.events.serverStopped(() -> onServerStopped.run());
            config.http.defaultContentType = "application/json";
            config.http.strictContentTypes = true;
            config.http.maxRequestSize = MAX_REQUEST_SIZE;
            config.jsonMapper(new JavalinJackson().updateMapper(mapper -> mapper.enable(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)));
            config.registerPlugin(new SwaggerPlugin(swagger -> {
                swagger.documentationPath = "/openapi.json";
                swagger.uiPath = "/docs";
                swagger.title = "Functionize Test Health API";
                swagger.validatorUrl = null;
            }));
            config.routes.get("/", context -> context.redirect("/docs"));
            config.routes.get("/openapi.json", context -> context.contentType("application/json").result(OPEN_API_SPEC));
            controller.register(config.routes);
        });
    }

    private static String loadOpenApiSpec() {
        try (var stream = HttpServer.class.getResourceAsStream("/openapi.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing OpenAPI specification");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read OpenAPI specification", exception);
        }
    }
}
