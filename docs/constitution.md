# Workflow project constitution

**Version:** 2.2.0

**Established:** 2026-10-24

**Last amended:** 2026-10-25

**Basis:** consolidated design v0.8, database design v1.4 and subsequent owner decisions; see [design sources](design-sources.md) for precedence and proposal status

**Scope:** `workflow-core`, `workflow-test-host`, their specifications and future APOLO integration

This constitution records the project's governing principles. MUST and MUST NOT denote requirements; SHOULD denotes a default whose departure needs a documented reason. Examples, suggested names and unresolved design details do not become requirements merely because they appear in a plan or code sample.

## C01 — Specifications lead implementation

Substantial behavior MUST have a versioned specification before implementation. The specification must describe the problem, scope, observable outcomes, inputs, errors, acceptance scenarios and unresolved decisions. Exact contracts must be settled before dependent code or migrations encode them.

Every implementation task MUST trace to a specified requirement or an explicitly identified enabling task. Implementation discoveries must feed back into the specification. Existing code is evidence of current behavior, not automatic authority to change an agreed contract.

Use AGREED, PROPOSED, PROVISIONAL, OPEN, PENDING VERIFICATION and DEFERRED consistently. Do not convert a proposed table, interface, API path, numeric limit or booking policy into a confirmed requirement without recording the decision. Routine implementation choices within an agreed contract do not need a separate approval step.

## C02 — Deliver workflow execution first

The first capability is a small reusable workflow engine: a working sandbox workflow, then one cross-team booking approval integration in APOLO. The Angular workflow designer follows the first successful APOLO integration.

V1 includes `START`, `HUMAN_TASK`, `SERVICE_TASK`, `DECISION` and `END`, one active process position, multiple directly assigned human tasks within an activity, a simple persistent task-type catalog, restricted expression routing, relational persistence and authoritative history. Multiple tasks do not introduce parallel process branches. Detailed collective completion rules, including the proposed SINGLE/ALL policies and fixed roster, require specification before implementation; they are not confirmed APOLO approval policy.

The standalone business decision engine is optional later work. Initial delivery MUST operate without decision tables, FIRST/UNIQUE policies, separate decision-definition storage or decision-management APIs. Do not add placeholder modules or schema for that extension.

Claim/release, candidate groups, general delegation, timers, expiration, advanced collective policies such as ANY/QUORUM, parallel orchestration, subprocesses and live migration of active instances remain deferred. Audited administrative reassignment does not authorize a general delegation feature. New capabilities require a concrete need and a specification; changes to this scope require an amendment.

## C03 — Keep two modules and a clear host boundary

Develop separately now with exactly two Maven modules:

- `workflow-core`: ordinary library JAR containing workflow models, JPA entities/repositories, services, expressions, codecs, neutral integration interfaces/configuration and workflow changelog resources.
- `workflow-test-host`: executable Spring Boot development host containing sample endpoints/adapters and the infrastructure needed to run core locally.

Use packages within core to separate responsibilities. Core MUST NOT depend on the test host, APOLO backend implementation, designer or a concrete optional business decision engine.

Core may use Spring/JPA internally. Public integration contracts MUST use standard Java or workflow-owned types, without exposing JPA entities, APOLO entities, security-provider objects, JDBC connections or messaging-provider classes. External users and business objects are referenced by stable identifiers.

Hosts own web endpoints, identity, authorization policy, business data/rules, datasource, transaction infrastructure, connection pooling, runtime configuration and external delivery. Core MUST NOT silently create another datasource, transaction manager, application context, security chain or web server, or mutate the host's global JSON mapper.

## C04 — Prepare for source integration into APOLO

`workflow-core` will become a sibling Maven module inside APOLO alongside backend, frontend and configuration. Backend depends on core. Core retains its library boundary while joining APOLO's build and release lifecycle. Keep one authoritative implementation.

The move MUST review APOLO's effective root parent, shared versions and plugin configuration before reparenting core. Backend is neither core's parent nor its code dependency. Preserve ordinary JAR packaging and avoid inheriting backend-only infrastructure dependencies.

Move timing, final coordinates and the test host's future location remain open. The test host stays outside backend's production dependency graph. A permanently separate repository or publishing lifecycle is not required.

## C05 — Core owns schema; the host owns migration execution

Core MUST package workflow migrations under `db/changelog/workflow/`, with the stable entry point `db/changelog/workflow/workflow-master.xml`.

The test host includes that master in its local Liquibase master. APOLO includes it in its existing master and remains the single production migration owner. Core MUST NOT create a `SpringLiquibase` runner or ship global migration application settings.

