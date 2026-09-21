---
name: write-customer-documentation
description: Write uniquely named, customer-facing Markdown documentation for a feature or workflow, suitable for Notion and similar hosted documentation systems. Use for public guides, API usage instructions, troubleshooting, or release-facing feature documentation; not for internal branch reports.
---

# Write customer documentation

Create a portable Markdown document in `docs/customer/`. Read `docs/AGENTS.md` and the repository instructions before writing it.

Establish the intended audience, task, and product terminology from the request and repository. If these are not explicit, infer the narrowest useful audience and state the assumption in the handoff rather than inside the published document.

Treat the implementation, tests, OpenAPI contract, README, and product requirements as sources of truth. Resolve discrepancies before documenting them. Verify commands and examples when practical; clearly label values that are illustrative.

Write for the customer's goal rather than the code structure. Include only sections that help complete the task, such as:

- What the feature does and when to use it
- Prerequisites
- A short step-by-step workflow
- Requests, responses, or configuration examples
- Expected outcomes
- Errors and troubleshooting
- Customer-relevant limitations or operational notes

Use plain language, descriptive headings, fenced code blocks with language identifiers, and standard Markdown tables sparingly. Avoid repository-internal paths, class names, implementation rationale, AI-workflow notes, secrets, private endpoints, unsupported promises, and renderer-specific HTML. Keep heading levels and links portable for Notion and similar importers.

Name the file `<UTC timestamp>-<topic slug>.md`, using a timestamp such as `20260921T181500Z`. Keep the title human-readable inside the document. If the filename exists, append `-2`, `-3`, and so on rather than overwriting existing documentation.

