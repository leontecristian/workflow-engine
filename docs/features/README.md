# Feature specifications

[F015 — Runtime task, command and recovery extraction](F015-runtime-task-instance-extraction/spec.md) is **IMPLEMENTED revision 2**. Runtime reads, complete task UI, immutable buffering and recovery are shared; independently activated completion mapping delegates to required host adapters. Read-only consumption needs no command provider. Business starts and transactional orchestration remain host-owned; see the [handover](F015-runtime-task-instance-extraction/implementation.md).

[F014 — Definition and catalog administration](F014-definition-catalog-administration/spec.md) is **IMPLEMENTED revision 3**. Existing administration is shared through workflow-web and the workflow-ui administration entry point; sandbox policy and transaction/audit remain host-owned. Focused unit/build, guarded changed-write/audit and packaged-browser checks pass; see the [handover](F014-definition-catalog-administration/implementation.md).

[F013 � Shared instance viewer extraction](F013-shared-instance-viewer/spec.md) is **IMPLEMENTED revision 5; runtime acceptance COMPLETE**. T03�T06 and the focused NgModule browser pass are complete. Guarded SQL/GET and backend-served production-console browser checks now pass; see the [handover](F013-shared-instance-viewer/implementation.md). Applicable F012 evidence is reused.

[F012 — Angular 19 frontend alignment](F012-angular-19-frontend-alignment/spec.md) is **IMPLEMENTED revision 1; sandbox acceptance complete** (2026-11-07). Dependency, isolated-test, production-build and Maven gates pass. Browser acceptance covers exact scalar/JSON downloads, sequential/skipped stages, uncertain-response recovery, failed/delayed reads, role/logout behavior, narrow-screen controls and packaged serving under host CSP. Library extraction and APOLO runtime integration remain excluded. See the [plan](F012-angular-19-frontend-alignment/plan.md), [tasks](F012-angular-19-frontend-alignment/tasks.md) and [handover](F012-angular-19-frontend-alignment/implementation.md).

[F011 — Multiple sequential human stages](F011-sequential-human-stages/spec.md) is **IMPLEMENTED revision 1** (2026-11-07): successive/conditional SINGLE stages, atomic entry, exact historical contracts and stable completion/replay identity. Its [handover](F011-sequential-human-stages/implementation.md) records unit/build, guarded SQL and HTTP outcomes; interactive browser acceptance remains blocked. See the [plan](F011-sequential-human-stages/plan.md) and [tasks](F011-sequential-human-stages/tasks.md).

[F010 — Human-task outcomes and outcome-based routing](F010-human-task-outcomes/spec.md) is **IMPLEMENTED revision 1** (2026-11-06): 1–16 outcomes and exact routes within one SINGLE human stage, per-outcome comments and pinned console actions. See its [plan](F010-human-task-outcomes/plan.md), [tasks](F010-human-task-outcomes/tasks.md) and [handover](F010-human-task-outcomes/implementation.md) for automated/HTTP verification and the completed Chrome walkthrough.

[F009 — JSON execution variables](F009-json-execution-variables/spec.md) is **IMPLEMENTED revision 3** (2026-11-06): schema-free bounded snapshots, explicit start/service mappings and safe nested SpEL reads. See its [plan](F009-json-execution-variables/plan.md), [tasks](F009-json-execution-variables/tasks.md) and [handover](F009-json-execution-variables/implementation.md). D17 removes pre-F009 API/data compatibility requirements while preserving history and guarantees for new executions. The technical annex governs the implementation. Automated unit/SQL/frontend verification is recorded in the handover; interactive browser acceptance remains NOT RUN.

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

[F008 — Restricted expressions and exclusive routing](F008-restricted-expressions-routing/spec.md) is **IMPLEMENTED revision 3**, with T08 browser/live-delivery acceptance open. Its [handover](F008-restricted-expressions-routing/implementation.md) records actual checks. F009 extends its scalar-only scope without changing that historical baseline.