Preserve applied changeset identities and resource paths, including during the source move. Evolve schema through new ordered changesets. JPA MUST NOT independently create or update production schema. Host fixture migrations remain outside core's master.

Workflow migrations MUST work without APOLO-owned tables or foreign keys to them. Use workflow-internal constraints for workflow relationships and logical identifiers for external references. Definitions are versioned JSON; runtime state, tasks, variables, receipts and history are relational. Physical names, encodings and concurrency mappings require specification before schema implementation.

## C06 — Commands preserve atomicity and reliability

A successful command MUST atomically cover required synchronous business updates, workflow/variable changes, task changes, history, command receipt and durable event handoff. Workflow persistence participates in the host transaction; an independently committed business action violates this boundary.

Optimistic concurrency MUST protect the instance aggregate, including activity, task and variable mutations. The selected mapping is Hibernate-managed numeric `BIGINT lock_version`, represented by Java `Long` with JPA `@Version`. Every mutation of an existing instance MUST acquire an early version-checked instance write before business effects; a child-row update alone is insufficient. Results and receipts MUST capture the actual final managed version, not assume a single increment. The database design specifies the history-sequence reservation approach; provider behavior, flush ordering and shared transactions remain host verification gates.

Command idempotency MUST return the committed result for an identical authorized retry and reject reuse of the identifier with different input. Successful-receipt resolution precedes state-dependent stale checks and business mutations. Access to a previous result must be reauthorized. Request fingerprints include relevant host association inputs and optional business correlation. Exact wire encoding, request normalization and lock ordering require specification and host verification.

Registered `SERVICE_TASK` actions execute synchronously with declared, validated inputs/outputs. Failure rolls back the command. Workflow business actions belong in service nodes; transitions select the next node. Each business effect has one owner so host orchestration and a service node do not duplicate it.

Automatic execution must be bounded and reach a human task or an end node on success. Automatic-only cycles are rejected; exceeding the specified execution bound fails and rolls back. Exact limits remain specification work. Initial execution does not automatically retry failed business commands.

External delivery MUST follow a durable handoff recorded in the same transaction and occur after commit through host infrastructure. An after-commit callback alone does not establish durability. Delivery can repeat; consumers must be idempotent. These guarantees apply to every production deployment, including one preceding the designer.

## C07 — Published definitions are immutable; task codes are stable

Workflow definition versions follow `DRAFT -> PUBLISHED -> RETIRED`. Validation results belong to an exact draft revision; `VALIDATED` is not a lifecycle status. Publication validates the current revision and target-host capabilities.

Published workflow content is immutable, including embedded designer metadata. Instances pin exact workflow versions. The pinned human node owns permitted outcomes, input contracts, comment requirements, guards and completion policy. Workflow-version retirement prevents new selection while retaining existing references and compatible support.

Human nodes also declare whether a structured completion result is forbidden, optional or required and bind its contract through embedded schema or an immutable versioned reference. Publication MUST reject unsupported or mutable contracts. Existing simple tasks remain supported without a result. Retain readers, validators and host capabilities needed by published definitions and retained evidence; task-type business versions are not required for this capability.

Task types use a simple `workflow_task_type` catalog with codes immutable from creation, retained rows, editable descriptive metadata, an authoring `selectable` flag and a technical concurrency version. They have no business-version or publication lifecycle. Renaming codes and physically deleting rows MUST NOT be supported, even for unused entries. `selectable = false` prevents new authoring selection; it MUST NOT invalidate published definitions or prevent their execution, including new instances of those definitions. The activity holds the task-type reference; its tasks inherit that type. Catalog administration and any import contracts require specification; no versioned task-type import lifecycle is mandated.

Catalog metadata is not executable behavior. Publication checks catalog references, node contracts and capabilities supplied by the deployed host. Unsupported task types must fail visibly. APOLO resolves screens and handlers by stable task code and retains compatibility with published workflow contracts. V1 has no separate form key/version contract.

## C08 — Expressions are restricted, explicit and deterministic

V1 routing uses restricted SpEL over validated `#facts` and `#vars` projections. Conditions MUST return actual Boolean values. Exactly one matching predicate selects a route; zero matches may use an explicit default. Multiple matches, missing required input, invalid conversion and evaluation errors MUST fail rather than become false or default approval.

Expression policy MUST allow only specified syntax and read-only access to bounded data. It must exclude type/constructor/bean access, general method invocation, assignment, mutation and access to arbitrary application objects. A restricted evaluation context alone is insufficient evidence of safety.

