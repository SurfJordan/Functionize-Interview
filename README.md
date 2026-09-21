# Test Health Classifier

A small Java service that ingests automated-test executions and classifies each test as `healthy`, `flaky`, `broken`, or `insufficient_data`. Classification is deterministic, bounded to recent history, and includes the evidence behind the result.

## Requirements

- Java 21 or newer
- Docker, for PostgreSQL and the repository integration tests

The Gradle wrapper is included; no local Gradle installation is required.

## Run locally

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Start the service (Flyway applies the schema automatically):

```bash
./gradlew run
```

The service listens on `http://localhost:8080`. Configuration can be overridden with:

| Variable | Default |
| --- | --- |
| `PORT` | `8080` |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/functionize` |
| `DATABASE_USER` | `functionize` |
| `DATABASE_PASSWORD` | `functionize` |

Open [`http://localhost:8080`](http://localhost:8080) to go straight to the interactive Swagger UI. The underlying OpenAPI 3.1 contract is available at [`/openapi.json`](http://localhost:8080/openapi.json).

Stop and remove the local database with `docker compose down -v` when its data is no longer needed.

## Load the sample data

With PostgreSQL running, import the supplied JSONL file:

```bash
./gradlew run --args="import data/sample_data.jsonl"
```

The importer uses the same validation and ingestion service as the HTTP endpoint. Re-running it is safe: identical `run_id` values are counted as duplicates.

## API

Ingest an execution:

```bash
curl --fail-with-body \
  -H 'Content-Type: application/json' \
  -d '{
    "test_id": "checkout_flow_happy_path",
    "run_id": "run_9281",
    "status": "failed",
    "duration_ms": 4230,
    "started_at": "2026-04-12T14:02:11Z",
    "error_message": "Expected submit button to be visible"
  }' \
  http://localhost:8080/events
```

A new event returns `201`; an identical replay returns `200`; reusing a `run_id` for different data returns `409`.

Get the current classification:

```bash
curl --fail-with-body http://localhost:8080/tests/checkout_flow_happy_path
```

Example response:

```json
{
  "test_id": "checkout_flow_happy_path",
  "classification": "flaky",
  "failure_mode": "mixed",
  "confidence": 0.626,
  "reasoning": "Non-passing outcomes: 6 of 20 decisive runs (4 failed, 2 errored).",
  "evidence": {
    "window_size": 20,
    "passed": 14,
    "failed": 4,
    "errored": 2,
    "non_pass_rate": 0.3,
    "consecutive_non_passes": 1,
    "wilson_interval": { "lower": 0.145, "upper": 0.519 }
  }
}
```

`failure_mode` is separate from health: `failed` executions indicate assertion failures and `errored` executions indicate execution failures. `skipped` executions are stored but do not affect classification.

## Tests

```bash
./gradlew test
./gradlew build
```

The suite covers classification boundaries, out-of-order/recent history behavior, confidence, HTTP contracts, duplicate ingestion, the JSONL importer, and the supplied dataset. PostgreSQL repository tests use Testcontainers and are skipped when Docker is unavailable.

GitHub Actions runs the full build on Java 21 and fails if any test is skipped, ensuring the PostgreSQL integration tests execute in CI.

The sample-data acceptance test verifies 5,360 records across 100 tests and, for the documented policy, 62 healthy, 24 flaky, and 14 broken classifications.

See [DESIGN.md](DESIGN.md) for the decisions and trade-offs.
