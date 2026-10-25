# Specification-driven development

This workflow operationalizes [constitution v2.2.0](constitution.md). It is a repository convention; it does not require installing a specification framework or code generator.

## 1. Specify the behavior

Start a feature under `docs/features/<feature-id>-<name>/` when substantive work on that feature begins. Keep project-wide guidance directly in `docs/`. Give the feature a stable identifier and keep specification changes reviewable in Git. See the [feature guide](features/README.md).

The `spec.md` should contain:

- Feature identifier, status, owner, revision/date and constitution version.
- Problem, actors, intended outcome and source-design references.
- In-scope behavior and explicit exclusions.
- Numbered requirements, such as `<feature-id>-R01`, written in observable terms.
- Contracts: accepted inputs, outputs, lifecycle rules, failure behavior, authorization and compatibility.
- Data ownership, transaction boundary, concurrency/idempotency and event implications where applicable.
- Numbered acceptance scenarios linked to requirements, covering success, rejection, boundaries and failure paths.
- Open questions with status, owner and the point at which each must be resolved.

Reference applicable CON identifiers from [constraints](constraints.md) and ASM identifiers from [assumptions](assumptions.md). Record new feature-specific assumptions with their owner, verification point and impact if false; do not treat them as confirmed requirements.

Use Given/When/Then where it makes a scenario precise. An acceptance scenario describes required behavior; writing it does not authorize creating or running an automated test.

For definition languages or imports, add versioned schemas and representative valid/invalid examples. Mark examples normative only after the corresponding contract is settled. Future executable examples must obey the current execution policy.

## 2. Resolve material uncertainty

Distinguish a missing implementation choice from an undecided business or public contract. Choose ordinary implementation details within the agreed constraints. Escalate decisions that would change scope, ownership, observable semantics or compatibility.

Do not guess a value for an OPEN field just to complete code. Record an assumption as proposed until the relevant owner resolves it. Continue independent work where that uncertainty has no effect.

A specification is ready for implementation when the affected behavior, contracts and acceptance criteria are clear; dependent open questions are resolved; and the constitution check is satisfied. Readiness does not require settling unrelated APOLO policies or later designer questions.

Use feature-document statuses `DRAFT`, `READY`, `IMPLEMENTED` and `SUPERSEDED`. These are documentation statuses, separate from runtime workflow-definition lifecycles. `IMPLEMENTED` means the specified implementation is present; acceptance evidence and production readiness must still be reported separately.

## 3. Plan the implementation

Create `plan.md` for substantive features. Reference the exact specification revision and applicable constitution principles. Describe affected modules/packages, public contracts, persistence/migrations, host adapters, failure handling, compatibility and the chosen verification scope.

Record alternatives only when they explain a consequential choice. Include a constitution check with PASS, NOT APPLICABLE or OPEN for relevant principles and a short justification. Resolve violations through a design change or constitutional amendment before dependent implementation.

For this project, explicitly inspect:

- Core/host dependency direction and public type independence.
- One host persistence/transaction boundary and one production migration runner.
- Workflow-version pinning, immutable publication, retained task codes and compatibility with pinned node contracts, including future starts of published workflows.
- Multiple tasks per activity without parallel process branches, keeping proposed completion semantics distinct from agreed scope.
- APOLO-owned instance-ID associations, authorized engine API queries and optional engine correlation; atomic creation of required host links.
- Pinned structured result contracts, authoritative completion history, authorized exact-activity retrieval, full-payload idempotency, same-transaction result visibility and operational retention (CON-13/ASM-10).
- JSON execution-variable declarations, whole-value replacements, bounded read-only path navigation, exact-value evidence and scalar compatibility (CON-14/ASM-11).
- Expression restrictions and fail-on-error routing.
- Authorization, concurrency, idempotency, audit and durable event handoff.
- Java target/runtime compatibility and consequences of the future APOLO source move.

## 4. Break the plan into traceable tasks

Use `tasks.md` for ordered, bounded implementation steps. Each task records its requirement IDs, dependencies, affected files/modules and completion condition. Explicitly identify enabling tasks such as build setup.

Prefer a small usable workflow slice over speculative infrastructure. Implement only the agreed scope. Include schema/document updates with the code they govern. New findings that change behavior require specification updates before proceeding with dependent implementation.