Time and other decision inputs must be supplied explicitly; expressions cannot fetch business data or read hidden mutable globals. Specify limits for expression size/complexity, inputs and evaluations, plus stable errors, before implementing evaluation.

Initial scalar types are STRING, LONG, DECIMAL, BOOLEAN, DATE and DATETIME. Preserve numeric precision and define explicit date/timestamp semantics. Exact JSON encoding, precision, nullability and timezone rules remain specification work. Unsupported versions and semantic features MUST be rejected.

A task may submit a bounded JSON object containing nested objects/arrays under its pinned result contract. Core validates structure and bounds; the host validates business meaning. Reject duplicate JSON keys and inexact conversions. Schema syntax/features, payload/depth/collection/string limits and versioned canonicalization MUST be specified before implementation. Task submissions are not automatically exposed or merged into expressions/variables. Declared services may aggregate exact-activity submissions and return validated scalar or JSON outputs through explicit bindings without changing the original evidence.

JSON is an additional execution-variable type for objects and arrays, including nested combinations. Its declaration, validation contract, nullability and permitted writers belong to the pinned workflow definition. Current values are mutable execution state stored in workflow_variable.json_value; task submissions remain immutable. Preserve existing scalar types and absent/explicit-null semantics. JSON variables use SQL NULL for explicit top-level null; nested nulls are ordinary JSON data. No arbitrary Java-object serialization is permitted.

Initialize or replace complete JSON values only through declared start/service bindings or authorized commands, under the same instance concurrency and transaction protocol as other variables. Include the full submitted value in versioned fingerprints and retain required write/routing evidence independently of current state. Read-only object-key/array-index navigation under #vars must be explicitly allowlisted and bounded; missing required paths, type/null mismatches and invalid indices fail. No implicit deep merge, nested mutation, method access or arbitrary JSON-path search is enabled. Specify exact encodings, per-value/per-instance/envelope bounds, binding/path syntax and expression safety evidence before implementation. Storage does not imply indexed querying.

## C09 — Host policy and trusted identity govern business actions

APOLO owns booking data, forms, validation, assignment sources, permissions, materiality rules and domain orchestration. Core MUST NOT encode overbooking calculations, country routing or alternative-resource policy.

APOLO owns booking-to-workflow associations by storing the engine-issued instance ID in host data and using the engine API to retrieve instance state, tasks and history. Several bookings may share an instance ID. A booking column or host association table may represent the links according to cardinality and history needs; this constitution does not mandate a host table. APOLO resolves and authorizes related bookings when composing task details or applying business effects. Prior execution links and reviewed membership must remain available when current associations change. A JSON variable may hold a booking selection as execution context; it does not replace host associations or establish membership/authorization. Partial ALL contributions must not alter shared review material, including JSON variables.

Core retains separate optional `business_type` and `business_id` correlation fields. Both MUST be supplied together at creation or both be null, and the pair is immutable thereafter. Lookup is nonunique; the pair neither determines booking membership nor grants access. Possession of an instance ID likewise does not authorize access. Instance creation and required host links MUST share the host transaction and idempotency boundary, with links established before actions that need them. Host association migrations remain host-owned.

Actors come from authenticated host context, never client-supplied identity. Command execution rechecks state, concurrency, authorization, assignment, inputs and guards; available-action queries are advisory. Clients may update only explicitly permitted inputs/variables.

The APOLO integration must enforce no self-approval, country/business access and audited reassignment/administrator overrides. Harmless booking edits may proceed; material changes invalidate review and require resubmission. APOLO defines materiality. Reusing the same instance for adjustment remains provisional.

Workflow history must explain task creation, assignment/reassignment, completion/cancellation and manual/automatic transitions, with the relevant actor, version, outcome and correlation. Store only necessary permitted evidence and deliberate snapshots. Full business entities remain host-owned; retention, access and redaction follow host policy.

Core owns the immutable accepted task submission: outcome, comment, permitted scalar inputs and optional structured result, stored once in TASK_COMPLETED.details_json. Every completed task MUST have one authoritative completion record; open/cancelled tasks have none. Supported authorized APIs retrieve exact-task/activity submissions without requiring hosts to parse arbitrary history. Host policy governs visibility between reviewers. The JSON records decisions, not authoritative business membership or the material reviewed; APOLO owns business validation, aggregation, effects and review evidence.

The complete result participates in the versioned command fingerprint. Submission acceptance, completion history, business effects and receipt MUST share the command transaction. Downstream actions receive exact completed activity context and can read the final submission before commit; rollback removes that command's result with its effects. Successful receipt replay does not rerun state-dependent validation. Retention/redaction MUST account for operational dependencies and audit obligations; missing operational evidence must be explicit, never silently interpreted as an empty decision. Draft/partial task saving and ordinary result edits remain outside this extension.

