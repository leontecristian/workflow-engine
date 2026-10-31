# Feature specifications

This folder contains specifications and delivery records for individual features. Project-wide principles, constraints, assumptions and development guidance live in the parent [docs folder](../README.md).

Substantive features use four process documents. Keep each proportionate to the work; small maintenance changes may use a concise change record. Schemas, examples and focused technical contracts are additional deliverables only when needed. Architecture stays at project level.

Create a feature folder when substantive specification work begins:

```text
docs/features/
  <feature-id>-<name>/
    spec.md
    plan.md
    tasks.md
    implementation.md
    schemas/       # when machine-readable contracts are needed
    examples/      # when concrete contract examples are needed
```

- `spec.md` defines behavior, requirement IDs, acceptance scenarios and open questions.
- `plan.md` describes the implementation approach and applicable constitutional checks.
- `tasks.md` lists bounded work linked to requirements and completion conditions.
- `implementation.md` records what was built, decisions/deviations, verification results, limitations and remaining work; it is the session handover.

Planned verification belongs in plan.md; actual checks and PASS/FAIL/NOT RUN/BLOCKED results belong in implementation.md. The specification defines behavior; implementation records must not silently override it. Link between files instead of repeating content.

Follow the [specification-driven development workflow](../specification-driven-development.md). Reference the [constitution](../constitution.md), applicable [CON constraints](../constraints.md) and [ASM assumptions](../assumptions.md). Create schemas and examples only as their contracts are specified; do not treat proposed design examples as finalized contracts.

## Feature index

| Feature | Status | Scope |
| --- | --- | --- |
| [F001 — Initial database schema with Liquibase](F001-initial-database-schema/spec.md) | IMPLEMENTED | Nine core tables; migration, catalog/tracking inspection and repeat-start verification completed locally on 2026-10-25. |
| [F002 — Task type catalog management](F002-task-type-catalog/spec.md) | IMPLEMENTED revision 3 | Concrete TaskTypeCatalogService with fresh build/proxy/startup/read-only verification; earlier evidence retained. CRUD/race/audit-rollback execution NOT RUN. |
| [F003 — Core persistence model](F003-core-persistence-model/spec.md) | IMPLEMENTED revision 1 | Eight remaining entity mappings, established value types and narrow internal read repositories; unchanged F001 schema and F002 catalog. Static review, packaging and local discovery/schema validation passed; hydration and lookup/paging execution NOT RUN. |

F003 implementation is complete within its mapping/read scope, with its [plan](F003-core-persistence-model/plan.md), [tasks](F003-core-persistence-model/tasks.md) and [persistence contract](F003-core-persistence-model/persistence-contract.md). The proposed continuation is F004 minimal definition management/validation, then F005 minimal SINGLE workflow execution; those later feature documents have not been created. F003 adds no service or mutation API and does not implement publication or workflow execution.

Later specifications cover expression rules for Workflow Definition v1, human-node/host-capability contracts, structured task submissions, JSON execution variables and further runtime command integration. Use database design v1.4 under the [source precedence](../design-sources.md). Structured submissions cite CON-13/ASM-10; JSON variables cite CON-14/ASM-11. Exact schemas, bounds and APIs require specification. SINGLE, ALL and the fixed task list are agreed in language design sections 9.2–9.3; exact runtime contracts and verification remain specification work. The optional business decision engine remains deferred.

Acceptance scenarios describe required behavior. Creating this structure does not authorize automated tests; the current owner's no-tests instruction continues to apply.
