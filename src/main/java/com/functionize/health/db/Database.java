package com.functionize.health.db;

import com.functionize.health.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

public final class Database implements AutoCloseable {
    private final HikariDataSource dataSource;

    private Database(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static Database connect(AppConfig appConfig) {
        var poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl(appConfig.databaseUrl());
        poolConfig.setUsername(appConfig.databaseUser());
        poolConfig.setPassword(appConfig.databasePassword());
        poolConfig.setMaximumPoolSize(10);
        poolConfig.setMinimumIdle(1);
        poolConfig.setConnectionTimeout(5_000);
        poolConfig.setPoolName("test-health-db");
        return new Database(new HikariDataSource(poolConfig));
    }

    public void migrate() {
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}

