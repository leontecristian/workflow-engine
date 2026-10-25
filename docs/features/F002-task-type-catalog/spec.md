# F002 — Task type catalog management

| Attribute | Value |
| --- | --- |
| Status | IMPLEMENTED revision 3; fresh build/proxy/startup/read-only verification PASS; AC01–AC09 behavioral execution NOT RUN |
| Revision | 3 — 2026-10-25; concrete service by default |
| Owner | Workflow project owner |
| Constitution | [v2.2.0](../../constitution.md) |
| Prerequisite | [F001 initial schema](../F001-initial-database-schema/spec.md), implemented in `4a7bf64c632b6e07216a5b285265f5ab5a0387ec` |

## Outcome and source

Provide a core Java service that lets a trusted host create, find, list and update task types. Each type has a permanent code, editable descriptive metadata, an authoring-selectability flag and an optimistic-lock token. This is the first persistence/service slice above F001; it does not execute tasks.

Authority: [database design v1.4, section 8.1](../../../../apolo-docs/workflow-engine/architecture/Workflow_Engine_Database_Design_v1.md#81-task-type-catalog), at `apolo-docs` commit `c32ffb07cc9f4f2f3d1d7bf087455c1af13acbc3`; [F001 schema contract](../F001-initial-database-schema/schema-contract.md); [source precedence](../../design-sources.md); CON-01/02/03/04/08/10/12. The operation names, validation rules, pagination and host transaction contract below are selected for F002, not claims of pre-existing APIs.

Initially prepared as documentation only, F002 is implemented through revision 3, including the owner's concrete-service convention and fresh wiring/proxy verification. Historical checks and behavioral gaps remain explicit in [evidence.md](evidence.md). Follow [plan.md](plan.md), [tasks.md](tasks.md) and [verification.md](verification.md).

## Scope and ownership

Implement one catalog persistence model/repository and one supported Java service in `workflow-core`, with workflow-owned immutable commands/results. Use the existing `wf.workflow_task_type` table and current host scanning, datasource and transaction manager. No schema change is required.

The host authenticates administrators, authorizes reads/writes and persists management audit. Core enforces input validity, code uniqueness/immutability and version-checked mutation. Catalog calls are trusted in-process integration operations, not unauthenticated endpoints. The transaction and audit handoff below is part of the integration contract; F002 does not invent an APOLO audit store or security provider.

Excluded: REST/HTTP endpoints, UI, import/export/bulk upsert, seeds, deletes/renames, task-type business versions or publication states, workflow-definition implementation, task creation/completion, expression execution, host capability registration, runtime command receipts/history/outbox, production integration and automated tests. Human-node outcomes, inputs, comments, results and completion policies remain in the pinned workflow definition, not the catalog.

## Java service contract

Expose the concrete Spring-managed `TaskTypeCatalogService` under `core.service.tasktype`, with package-private TaskTypeValidation and TaskTypePersistenceErrors alongside it. Remove the former TaskTypeCatalog interface, consolidate JpaTaskTypeCatalog into the service, and remove the obsolete `impl` package. Commands/queries/result DTOs/filter enum remain under `core.dto.tasktype`, documented errors under `core.exception.tasktype`, entities under `core.entity.tasktype` and repositories under `core.repository.tasktype`; see [exact package assignments](../../package-structure.md#exact-f002-assignments). Preserve operation signatures, behavior, transaction interception and host preconditions; move interface documentation to the concrete service. Public data carriers use Java 17-compatible records or equivalent immutable types; do not expose entities, repositories, Spring Data Page/Pageable, provider exceptions as business errors, or security-provider objects.

| Operation | Input | Output |
| --- | --- | --- |
| `create` | `CreateTaskType(code, name, description, selectable)` | `TaskTypeChange` with absent before, persisted after, changed=true |
| `findById` | positive `long id` | `Optional<TaskTypeView>` |
| `findByCode` | valid exact `String code` | `Optional<TaskTypeView>` |
| `list` | `TaskTypeQuery(filter, afterId, limit)` | `TaskTypePage` |
| `update` | `UpdateTaskType(id, expectedVersion, name, description, selectable)` | `TaskTypeChange` with before and after snapshots |

`TaskTypeView` contains `long id`, `String code`, `String name`, nullable `String description`, `boolean selectable`, and `long lockVersion`. `TaskTypeChange` contains `Optional<TaskTypeView> before`, `TaskTypeView after`, and `boolean changed`. Result objects are detached snapshots, not live managed objects. No public update command accepts code or lets a caller assign ID/version. There is no delete or rename operation. One update replaces all three mutable fields; a null description clears it, not "leave unchanged."

### Input rules

- Commands/query/filter must be non-null. ID must be positive and expectedVersion nonnegative. All long values retain full precision inside Java; future transports must specify safe wire encoding separately.
- Code is case-sensitive, 1–100 ASCII characters, matching `[A-Za-z][A-Za-z0-9._-]{0,99}` for the entire string. Reject surrounding whitespace or invalid characters; never trim, case-fold or silently normalize. `BOOKING_REVIEW` and `booking_review` are distinct codes under the selected BIN2 collation. The same validation applies to exact-code reads.
- Name is non-null, not blank under Java `String.isBlank()`, and at most 200 UTF-16 code units. Description is null or at most 2000 UTF-16 code units; an empty description is allowed and remains distinct from null. Preserve accepted text exactly, including Unicode and whitespace; do not silently truncate. These limits match SQL Server NVARCHAR storage. Host rendering must treat this metadata as text.
- Selectable is explicit in both mutation commands; there is no hidden true/false default. Validate before persistence and before returning a no-op result.

### Lookup and listing

Exact ID/code lookups include nonselectable rows. Unknown valid identity returns Optional.empty; malformed input is an error. Do not make code lookup implicitly authoring-only: published definitions may still depend on a nonselectable type.

`TaskTypeQuery.filter` is one of ALL, SELECTABLE, NONSELECTABLE. `afterId` is a nonnegative long (0 starts the list); `limit` is 1–100. Return matches with `id > afterId`, ordered by ascending ID, at most limit items. Fetch at most limit+1 to establish whether another match exists. `TaskTypePage` contains an immutable item list and nullable `Long nextAfterId`; set that cursor to the last returned ID only when an additional matching row was observed. Empty/end pages have a null cursor. The cursor need not identify an existing row. Do not issue an unbounded query or return total counts.

Pages are separate current reads, not a snapshot across requests. Concurrent inserts or selectability changes may change later page membership. Keep the same filter when continuing a page sequence. No arbitrary sorting, substring search or permission filtering is added; the trusted host authorizes catalog access before calling.

### Mutation, transactions and concurrency

Create/update require an existing host-managed transaction and participate in it using MANDATORY propagation or equivalent enforced semantics. Missing transaction fails before writing; it is an integration error, not permission for core to create its own commit. Read methods may open a read-only transaction when none exists and must join an existing host transaction when called inside it.

Map `id` as generated IDENTITY and `lockVersion` as a nullable Java `Long` with JPA `@Version` on the existing BIGINT column. Hibernate initializes/increments it; application code must not assign it, increment it or replace it with SQL ROWVERSION. Map code as non-updatable and provide no mutation method for it. No cascade/orphan deletion or public repository delete facility is part of the supported API.

For create: validate, insert, explicitly flush, and return the actual generated ID/provider-managed version. The database unique constraint is the authority for duplicate codes, including concurrent creation. An advisory lookup cannot replace it. Creation is not upsert or idempotent replay; even an identical second create conflicts.

For update: validate, load the row, reject missing ID, compare expectedVersion with the managed version, and capture an immutable before snapshot. Replace only name/description/selectable. For a real change, flush the versioned update before constructing the after snapshot. The database version predicate must detect another transaction's winning update, not merely an in-memory comparison. Return the actual managed version after flush; do not calculate expectedVersion+1. No bulk/native update or blind detached-entity merge may bypass this protocol. Do not automatically retry.

If all mutable values are exactly equal, return changed=false with equal before/after snapshots and the observed version, after still checking expectedVersion. No database write, forced increment or change-audit record is required. This is an observation with no mutation, not a reservation that prevents a concurrent later change. A stale expectedVersion is rejected even if the submitted values now match. Any successful result/version can become stale after another transaction commits.

### Host authorization and management audit

The host's administrator operation must:

1. Obtain trusted actor identity and authorize the requested operation before exposing catalog data or invoking a write.
2. Enter its transaction, invoke the core service through the managed Spring bean, and receive the flushed change snapshots.
3. For a real change, persist a management audit record in that same transaction containing trusted actor/time, operation, stable code/ID, and complete before/after values including versions. Create has no before value. Core does not accept actor/time as client-supplied catalog fields.
4. Commit before reporting success to the external caller. Audit failure or any command error rolls back the catalog write too. Never catch a flush failure and continue using the failed transaction, publish a success response before commit, or use an asynchronous/log-only audit substitute.

The core return is provisional until the host commits. Host transaction failure must not be hidden by a previously returned TaskTypeChange. No-op attempts can be recorded under host policy but must not be fabricated as catalog changes. F002 supplies the result and transaction boundary needed for this integration; durable host audit and authorization adapters are not implemented or verified by this library-only feature. No no-op audit/security adapter or unauthenticated development endpoint should be added to make wiring appear complete.

Management audit belongs outside `workflow_history`: a catalog edit has no workflow instance or command-receipt identity. Do not create a synthetic instance, use runtime receipts, or write fake runtime events.

### Errors

Expose a workflow-owned unchecked `TaskTypeCatalogException` with a stable category and bounded non-sensitive context. Mutation failures must retain rollback semantics for the joined transaction; translating a provider exception must not permit a partial commit. Preserve underlying causes for internal diagnosis, without returning SQL, provider objects or stack traces as public payloads.

| Category | Meaning |
| --- | --- |
| INVALID_INPUT | Violates the input/query rules; include the field name without echoing full submitted text. |
| NOT_FOUND | A valid update ID does not exist. Exact reads instead return Optional.empty. |
| DUPLICATE_CODE | The code is already reserved, including a nonselectable row or a concurrent insert. No automatic metadata update or code reuse. |
| STALE_VERSION | Expected version differs, or the versioned write loses a race. No successful change result; host rolls back. |
| STORAGE_FAILURE | Other persistence failure; preserve internal cause, do not misclassify every integrity failure as a duplicate code. |

Translate duplicate/optimistic failures from the explicit flush where possible, including the provider's wrapped forms; identify the code unique-constraint violation, not any arbitrary constraint failure. A preflight duplicate read alone does not cover races. Do not query for recovery on a transaction invalidated by the flush. Missing host transaction, missing persistence infrastructure and commit-time failures remain host integration failures; the host must translate them at its own boundary. No HTTP status/error-envelope contract is selected here.

## Requirements and acceptance

| ID | Requirement |
| --- | --- |
| F002-R01 | Implement the specified Java service over the existing six-column catalog, with internal JPA persistence and detached immutable results. |
| F002-R02 | Enforce exact input rules, unique permanent codes and create semantics without renames, deletion or upsert. |
| F002-R03 | Provide exact reads including disabled entries and bounded, ordered filter/cursor listing. |
| F002-R04 | Apply whole-metadata updates with expected-version checks, actual post-flush versions, race protection and the specified no-op behavior. |
| F002-R05 | Preserve host transactions and provide before/after audit evidence; document required host authorization/audit/commit handling. |
| F002-R06 | Preserve authoring-only selectability: disabling leaves code/ID retained and resolvable, with no deletion or published-workflow updates. Re-enabling is a normal versioned metadata change. |
| F002-R07 | Expose the specified errors, with no automatic retry, swallowed transaction failure or provider leakage in the public contract. |
| F002-R08 | Preserve F001 changesets and infrastructure, follow the no-tests policy, and record actual verification and deferred runtime evidence accurately. |

| Scenario | Given / When / Then | Requirements |
| --- | --- | --- |
| F002-AC01 | In a host transaction, create BOOKING_REVIEW with valid fields; after host audit/commit, exact ID/code reads return preserved values and generated identity/version. | R01/02/05 |
| F002-AC02 | Reject invalid/null/blank/overlength input and invalid query bounds without successful writes. Cover code lengths 100/101, name 200/201, description 2000/2001 UTF-16 units, null/empty description, and limits 1/100 versus 0/101. | R02/03/07 |
| F002-AC03 | Creating an existing code, even with identical data or selectable=false, conflicts. Case-distinct valid codes remain distinct. In concurrent same-code creation at most one commits; the other reports DUPLICATE_CODE if the winning insert commits. | R02/07 |
| F002-AC04 | Update with the current version changes only mutable fields; the returned version is the provider's actual flushed value. A stale version fails with no partial committed update. | R04/05/07 |
| F002-AC05 | Two real updates from the same version cannot both commit successfully; the loser reports STALE_VERSION. No automatic retry masks the conflict. | R04/07 |
| F002-AC06 | An exact no-op with current expectedVersion returns changed=false and equal snapshots without an increment; a stale no-op still fails. | R04 |
| F002-AC07 | Disable a type: it disappears from SELECTABLE listing but remains in ALL/NONSELECTABLE and exact lookups with the same identity/code. Re-enable through versioned update. No dependent records are changed. | R03/06 |
| F002-AC08 | Read absent IDs/codes as Optional.empty; update an absent ID as NOT_FOUND. Page static records in ID order, with bounded size and correct next/end cursors; invalid filter/cursor/limit fails. | R03/07 |
| F002-AC09 | Missing host transaction fails before mutation. In an existing host transaction, rollback or required audit failure leaves no committed catalog mutation. Core cannot independently commit. | R05/07 |
| F002-AC10 | Public API has no code update/delete, lifecycle/versioned task-contract fields, entities or provider pagination types. Host guards are documented; no anonymous controller/no-op audit implementation is supplied. | R01/02/05/06 |
| F002-AC11 | Build with tests skipped and start the local host with JPA validate; existing Liquibase records/checksums remain unchanged and no seeds/catalog mutations occur at startup. | R08 |

These are acceptance scenarios, not authorization to create automated tests. [Verification](verification.md) distinguishes authorized build/startup/read-only inspection from deferred behavioral execution. F002 may be IMPLEMENTED with explicit verification gaps; that does not mean production acceptance.

## Deferred integration and assumptions

- **ASM-02:** real host catalog writes and its audit must share the transaction. F002 enforces joining and supplies snapshots; concrete APOLO authorization/audit wiring and atomicity evidence are required before exposing administrative operations to users.
- **ASM-03:** numeric JPA version initialization, race handling and final version capture must be supported by the actual provider. Mapping/startup alone does not prove concurrency behavior; retain AC04/05/09 evidence gaps until observed.
- **ASM-05:** catalog existence/selectability does not prove executable host support. Definition validation/capability checks remain later features. Nonselectable exact lookup preserves the necessary primitive, but F002 cannot prove execution of published definitions before that engine exists.
- **ASM-01/08/09:** current standalone dependencies/local environment are the implementation baseline; production alignment and the later source move remain deferred.
- No unresolved business policy blocks the core-service contract. Host adapters, wire encoding and future definition semantics are explicitly outside this feature; do not guess them during implementation.