Small maintenance changes, dependency configuration and documentation corrections may use a short specification/plan/task record in the change description instead of four separate files. They still need a clear purpose, applicable constraints and honest validation evidence.

## 5. Record evidence and reconcile the specification

For substantive features, `evidence.md` records:

- Specification revision and relevant code revision or working-tree state.
- Environment, toolchain and the exact commands or manual actions performed.
- Requirement/scenario IDs covered, observations and resulting artifacts.
- PASS, FAIL, NOT RUN or BLOCKED for each planned check, with the reason.
- Known gaps, unresolved integration risks and whether production acceptance remains outstanding.

Mark an implementation task complete only when its stated completion condition is met. Do not describe an unexecuted check as passed or use a packaging result as proof of runtime semantics. A requirement-to-task-to-evidence link should be possible without reconstructing the conversation.

Before closing work, reconcile the specification, schema/examples, implementation and evidence. Record intentional behavior changes and migration/compatibility impact. A production release requires the mandatory behavioral guarantees to be supported by appropriate evidence.

## Verification and current no-tests policy

The owner has repeatedly instructed **no tests**. During current development, do not create or run automated tests unless the owner changes that instruction. Preserve existing test sources. This applies even where older design documents describe future required test layers.

For an authorized build, use JDK 21 and skip both test compilation and execution:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

Use document review, static inspection and appropriate build checks within the task's scope. Run application/database checks when authorized by the task. Starting the test host invokes Liquibase and may modify the development database; it is not equivalent to compilation. Stop processes started for temporary verification when finished unless the user asks to keep them running, and report their final state.

Keep acceptance scenarios and required evidence in specifications even when execution is deferred. Mark checks NOT RUN, including the reason. The current instruction does not remove the architecture's production requirements for rollback, concurrency, idempotency, expression restrictions, authorization and durable recovery. Do not claim those guarantees are verified or production-ready from a build or connection check. Resolve the verification scope with the owner when production acceptance becomes the task.

## First specification priorities

1. **Workflow Definition v1:** node/transition semantics, typed inputs/variables, synchronous service bindings, errors, lifecycle, publication validation and execution bounds (O1/O6).
2. **Expression Policy v1:** supported scalar and JSON path syntax/encodings, nullability, date/time rules, deterministic inputs, read-only allowlist, traversal/resource bounds and errors (O6/ASM-11).
3. **Task catalog and human-node contracts:** catalog administration and any import schema, immutable codes/retained rows, editable metadata/selectability, node-owned outcomes and inputs, and deployed-host capabilities by stable task code (O2). Specify collective completion behavior separately from the confirmed multiple-task scope; task-type business versions and publication lifecycle are superseded.
4. **Initial database schema and command integration:** [F001](features/F001-initial-database-schema/spec.md) now specifies the schema-only Liquibase slice from [database design v1.4](../../apolo-docs/workflow-engine/architecture/Workflow_Engine_Database_Design_v1.md), including authorized local startup and database/changelog inspection. Specify runtime command integration separately before implementing it. Cover selected numeric JPA versions, early aggregate claims/final version capture, proposed physical constraints, optional business correlation, APOLO-owned associations, completion-record uniqueness/lookup, transaction participation, idempotency fingerprint/receipt rules, event handoff and audit (Q11/O7/O9).
5. **Structured task submissions:** specify node-declared result schema and limits, exact JSON normalization, authoritative TASK_COMPLETED evidence, authorized exact-task/activity APIs, same-transaction downstream reads, rollback, retention and compatibility (CON-13/ASM-10). Acceptance scenarios must include mixed per-item decisions without inventing host aggregation policy, draft saving or implicit result-to-variable propagation.
6. **JSON execution variables:** specify pinned declarations/validation, JSON column/type constraints, explicit start/service/command bindings, whole-value replacement, read/path APIs, canonicalization, evidence budgets and scalar compatibility (CON-14/ASM-11). Verify persistence dirty tracking, stale replacements, transaction visibility and expression restrictions; indexed JSON search needs separate justification.

These are planned artifacts, not a mandated implementation sequence. Apply the [source precedence](design-sources.md) when older architecture examples conflict with the selected database design changes. Exact schemas, interfaces, endpoints and unresolved physical details require specification; selected numeric JPA mapping does not establish verified host behavior. Decision Definition v1 and decision-table artifacts remain deferred.
