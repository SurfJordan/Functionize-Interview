---
name: document-branch
description: Create a uniquely named Markdown report of the current branch's completed work, acceptance-criteria coverage, verification evidence, regression impact, and remaining test needs. Use for change handoff, pull-request evidence, or pre-submission documentation.
---

# Document the branch

Create an evidence-based report in `docs/branch-reports/`. Read `docs/AGENTS.md` and the repository instructions before writing it.

## Establish the change set

- Use a user-specified base when provided. Otherwise prefer the configured upstream, then an existing `origin/main`, `main`, or `master` ref. Do not fetch or mutate Git state just to find a base.
- Inspect committed, staged, unstaged, and untracked work. State the chosen comparison point and whether the worktree is dirty.
- Read the acceptance-criteria source, normally `PROMPT.md`, plus relevant design and product documentation. Do not invent criteria from implementation details.
- Follow changed code into callers, persistence, HTTP contracts, configuration, and tests to identify prior behavior that could regress.

## Verify claims

Run the smallest meaningful checks needed to substantiate the report, expanding to the full suite when shared behavior or infrastructure changed. Record exact commands and outcomes. Never describe a recommended check as completed, and call out skipped tests or unavailable prerequisites.

## Write the report

Use these sections where applicable:

1. Summary and scope
2. Completed work by behavior, not commit chronology
3. Acceptance-criteria traceability: criterion, implementation evidence, test evidence, and status
4. Regression-impact analysis: affected prior behavior, why it may be affected, verification performed, and further testing needed
5. Verification evidence with exact commands and results
6. Known limitations, residual risks, and follow-up work

Reference repository paths and relevant symbols so reviewers can verify each claim. Keep the report factual and concise; do not duplicate the full README or design document.

Name the file `<UTC timestamp>-<branch slug>-change-report.md`, using a timestamp such as `20260921T181500Z`. Sanitize the branch name to lowercase hyphenated text. Use `detached-head` when necessary. If the name already exists, append `-2`, `-3`, and so on rather than overwriting another report.

