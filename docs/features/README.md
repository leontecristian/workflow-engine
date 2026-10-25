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

## Feature index

| Feature | Status | Scope |
| --- | --- | --- |
| [F001 — Initial database schema with Liquibase](F001-initial-database-schema/spec.md) | IMPLEMENTED | Nine core tables; migration, catalog/tracking inspection and repeat-start verification completed locally on 2026-10-25. |
| [F002 — Task type catalog management](F002-task-type-catalog/spec.md) | READY | Core Java create/read/list/update service, permanent codes, optimistic concurrency and host transaction/audit handoff. Documentation only; implementation and verification not run. |

Next specifications cover Workflow Definition v1 and validation, definition lifecycle, Expression Policy v1, human-node/host-capability contracts, structured task submissions, JSON execution variables and runtime command integration. F002 does not implement publication or workflow execution. Use database design v1.4 under the [source precedence](../design-sources.md). Structured submissions cite CON-13/ASM-10; JSON variables cite CON-14/ASM-11. Exact schemas, bounds and APIs require specification. Detailed completion policies remain proposals until specified. The optional business decision engine remains deferred.

Acceptance scenarios describe required behavior. Creating this structure does not authorize automated tests; the current owner's no-tests instruction continues to apply.
