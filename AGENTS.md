# Repository guidance

## Product boundaries

- Implement only event ingestion and per-test classification; avoid unrelated API surface.
- Keep health classification deterministic and failure cause orthogonal.
- Preserve idempotency by treating `run_id` as globally unique.
- Support out-of-order ingestion by using `started_at`, never arrival order.

## Engineering conventions

- Target Java 21 and use the Gradle wrapper.
- Name Flyway migrations with ordered Unix timestamps, for example `V1789994930__create_execution_events.sql`.
- Prefer small Java records and explicit dependencies over reflection-heavy abstractions.
- Keep the classifier independent of HTTP and PostgreSQL.
- Add comments only for non-obvious policy or mathematics.
- Run `./gradlew test` after behavioral changes and `./gradlew build` before handoff.

Read the nearest nested `AGENTS.md` before changing classification or test code. Use the repository skills for classifier policy changes and final submission verification; final verification generates the branch report and customer documentation when applicable.

Use `document-branch` for acceptance-criteria and regression-evidence reports. Use `write-customer-documentation` for portable customer-facing guides; both write uniquely named files beneath `docs/`.
