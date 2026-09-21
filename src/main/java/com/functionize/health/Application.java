package com.functionize.health;

import com.functionize.health.api.EventController;
import com.functionize.health.api.HttpServer;
import com.functionize.health.classification.TestClassifier;
import com.functionize.health.db.Database;
import com.functionize.health.event.EventService;
import com.functionize.health.event.EventValidator;
import com.functionize.health.event.PostgresEventRepository;
import com.functionize.health.importer.JsonlImporter;
import java.nio.file.Path;

public final class Application {
    private Application() {}

    public static void main(String[] args) {
        var config = AppConfig.fromEnvironment();
        var database = Database.connect(config);

        try {
            database.migrate();
            var service = new EventService(
                    new PostgresEventRepository(database.dataSource()), new EventValidator(), new TestClassifier());

            if (args.length > 0) {
                runCommand(args, service);
                database.close();
                return;
            }

            var controller = new EventController(service);
            var app = HttpServer.create(controller, database::close);
            app.start(config.port());
        } catch (RuntimeException exception) {
            database.close();
            throw exception;
        }
    }

    private static void runCommand(String[] args, EventService service) {
        if (args.length != 2 || !"import".equals(args[0])) {
            throw new IllegalArgumentException("Usage: import <jsonl-file>");
        }

        var report = new JsonlImporter(service).importFile(Path.of(args[1]));
        System.out.printf(
                "Imported %d records: %d created, %d duplicates%n",
                report.linesRead(), report.created(), report.duplicates());
    }
}
