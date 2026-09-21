package com.functionize.health;

import java.util.Map;

public record AppConfig(int port, String databaseUrl, String databaseUser, String databasePassword) {
    public static AppConfig fromEnvironment() {
        return from(System.getenv());
    }

    static AppConfig from(Map<String, String> environment) {
        var portValue = environment.getOrDefault("PORT", "8080");
        final int port;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("PORT must be an integer", exception);
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("PORT must be between 1 and 65535");
        }

        return new AppConfig(
                port,
                environment.getOrDefault("DATABASE_URL", "jdbc:postgresql://localhost:5432/functionize"),
                environment.getOrDefault("DATABASE_USER", "functionize"),
                environment.getOrDefault("DATABASE_PASSWORD", "functionize"));
    }
}