[F007 — Typed bindings and service tasks](F007-typed-bindings-service-tasks/spec.md) is **IMPLEMENTED through revision 3** (batched variable-write events), with revision 2 T09 **CLOSED by owner acceptance** on 2026-11-01 after manual API/SQL observations and owner browser walkthrough/replay confirmation; unverified follow-up coverage remains recorded in the handover. Its [plan](F007-typed-bindings-service-tasks/plan.md), [tasks](F007-typed-bindings-service-tasks/tasks.md) and [handover](F007-typed-bindings-service-tasks/implementation.md) record scalar bindings, synchronous service tasks, one privacy-preserving variable-write event per nonempty mapping operation, exact receipts/history, transactional sandbox support and isolated checks/builds. F005/F006 acceptance is unchanged.

| Feature | Status | Scope |
| --- | --- | --- |
| [F001 — Initial database schema with Liquibase](F001-initial-database-schema/spec.md) | IMPLEMENTED | Nine core tables; migration, catalog/tracking inspection and repeat-start verification completed locally on 2026-10-25. |
| [F002 — Task type catalog management](F002-task-type-catalog/spec.md) | IMPLEMENTED revision 3 | Concrete TaskTypeCatalogService with fresh build/proxy/startup/read-only verification; earlier evidence retained. CRUD/race/audit-rollback execution NOT RUN. |
| [F003 — Core persistence model](F003-core-persistence-model/spec.md) | IMPLEMENTED revision 1 | Eight remaining entity mappings, established value types and narrow internal read repositories; unchanged F001 schema and F002 catalog. Static review, packaging and local discovery/schema validation passed; hydration and lookup/paging execution NOT RUN. |
| [F004 — Definition management and validation](F004-definition-management-validation/spec.md) | IMPLEMENTED revision 1 | Explicit-version draft import/edit/read, minimal SINGLE validation, immutable publication/retirement, canonical hashes and gated legacy-column migration. Package, local migration/restart and bootstrap passed; lifecycle/validation/race behavior NOT RUN. |
| [F005 — Minimal SINGLE workflow execution](F005-minimal-single-workflow-execution/spec.md) | IMPLEMENTED core revision 1; original T11 remains open | Core commands/reads are implemented. F006 now supplies real sandbox adapters and explicitly matched local rollback/replay/concurrency evidence; its handover does not automatically close F005's broader acceptance gate. |
| [F006 — Test-host workflow console](F006-test-host-definition-console/spec.md) | IMPLEMENTED revision 6; new graph browser acceptance open | Angular management/execution console with an administrator Instances monitor and authorized read-only general instance graph, two-account security, persisted sandbox links, real assignment, transactional audit/outbox and explicit durable local delivery. API/database walkthrough evidence and browser limitations are in its handover. |

F003/F004 and F005 core implementations are present within their selected scopes. F004 retains its [plan](F004-definition-management-validation/plan.md), [tasks](F004-definition-management-validation/tasks.md) and [handover](F004-definition-management-validation/implementation.md). F005 retains its [execution plan](F005-minimal-single-workflow-execution/plan.md), [tasks](F005-minimal-single-workflow-execution/tasks.md) and [handover](F005-minimal-single-workflow-execution/implementation.md). F006 revision 6 supplies the separately authorized executable host adapters, console, bounded instance monitor and pinned graph; its [plan](F006-test-host-definition-console/plan.md), [tasks](F006-test-host-definition-console/tasks.md) and [handover](F006-test-host-definition-console/implementation.md) distinguish actual API/database observations from browser and production integration gaps. No historical F005 acceptance gate is automatically closed.

Later specifications cover expression rules for Workflow Definition v1, human-node/host-capability contracts, structured task submissions, JSON execution variables and further runtime command integration. Use database design v1.4 under the [source precedence](../design-sources.md). Structured submissions cite CON-13/ASM-10; JSON variables cite CON-14/ASM-11. Exact schemas, bounds and APIs require specification. SINGLE, ALL and the fixed task list are agreed in language design sections 9.2–9.3; exact runtime contracts and verification remain specification work. The optional business decision engine remains deferred.

Acceptance scenarios describe required behavior. The owner permits isolated unit tests and guarded integration tests on `localhost:1433/apolo_workflow_test` under AGENTS.md. Other database targets require task-specific authorization; this policy supersedes older feature-level restrictions without changing historical results. F006's documented manual development-database walkthrough is separately authorized.
