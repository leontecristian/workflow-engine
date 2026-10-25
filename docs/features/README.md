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

The first feature is [F001 — Initial database schema with Liquibase](F001-initial-database-schema/spec.md), IMPLEMENTED with local verification complete. Its plan, schema contract, tasks and verification runbook cover the nine core tables and owner-authorized local startup, catalog inspection, DATABASECHANGELOG checks and repeat-start verification. The schema migration, complete catalog inspection, tracking records and same-artifact restart were verified on 2026-10-25; see its evidence record. The remaining priorities are Workflow Definition v1, Expression Policy v1, simple task catalog and human-node contracts, structured task submissions, JSON execution variables, and runtime command integration, as described in the development workflow. Use database design v1.4 under the [source precedence](../design-sources.md), including selected numeric JPA versions, APOLO-owned instance-ID associations and authoritative JSON completion evidence. Structured submissions cite CON-13/ASM-10; JSON variables cite CON-14/ASM-11. Exact schemas, bounds and APIs require specification. Detailed completion policies remain proposals until specified. The optional business decision engine remains deferred.

Acceptance scenarios describe required behavior. Creating this structure does not authorize automated tests; the current owner's no-tests instruction continues to apply.
