---
name: change-classification-policy
description: Change or review this service's health classification rules, thresholds, confidence calculation, or failure modes. Use only for classifier behavior, not routine HTTP or persistence work.
---

# Change classification policy

Read `PROMPT.md`, `DESIGN.md`, and the classification directory's `AGENTS.md` before changing behavior.

Keep classification deterministic, use only observable event fields, and keep health separate from failure cause. Make policy constants explicit and ensure the returned reasoning and evidence describe the exact rule that selected the label.

After a change:

1. Add or update focused tests at every changed boundary, including insufficient evidence, recent regressions, skips, and out-of-order history where relevant.
2. Run the classifier and sample-data acceptance tests.
3. Review changes to the sample dataset's category counts; accept them only when they are an intended consequence.
4. Update `README.md` and the one-page `DESIGN.md` so the public explanation matches the code.

