package com.functionize.health.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public final class HttpServer {
    private static final long MAX_REQUEST_SIZE = 64 * 1024;

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
            controller.register(config.routes);
        });
    }
}

