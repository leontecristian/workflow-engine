# Workflow project constitution

**Version:** 1.0.0

**Established:** 2026-10-24

**Last amended:** 2026-10-24

**Basis:** consolidated design v0.7, its decision register and subsequent owner decisions

**Scope:** `workflow-core`, `workflow-test-host`, their specifications and future APOLO integration

This constitution records the project's governing principles. MUST and MUST NOT denote requirements; SHOULD denotes a default whose departure needs a documented reason. Examples, suggested names and unresolved design details do not become requirements merely because they appear in a plan or code sample.

## C01 — Specifications lead implementation

Substantial behavior MUST have a versioned specification before implementation. The specification must describe the problem, scope, observable outcomes, inputs, errors, acceptance scenarios and unresolved decisions. Exact contracts must be settled before dependent code or migrations encode them.

Every implementation task MUST trace to a specified requirement or an explicitly identified enabling task. Implementation discoveries must feed back into the specification. Existing code is evidence of current behavior, not automatic authority to change an agreed contract.

Use AGREED, PROPOSED, PROVISIONAL, OPEN, PENDING VERIFICATION and DEFERRED consistently. Do not convert a proposed table, interface, API path, numeric limit or booking policy into a confirmed requirement without recording the decision. Routine implementation choices within an agreed contract do not need a separate approval step.

## C02 — Deliver workflow execution first

The first capability is a small reusable workflow engine: a working sandbox workflow, then one cross-team booking approval integration in APOLO. The Angular workflow designer follows the first successful APOLO integration.

V1 includes `START`, `HUMAN_TASK`, `SERVICE_TASK`, `DECISION` and `END`, one active process position, one directly assigned human task per activity, a persistent task-type registry, restricted expression routing, relational persistence and authoritative history.

The standalone business decision engine is optional later work. Initial delivery MUST operate without decision tables, FIRST/UNIQUE policies, separate decision-definition storage or decision-management APIs. Do not add placeholder modules or schema for that extension.

Claim/release, candidate groups, general delegation, timers, expiration, collective approval, parallel orchestration, subprocesses and live migration of active instances remain deferred. Audited administrative reassignment does not authorize a general delegation feature. New capabilities require a concrete need and a specification; changes to this scope require an amendment.

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

Optimistic concurrency MUST prevent conflicting commands from advancing the same instance twice. Command idempotency MUST return the committed result for an identical retry and reject reuse of the identifier with different input. Access to a previous result must be reauthorized. Exact token representation, request normalization, lock ordering and JPA mapping require specification and host verification.

Registered `SERVICE_TASK` actions execute synchronously with declared, validated inputs/outputs. Failure rolls back the command. Workflow business actions belong in service nodes; transitions select the next node. Each business effect has one owner so host orchestration and a service node do not duplicate it.

Automatic execution must be bounded and reach a human task or an end node on success. Automatic-only cycles are rejected; exceeding the specified execution bound fails and rolls back. Exact limits remain specification work. Initial execution does not automatically retry failed business commands.

External delivery MUST follow a durable handoff recorded in the same transaction and occur after commit through host infrastructure. An after-commit callback alone does not establish durability. Delivery can repeat; consumers must be idempotent. These guarantees apply to every production deployment, including one preceding the designer.

## C07 — Published definitions and task types are immutable

Workflow definitions and task-type versions follow `DRAFT -> PUBLISHED -> RETIRED`. Validation results belong to an exact draft revision; `VALIDATED` is not a lifecycle status. Publication validates the current revision and target-host capabilities.

Published content is immutable, including embedded designer metadata. Instances pin exact workflow versions; human tasks refer to exact task-type versions. Retirement prevents new selection while retaining existing references and compatible support for active tasks.

Task-type administration MUST work through an API and versioned JSON import before the designer. Equivalent reimports return the existing version. Conflicting published content is rejected, including for retired versions. Draft replacement requires an explicit revision-checked update. Normalization and revision-token details must be specified.

