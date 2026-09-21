package com.functionize.health.importer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.functionize.health.event.EventService;
import com.functionize.health.event.ExecutionEvent;
import com.functionize.health.event.IngestResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonlImporter {
    private final EventService service;
    private final JsonMapper mapper;

    public JsonlImporter(EventService service) {
        this.service = service;
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public ImportReport importFile(Path file) {
        long linesRead = 0;
        long created = 0;
        long duplicates = 0;
        try (var lines = Files.lines(file)) {
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                var line = iterator.next();
                linesRead++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    var result = service.ingest(mapper.readValue(line, ExecutionEvent.class));
                    if (result == IngestResult.CREATED) {
                        created++;
                    } else {
                        duplicates++;
                    }
                } catch (RuntimeException | IOException exception) {
                    throw new ImportException(linesRead, exception);
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read JSONL file: " + file, exception);
        }
        return new ImportReport(linesRead, created, duplicates);
    }

    public record ImportReport(long linesRead, long created, long duplicates) {}

    @SuppressWarnings("serial")
    public static final class ImportException extends RuntimeException {
        public ImportException(long lineNumber, Throwable cause) {
            super("Import failed at line " + lineNumber + ": " + cause.getMessage(), cause);
        }
    }
}
