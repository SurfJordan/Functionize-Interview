# Test guidance

- Test observable policy boundaries and failure behavior, not implementation details.
- Keep classifier tests database-free; use Testcontainers for PostgreSQL semantics.
- Use the supplied dataset for one policy-level acceptance test rather than thousands of repetitive assertions.
- Never weaken an assertion solely to make a failing behavior pass; confirm the intended contract first.

