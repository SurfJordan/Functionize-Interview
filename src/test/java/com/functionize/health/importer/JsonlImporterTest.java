package com.functionize.health.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.functionize.health.classification.TestClassifier;
import com.functionize.health.event.EventService;
import com.functionize.health.event.EventValidator;
import com.functionize.health.event.InMemoryEventRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlImporterTest {
    @TempDir
    Path directory;

    @Test
    void importsNewRecordsAndCountsReplays() throws Exception {
        var importer = importer();
        var file = directory.resolve("events.jsonl");
        Files.writeString(file, validLine("run-1") + "\n" + validLine("run-1") + "\n");

        var report = importer.importFile(file);

        assertEquals(2, report.linesRead());
        assertEquals(1, report.created());
        assertEquals(1, report.duplicates());
    }

    @Test
    void reportsTheInvalidLineNumber() throws Exception {
        var file = directory.resolve("events.jsonl");
        Files.writeString(file, validLine("run-1") + "\n{not-json}\n");

        var exception = assertThrows(JsonlImporter.ImportException.class, () -> importer().importFile(file));

        assertTrue(exception.getMessage().contains("line 2"));
    }

    private static JsonlImporter importer() {
        var service = new EventService(new InMemoryEventRepository(), new EventValidator(), new TestClassifier());
        return new JsonlImporter(service);
    }

    private static String validLine(String runId) {
        return ("{\"test_id\":\"example\",\"run_id\":\"%s\",\"status\":\"passed\","
                        + "\"duration_ms\":100,\"started_at\":\"2026-04-12T14:02:11Z\"}")
                .formatted(runId);
    }
}
