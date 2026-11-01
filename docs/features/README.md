# Feature specifications

This folder contains specifications and delivery records for individual features. Project-wide principles, constraints, assumptions and development guidance live in the parent [docs folder](../README.md).

The [project roadmap](../roadmap.md) owns delivery sequencing, proposed future increments and milestone exit conditions. This index tracks specified features; roadmap proposals are not READY specifications.

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

[F008 — Restricted expressions and exclusive routing](F008-restricted-expressions-routing/spec.md) is **DRAFT revision 1**, dependent on F007. Its [plan](F008-restricted-expressions-routing/plan.md), [tasks](F008-restricted-expressions-routing/tasks.md) and [handover](F008-restricted-expressions-routing/implementation.md) track scalar first-match routing, expression safety and the G01–G03 readiness gates. No implementation or safety verification is claimed.

| Feature | Status | Scope |
| --- | --- | --- |
| [F001 — Initial database schema with Liquibase](F001-initial-database-schema/spec.md) | IMPLEMENTED | Nine core tables; migration, catalog/tracking inspection and repeat-start verification completed locally on 2026-10-25. |
| [F002 — Task type catalog management](F002-task-type-catalog/spec.md) | IMPLEMENTED revision 3 | Concrete TaskTypeCatalogService with fresh build/proxy/startup/read-only verification; earlier evidence retained. CRUD/race/audit-rollback execution NOT RUN. |
| [F003 — Core persistence model](F003-core-persistence-model/spec.md) | IMPLEMENTED revision 1 | Eight remaining entity mappings, established value types and narrow internal read repositories; unchanged F001 schema and F002 catalog. Static review, packaging and local discovery/schema validation passed; hydration and lookup/paging execution NOT RUN. |
| [F004 — Definition management and validation](F004-definition-management-validation/spec.md) | IMPLEMENTED revision 1 | Explicit-version draft import/edit/read, minimal SINGLE validation, immutable publication/retirement, canonical hashes and gated legacy-column migration. Package, local migration/restart and bootstrap passed; lifecycle/validation/race behavior NOT RUN. |
| [F005 — Minimal SINGLE workflow execution](F005-minimal-single-workflow-execution/spec.md) | IMPLEMENTED core revision 1; original T11 remains open | Core commands/reads are implemented. F006 now supplies real sandbox adapters and explicitly matched local rollback/replay/concurrency evidence; its handover does not automatically close F005's broader acceptance gate. |
| [F006 — Test-host workflow console](F006-test-host-definition-console/spec.md) | IMPLEMENTED revision 5; browser acceptance open | Angular management/execution console with an administrator Instances monitor and authorized read-only instance graph, two-account security, persisted sandbox links, real assignment, transactional audit/outbox and explicit durable local delivery. API/database walkthrough evidence and browser limitations are in its handover. |

F003/F004 and F005 core implementations are present within their selected scopes. F004 retains its [plan](F004-definition-management-validation/plan.md), [tasks](F004-definition-management-validation/tasks.md) and [handover](F004-definition-management-validation/implementation.md). F005 retains its [execution plan](F005-minimal-single-workflow-execution/plan.md), [tasks](F005-minimal-single-workflow-execution/tasks.md) and [handover](F005-minimal-single-workflow-execution/implementation.md). F006 revision 5 supplies the separately authorized executable host adapters, console, bounded instance monitor and pinned graph; its [plan](F006-test-host-definition-console/plan.md), [tasks](F006-test-host-definition-console/tasks.md) and [handover](F006-test-host-definition-console/implementation.md) distinguish actual API/database observations from browser and production integration gaps. No historical F005 acceptance gate is automatically closed.

Later specifications cover expression rules for Workflow Definition v1, human-node/host-capability contracts, structured task submissions, JSON execution variables and further runtime command integration. Use database design v1.4 under the [source precedence](../design-sources.md). Structured submissions cite CON-13/ASM-10; JSON variables cite CON-14/ASM-11. Exact schemas, bounds and APIs require specification. SINGLE, ALL and the fixed task list are agreed in language design sections 9.2–9.3; exact runtime contracts and verification remain specification work. The optional business decision engine remains deferred.

Acceptance scenarios describe required behavior. The owner permits isolated unit tests; database integration tests require explicit authorization under AGENTS.md. F006's documented manual development-database walkthrough is separately authorized.