## C10 — Compatibility and evidence are explicit

Compile against Java 17 APIs and bytecode; run on JDK 21. Each module declares its direct dependencies. Shared version management must not pull the host's entire dependency stack into core.

The standalone build baseline is evidence about this project, not proof of APOLO compatibility. Verify the effective APOLO dependency graph, persistence registration, transaction participation, flush/commit behavior and concurrency mapping before claiming integration readiness.

Specifications MUST identify evidence for their acceptance criteria. Build success, host startup and a database connection are distinct checks; none proves rollback, concurrency, expression restrictions or recovery by itself. Record unverified guarantees openly. Follow the current owner-directed verification scope described in the [development workflow](specification-driven-development.md#verification-and-current-no-tests-policy).

## Governance and amendments

The project owner decides changes to constitutional principles and disputed scope. Contributors may resolve ordinary implementation details within those principles and record the rationale in the feature plan. No new approval ceremony is required for routine compliant work.

A constitutional amendment must record the changed principle, reason, owner decision, affected specifications/design documents and any compatibility or migration consequence. Update the constitution and dependent documents together; do not leave an implementation-only exception that silently weakens a guarantee.

Use semantic versioning for this document: major for incompatible principle/scope changes, minor for added governance or principles, patch for clarifications that preserve meaning. This version is independent of library releases, JSON schema versions, definition versions and draft revisions.

The consolidated design and database design remain the detailed architectural sources, with scoped precedence recorded in [design sources](design-sources.md). This constitution codifies their confirmed principles and the owner's later decisions. Database design v1.4 retains earlier selected changes and extends the scalar-only execution-variable model with JSON. Structured submissions remain distinct from mutable variables. Other proposed details remain proposals. Where a conflict lacks a superseding decision, record it and resolve it before implementing affected behavior. Historical archives and illustrative contracts cannot override current decisions.

## Amendment history

- **2.2.0 — 2026-10-25:** owner-selected additive JSON workflow-variable capability from database design v1.4. C08 adds pinned typed JSON declarations, explicit whole-value bindings, bounded read-only navigation and evidence/idempotency requirements; C09 preserves host ownership and frozen review material. Updated constraints, assumptions, sources, development guidance and agent instructions together. Existing scalar behavior and immutable task results remain supported. The proposed schema adds json_value and CHECK changes; deployed schemas require forward migration and compatible codecs before JSON-enabled definitions. Exact contracts/limits and provider/expression verification remain pending; this amendment does not implement the capability.
- **2.1.0 — 2026-10-25:** owner-approved additive structured task-submission capability from database design v1.3. C07 pins result contracts; C08 separates bounded JSON submissions from scalar execution variables; C09 defines authoritative completion history, authorized retrieval, host interpretation, atomicity, idempotency and operational retention. Updated constraints, assumptions, source traceability, development guidance and agent instructions together. Exact contracts/limits and integration evidence remain pending. Existing simple completions remain supported. No new result table/column is selected; completion constraints/indexes and event/definition codec compatibility require specification and a forward migration if an older schema was applied. No implementation or database changes are claimed.
- **2.0.0 — 2026-10-25:** owner-approved reconciliation with database design v1.2. C02 includes multiple tasks per activity; C07 replaces versioned task types with retained immutable codes and workflow-node contracts; C06 records numeric JPA versions and aggregate concurrency; C09 makes APOLO's instance-ID associations and optional two-column correlation explicit. These decisions supersede incompatible 1.0.0 scope/contracts. Updated constraints, assumptions, source traceability, development priorities, documentation guides and agent instructions together. Detailed completion policies remain proposed and host behavior remains unverified. This is a documentation amendment, not a schema migration or implementation claim; any previously applied incompatible schema requires a separate forward migration preserving changeset identity, and host association migrations belong to APOLO.
- **1.0.0 — 2026-10-24:** initial codification of the agreed architecture and specification-driven workflow. Exact feature contracts remain pending. Current no-tests instructions govern development execution without asserting production readiness.

See [design sources and decision status](design-sources.md) for principle traceability and open items.

Implementation evidence, 2026-10-25: [F001](features/F001-initial-database-schema/evidence.md) implements the initial nine-table schema and records local migration, complete catalog/tracking inspection and same-artifact restart verification with both temporary hosts stopped. This evidence does not amend the principles or establish runtime command guarantees, JPA concurrency behavior or APOLO production compatibility.
