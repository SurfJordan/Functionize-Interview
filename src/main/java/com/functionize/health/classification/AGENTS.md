# Classification guidance

- Treat the list passed to `TestClassifier` as newest-first decisive history.
- Keep thresholds named and the reasoning derived from the same evidence as the label.
- Do not infer root cause from free-form error text in deterministic code.
- Keep `failure_mode` independent from `classification`.
- Any policy change must update focused boundary tests, the sample-data acceptance result, README, and DESIGN.md.