Registry metadata is not executable behavior. Publication checks both registry references and capabilities supplied by the deployed host. Unsupported task types must fail visibly. APOLO resolves screens and handlers from `taskType + taskTypeVersion`; V1 has no separate form key/version contract.

## C08 — Expressions are restricted, explicit and deterministic

V1 routing uses restricted SpEL over validated `#facts` and `#vars` projections. Conditions MUST return actual Boolean values. Exactly one matching predicate selects a route; zero matches may use an explicit default. Multiple matches, missing required input, invalid conversion and evaluation errors MUST fail rather than become false or default approval.

Expression policy MUST allow only specified syntax and read-only access to bounded data. It must exclude type/constructor/bean access, general method invocation, assignment, mutation and access to arbitrary application objects. A restricted evaluation context alone is insufficient evidence of safety.

Time and other decision inputs must be supplied explicitly; expressions cannot fetch business data or read hidden mutable globals. Specify limits for expression size/complexity, inputs and evaluations, plus stable errors, before implementing evaluation.

Initial scalar types are STRING, LONG, DECIMAL, BOOLEAN, DATE and DATETIME. Preserve numeric precision and define explicit date/timestamp semantics. Exact JSON encoding, precision, nullability and timezone rules remain specification work. Unsupported versions and semantic features MUST be rejected.

## C09 — Host policy and trusted identity govern business actions

APOLO owns booking data, forms, validation, assignment sources, permissions, materiality rules and domain orchestration. Core MUST NOT encode overbooking calculations, country routing or alternative-resource policy.

Actors come from authenticated host context, never client-supplied identity. Command execution rechecks state, concurrency, authorization, assignment, inputs and guards; available-action queries are advisory. Clients may update only explicitly permitted inputs/variables.

The APOLO integration must enforce no self-approval, country/business access and audited reassignment/administrator overrides. Harmless booking edits may proceed; material changes invalidate review and require resubmission. APOLO defines materiality. Reusing the same instance for adjustment remains provisional.

Workflow history must explain task creation, assignment/reassignment, completion/cancellation and manual/automatic transitions, with the relevant actor, version, outcome and correlation. Store only necessary permitted evidence and deliberate snapshots. Full business entities remain host-owned; retention, access and redaction follow host policy.

## C10 — Compatibility and evidence are explicit

Compile against Java 17 APIs and bytecode; run on JDK 21. Each module declares its direct dependencies. Shared version management must not pull the host's entire dependency stack into core.

The standalone build baseline is evidence about this project, not proof of APOLO compatibility. Verify the effective APOLO dependency graph, persistence registration, transaction participation, flush/commit behavior and concurrency mapping before claiming integration readiness.

Specifications MUST identify evidence for their acceptance criteria. Build success, host startup and a database connection are distinct checks; none proves rollback, concurrency, expression restrictions or recovery by itself. Record unverified guarantees openly. Follow the current owner-directed verification scope described in the [development workflow](specification-driven-development.md#verification-and-current-no-tests-policy).

## Governance and amendments

The project owner decides changes to constitutional principles and disputed scope. Contributors may resolve ordinary implementation details within those principles and record the rationale in the feature plan. No new approval ceremony is required for routine compliant work.

A constitutional amendment must record the changed principle, reason, owner decision, affected specifications/design documents and any compatibility or migration consequence. Update the constitution and dependent documents together; do not leave an implementation-only exception that silently weakens a guarantee.

Use semantic versioning for this document: major for incompatible principle/scope changes, minor for added governance or principles, patch for clarifications that preserve meaning. This version is independent of library releases, JSON schema versions, definition versions and draft revisions.

The consolidated design remains the detailed architectural authority. This constitution codifies its confirmed principles and the owner's later decisions. Where a conflict lacks a superseding decision, record it and resolve it before implementing affected behavior. Historical archives and illustrative contracts cannot override current decisions.

## Amendment history

- **1.0.0 — 2026-10-24:** initial codification of the agreed architecture and specification-driven workflow. Exact feature contracts remain pending. Current no-tests instructions govern development execution without asserting production readiness.

See [design sources and decision status](design-sources.md) for principle traceability and open items.
