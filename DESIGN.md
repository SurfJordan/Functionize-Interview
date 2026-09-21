# Design

## Stack and structure

I chose Java 21 because it is explicitly supported by the task and is the JVM language I can ship most confidently. Javalin 7 keeps the HTTP layer small while using mature Jetty and virtual threads. PostgreSQL is a deliberate production-facing choice: durable storage, atomic uniqueness, and indexed chronological reads matter more here than avoiding local setup. Docker Compose keeps that setup to one command; HikariCP and Flyway handle pooling and schema evolution.

The service follows a Controller–Service–Repository structure, with server configuration kept separate from route handling. Dependencies use constructor injection and are assembled explicitly in `Application`; a DI container would add ceremony to this small, static object graph. The classifier is a pure function and the repository is an interface, so policy tests do not require a server or database. Reads fetch at most 20 indexed rows, avoiding classification work that grows with lifetime history.

## Classification

“Flaky” means recent evidence contains both passing and non-passing outcomes without a sustained failure. The classifier orders by execution time, ignores skipped runs, and evaluates the latest 20 decisive runs:

- fewer than 5: `insufficient_data`
- latest 5 non-passing, or at least 80% non-passing: `broken`
- at most 10% non-passing: `healthy`
- otherwise: `flaky`

The five-run override detects a new regression before older passes dilute it. Thresholds are named policy constants, not claims of universal truth; production calibration would use labelled incidents and customer tolerance. Confidence is evidence precision: sample coverage multiplied by one minus the 95% Wilson-interval width. The response includes counts and the interval so callers can audit it.

Health and cause are separate. `failure_mode` is `assertion`, `execution`, `mixed`, or `none`, based only on the supplied statuses. I avoided guessing that a timeout string is definitely application or infrastructure failure.

## Reliability and cuts

`run_id` is an idempotency key: identical retries succeed, conflicting reuse returns `409`. PostgreSQL constraints backstop validation, and out-of-order arrivals work because reads use `started_at`. Storage failures become `503`; internal details are logged rather than returned. The main scale limit is PostgreSQL write throughput; a larger system would partition ingestion through a durable log and materialize per-test state asynchronously.

I cut authentication, deployment, metrics infrastructure, caches, a bulk HTTP API, and additional speculative states. They add little evidence about the classification problem in the allotted time.

## AI use

I used Codex to inspect the dataset, challenge architecture choices, draft code and tests, and iterate against compiler/test feedback; I retained the deterministic policy and reviewed the generated boundaries and failure paths. Repository `AGENTS.md` files and local skills make that workflow reproducible.

No model runs in the service. The classification strategy deliberately combines explicit heuristics with statistical confidence: its inputs are structured, its result must be reproducible, and the supplied data has no ground-truth health labels with which to train or objectively evaluate a model. Putting an LLM on the synchronous path would add latency, cost, nondeterminism, and another failure dependency without evidence that it improves the health label.

A future asynchronous enrichment stage could use pretrained embeddings or an LLM to cluster error messages and suggest runner, network, or application causes. With labelled incident and customer-feedback data, I would compare that model against the deterministic baseline for precision, recall, calibration, latency, and cost, first in shadow mode. It should influence customer-visible decisions only after demonstrating an improvement, never block ingestion, and retain the deterministic classifier as an auditable fallback. This uses existing models rather than training one from scratch.
