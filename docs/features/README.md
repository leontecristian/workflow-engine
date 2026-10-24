# Feature specifications

This folder contains specifications and delivery records for individual features. Project-wide principles, constraints, assumptions and development guidance live in the parent [docs folder](../README.md).

Create a feature folder when substantive specification work begins:

```text
docs/features/
  <feature-id>-<name>/
    spec.md
    plan.md
    tasks.md
    evidence.md
    schemas/       # when machine-readable contracts are needed
    examples/      # when concrete contract examples are needed
```

- `spec.md` defines behavior, requirement IDs, acceptance scenarios and open questions.
- `plan.md` describes the implementation approach and applicable constitutional checks.
- `tasks.md` lists bounded work linked to requirements and completion conditions.
- `evidence.md` records what was checked, its result and remaining gaps.

Follow the [specification-driven development workflow](../specification-driven-development.md). Reference the [constitution](../constitution.md), applicable [CON constraints](../constraints.md) and [ASM assumptions](../assumptions.md). Create schemas and examples only as their contracts are specified; do not treat proposed design examples as finalized contracts.

No feature specifications have been created yet. The current priorities are Workflow Definition v1, Expression Policy v1, task-type registry contracts, and persistence/command integration, as described in the development workflow. The optional business decision engine remains deferred.

Acceptance scenarios describe required behavior. Creating this structure does not authorize automated tests; the current owner's no-tests instruction continues to apply.
