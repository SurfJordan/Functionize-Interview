---
name: verify-submission
description: Verify this Functionize take-home before handoff or submission. Use for final readiness checks across build, tests, sample import, API behavior, documentation, and repository hygiene.
---

# Verify the submission

Inspect the working tree first and preserve unrelated user changes.

1. Run `./gradlew test` and `./gradlew build`.
2. Confirm the Testcontainers tests actually ran when Docker is available; report skips rather than presenting them as passes.
3. Validate `compose.yaml`, then start PostgreSQL and import `data/sample_data.jsonl` when local Docker access is available.
4. Exercise a new event, an identical replay, a conflicting replay, a known classification, and validation/not-found errors through HTTP.
5. Confirm `/` redirects to `/docs`, Swagger UI loads, and `/openapi.json` describes both public API operations and their meaningful responses.
6. Check that README commands work from a clean checkout and that `DESIGN.md` remains one concise page covering every prompt requirement.
7. Review `git status` for generated output, credentials, IDE files, or accidental edits to the supplied prompt/data.
8. Read and follow `.agents/skills/document-branch/SKILL.md` to create the final branch report. If customer-visible behavior changed, also read and follow `.agents/skills/write-customer-documentation/SKILL.md`; otherwise do not create customer documentation merely for ceremony.

Report exact checks run, failures or skips, and any remaining operational prerequisite.
