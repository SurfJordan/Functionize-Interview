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

After only that first event, the expected response is `insufficient_data`:

```json
{
  "test_id": "checkout_flow_happy_path",
  "classification": "insufficient_data",
  "failure_mode": "assertion",
  "confidence": 0.0,
  "reasoning": "1 decisive run available; at least 5 are required.",
  "evidence": {
    "window_size": 1,
    "passed": 0,
    "failed": 1,
    "errored": 0,
    "non_pass_rate": 1.0,
    "consecutive_non_passes": 1,
    "wilson_interval": { "lower": 0.207, "upper": 1.0 }
  }
}
```

`failure_mode` is separate from health: `failed` executions indicate assertion failures and `errored` executions indicate execution failures. `skipped` executions are stored but do not affect classification.

## Exercise the classification policy

The service waits for five decisive (`passed`, `failed`, or `errored`) runs before assigning a health classification. A single failed event therefore returns `insufficient_data`, not `flaky`; there is not enough evidence to distinguish a persistent failure from a transient one.

Once five runs exist, one failure among four passes is `flaky`: its 20% non-pass rate is above the 10% healthy threshold and below the 80% broken threshold. The failure still produces `failure_mode: assertion` because failure cause and health are intentionally independent.

This example uses a timestamped test ID, so it can be run repeatedly without existing database records changing the result:

```bash
DEMO_ID="readme-flaky-$(date +%s)"

post_demo_event() {
  curl --silent --show-error --fail-with-body \
    -H 'Content-Type: application/json' \
    -d "{
      \"test_id\": \"$DEMO_ID\",
      \"run_id\": \"$DEMO_ID-$1\",
      \"status\": \"$2\",
      \"duration_ms\": 100,
      \"started_at\": \"2026-04-12T14:02:1$1Z\"
    }" \
    http://localhost:8080/events
}

post_demo_event 1 failed
curl --silent --show-error --fail-with-body "http://localhost:8080/tests/$DEMO_ID"
# classification: insufficient_data; 1 decisive run

for RUN in 2 3 4 5; do
  post_demo_event "$RUN" passed
done
curl --silent --show-error --fail-with-body "http://localhost:8080/tests/$DEMO_ID"
# classification: flaky; 1 of 5 decisive runs is non-passing
```

Use these minimal histories to exercise each result:

| Desired classification | Decisive history | Why |
| --- | --- | --- |
| `insufficient_data` | Any 0–4 decisive runs | At least 5 are required |
| `healthy` | 5 passes | 0% non-passing is at most 10% |
| `flaky` | 4 passes and 1 failure | 20% is between the healthy and broken thresholds |
| `broken` | 1 pass and 4 failures | 80% non-passing meets the broken threshold |

The classifier evaluates only the latest 20 decisive runs, ordered by `started_at`. Five consecutive recent non-passing runs also produce `broken`, even when older passes make the overall rate lower.

## Tests

```bash
./gradlew test
./gradlew build
```

The suite covers classification boundaries, out-of-order/recent history behavior, confidence, HTTP contracts, duplicate ingestion, the JSONL importer, and the supplied dataset. PostgreSQL repository tests use Testcontainers and are skipped when Docker is unavailable.

GitHub Actions runs the full build on Java 21 and fails if any test is skipped, ensuring the PostgreSQL integration tests execute in CI.

The sample-data acceptance test verifies 5,360 records across 100 tests and, for the documented policy, 62 healthy, 24 flaky, and 14 broken classifications.

See [DESIGN.md](DESIGN.md) for the decisions and trade-offs.
