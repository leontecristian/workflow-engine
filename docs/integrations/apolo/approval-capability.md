# Implement a Reusable Workflow Engine Module for Booking approval

> **Updated 2026-10-25 — APOLO approval-basis policy.** Read the [consolidated design](../../architecture/workflow-engine-design.md) with the later selected changes in [database design v1.4](../../architecture/Workflow_Engine_Database_Design_v1.md), which supersede older engine scope/registry examples below. Java 17 is the compilation target; JDK 21 is the runtime. OPEN, PROVISIONAL and DEFERRED items below are not implementation decisions.

## Objective

Develop a reusable workflow engine that supports configurable process definitions, workflow execution, human tasks, persistence, auditing, and workflow design.

The workflow engine is developed separately from Apolo for now. Eventually `workflow-core` becomes a Maven module inside Apolo's source tree, alongside backend, frontend and configuration. Backend depends on its ordinary library JAR, and core participates in Apolo's build/release lifecycle while remaining independent of backend implementation classes.

The engine must remain independent of Apolo-specific business domains, frontend components, security implementations, messaging infrastructure, and persistence entities.



## Why develop a reusable workflow capability instead of a booking-specific approval flow?

A booking-specific implementation would likely deliver the first approval scenario faster. However, the current requirements already describe several booking approval variants, including cross-team booking, overbooking, cross-border allocation, clarification requests, adjustment loops, alternative proposals, and different approver-routing rules.

Implementing each scenario directly in the booking functionality would repeat the same technical capabilities:

* Starting an approval process
* Selecting the appropriate approver
* Creating and assigning human tasks
* Supporting approve, reject, clarify, and adjust outcomes
* Tracking the current approval status
* Recording decisions and audit history
* Sending notifications
* Managing future changes to the approval sequence

Apolo already contains a budget and planning approval workflow, but that implementation is tightly coupled to those domains and cannot be reused for booking approvals. Building another booking-specific workflow in the same way would reproduce that limitation and create another isolated implementation to maintain.

The proposal is therefore not to build a large, general-purpose workflow product before delivering booking approval. It is to extract the minimum reusable workflow capabilities required by the booking scenarios and use booking approval as the first implementation.

The separation would be:

* The workflow capability manages process state, routing, human tasks, decisions, history, and transitions.
* The booking functionality continues to own booking data, booking validation, authorization, user interfaces, and the business consequences of approval or rejection.

This approach provides an immediate solution for booking approval while reducing duplication between its different approval scenarios. It also creates a controlled path for replacing or improving the existing budget and planning workflow in the future, without making that migration a prerequisite for the booking feature.

The reusable approach requires a higher initial investment than a single hard-coded approval flow. That investment is justified if booking approval has multiple variants or is expected to evolve. If the confirmed requirement is only one simple and stable approval sequence, a booking-specific implementation may be more cost-effective.

For this reason, the proposed scope should remain focused: implement only the reusable capabilities required by the agreed booking approval requirements, rather than attempting to create a universal workflow engine.



## Why reduce future technical debt

A booking-specific implementation would reduce the initial development scope, but it would also create a second approval solution alongside the existing budget and planning workflow.

Because the current workflow is tightly coupled to budgets and planning, it cannot be reused for booking approvals. Implementing booking approval in the same way would repeat this architectural limitation and increase future technical debt.

Over time, Apolo could accumulate multiple approval implementations, each with separate logic for:

* Approval routing
* Human-task assignment
* Status management
* Approve, reject, and clarification actions
* Notifications
* Audit history
* Permissions
* Process changes

Any common improvement, regulatory requirement, security correction, or audit enhancement would then need to be implemented and tested separately in every approval flow. Differences between implementations would also make the application **harder to understand, support, and maintain.**

The proposed workflow capability addresses the common process mechanics once, while keeping booking-specific rules and interfaces inside the booking domain. This requires a higher initial investment, but reduces the cost and risk of adding or changing approval processes later.

The intention is not to build a universal workflow platform in advance. The intention is to avoid creating another isolated approval implementation that is likely to require extraction or replacement when additional approval scenarios are introduced.

The approach should therefore remain **generic by design but booking-driven in scope**. Only capabilities required by the confirmed booking requirements should be implemented initially.



## Why develop the workflow capability as a separate module?

The workflow capability is being developed as a separate module to create a clear boundary between reusable approval-process mechanics and booking-specific business functionality.

The workflow module will manage common capabilities such as:

* Process execution and status
* Human-task creation and assignment
* Approval routing and transitions
* Decision and task history
* Workflow-definition versioning
* Audit information
* Workflow events

The booking functionality will continue to own:

* Booking data
* Booking-specific validation
* Approval screens
* Business authorization
* Booking status changes
* The business consequences of approval or rejection

Keeping these responsibilities in separate modules prevents workflow logic from becoming dependent on booking entities, screens, and services. This is particularly important because the existing budget and planning workflow is already tightly coupled to those domains and cannot be reused by other processes.

A separate module also allows the workflow capability to be developed and tested independently, with a small standalone test application. Integration with Apolo can then occur through explicit APIs and contracts rather than direct dependencies on Apolo implementation classes.

This does not mean that the workflow engine must be deployed as a separate application or microservice. It can remain a library packaged and deployed together with the Apolo backend. The separation is primarily an architectural and development boundary.

The separate-module approach provides:

* Clear ownership and responsibilities
* Better control over dependencies
* Independent development and testing
* Lower risk of coupling to booking-specific implementation details
* Easier reuse for multiple booking approval scenarios
* A future option to reuse the capability for other processes
* Reduced extraction and migration effort if the workflow capability grows

A separate module introduces some additional setup, including its own build configuration, integration contracts, and testing environment. This effort is justified because reusability and separation are primary objectives of the proposed solution.

The module should remain focused on the capabilities required by booking approval. It should not become a large standalone platform or microservice unless future requirements provide a clear reason for that evolution.

## Agreed module responsibilities

Under the 2026-11-07 adoption in constitution 6.0.0, the agreed target has three Maven modules under the workflow parent/aggregator POM. The current implementation still has two; this amendment starts no implementation:

- **workflow-core:** a Spring/JPA-based library containing graph/task models, entities, repositories, workflow and registry services, restricted SpEL support, codecs, integration interfaces/configuration and workflow-specific Liquibase resources. Packages separate execution, persistence and integration responsibilities. Public service contracts remain independent of APOLO, JPA entities and security/messaging-provider types.
- **workflow-web:** ordinary Spring MVC library JAR depending on core, with reusable controllers/transport mapping and explicitly activated API groups. APOLO supplies required identity/access and business integration; core-only consumption remains possible.
- **workflow-test-host:** executable Spring Boot sandbox with sample business data, endpoints and reference adapters, host datasource/transaction configuration, JDBC driver, migration execution and SQL Server integration tests.

The separate **workflow-ui** Angular 19 library supplies generic pages/components and the later embedded designer, consumed by APOLO and the sandbox shell. It is not another Maven module. This retains the rejection of separate expression, persistence and starter artifacts. Core depends on neither host nor web/UI; web/core use ordinary JAR packaging, and only the test host uses executable Boot packaging in this project. The designer remains after first APOLO integration. A business decision engine remains optional and deferred. Exact web/UI contracts and source/build arrangements need specifications; host business rules and transaction/migration ownership remain unchanged.

At the eventual source move, add core to APOLO's reactor and align its parent and dependency versions with the APOLO root build. Core remains a sibling module of backend, with dependency direction `backend -> workflow-core`; inheriting the root build parent does not authorize a dependency on backend classes. Keep one authoritative core source tree. A permanent independent publishing lifecycle is not required. The move's timing, final coordinates and whether the test host moves into APOLO as an optional development module remain open.

## Technical compatibility

**AGREED:** Java 17 compilation target and JDK 21 runtime. The Java 17 POM configuration is current and correct. Verify the compiler JDK/toolchain separately and enforce Java 17 API/bytecode compatibility. Run sandbox and integration tests on JDK 21.

The existing notes quote Spring Boot 3.4.5 and other dependency versions. Their effective current versions, including Spring/JPA/Hibernate/JSON/Liquibase and the SQL Server driver, remain OPEN verification work. Match APOLO's supported baseline rather than selecting incompatible versions independently.

## Workflow and decision definitions

**AGREED graph:** explicit START, HUMAN_TASK, SERVICE_TASK, DECISION and END nodes. Workflows are sequential, with one active position.

Initial workflow definitions are immutable published JSON documents. Instances pin their workflow version and workflows pin task-type versions. Separate business decision definitions and their version references are deferred with the optional extension. Definition lifecycle is DRAFT, PUBLISHED and RETIRED. Validation results attach to a draft revision, and edits invalidate prior validation results; VALIDATED is not a separate status.

**Initial routing — revised 2026-10-31:** DECISION nodes evaluate restricted workflow expressions against #facts/#vars in explicitly declared priority order. The first true condition selects a transition and stops evaluation; later conditions are not evaluated. If all conditions are false, use the separately identified unconditional default or fail if absent. Errors in evaluated conditions fail the command without fallback. Publication validates every condition, and diagram layout must not change priority. Expressions cannot fetch business data or invoke arbitrary application code. A routing node does not require a business decision engine. See [the routing decision](../../architecture/workflow-definition-language-design.md#52-choosing-a-route-in-priority-order).

**Optional later business decisions:** retain business-value outputs mapped by workflows and FIRST/UNIQUE table policies as future design guidance. Their evaluator, versioned definitions, persistence, APIs, standalone simulation and editor are not part of the first stage. Initial publication must reject unsupported decision-table references rather than silently accepting them.

**AGREED — O1:** SERVICE_TASK nodes invoke registered host actions synchronously in the enclosing transaction, with declared/validated inputs, outputs and permitted variable updates. Failures roll back the command; no automatic business-command retries initially. Workflow-driven business actions belong in explicit service nodes, not transitions. APOLO retains request/domain validation and transaction ownership; each business effect has one owner. Automatic execution continues to a human task/end under a step limit.

**AGREED — O6:** use a small, strict contract with explicit transitions/bindings, scalar types STRING/LONG/DECIMAL/BOOLEAN/DATE/DATETIME and restricted Boolean expressions. Preserve decimal/large-integer precision, explicit dates and unambiguous timestamps. Missing required inputs and invalid expressions fail; unsupported schema versions and semantic features are rejected. **Specification pending:** exact node/transition/binding fields, JSON encodings, precision/null rules, expression allowlist, limits and error codes. Table-cell operators and business-decision contracts remain deferred.

## Human tasks — initial scope

One directly assigned approver per activity, with task creation, completion, cancellation and audit history. Approve, reject and request-changes are outcomes. OPEN, COMPLETED and CANCELLED are proposed status names; the exact wire contract remains specification work.

Tasks reference the business object and actor by stable identifiers. They pin the registered task-type key/version and expose its supported outcomes. No JPA associations to APOLO-owned entities are allowed.

Claiming/releasing, candidate groups, delegation, automatic expiration, collective approval and timers are deferred. Due dates, if included, are metadata only. Audited administrative reassignment is distinct from the deferred delegation feature.

## Persistent human-task registry

**AGREED:** maintain immutable published task-type versions containing a stable key/version, display metadata and supported outcomes. Workflow publication must verify exact task-type/outcome references and compatibility with the target host's supported types.

The registry is design-time metadata, not executable behavior. It must not store Java class or Spring bean names, frontend components, APOLO API routes/entities, Keycloak types or messaging destinations. APOLO maps supported task types to its own screens and business services. It need not query the registry for routine task execution.

**AGREED — O2:** registry administration API with versioned JSON import before the graphical designer; the later designer uses the same API. Task types follow DRAFT/PUBLISHED/RETIRED. Import/edit drafts, then publish immutable versions; retirement prevents new selection while preserving existing references and active-task compatibility. Equivalent same-key/version reimports return the existing result; different content for a published or subsequently retired version is rejected. Draft changes require explicit revision-checked updates.

A capability provider supplied by the deployed host reports supported task-type versions, outcomes and registered extensions. Publication checks it as well as the registry. Importing catalog metadata does not register executable handlers or prove screen support; deployments must retain compatible screens/handlers for active tasks. **Specification pending:** exact API/import schema, content normalization before checksum comparison, revision-token representation and provider interface.

## Business and security boundary

APOLO owns booking fields, validation, business authorization, approval screens and business effects. A booking-specific endpoint/service owns orchestration and the transaction, invoking the library through neutral APIs. Generic task completion must not bypass booking checks.

**AGREED screen resolution:** use taskType + taskTypeVersion to select the APOLO-owned screen/handler; no separate formKey/formVersion fields in V1. The task API supplies metadata and a business reference; APOLO's domain API supplies business fields. Store only permitted completion/audit evidence and deliberate decision snapshots in workflow storage, not the full booking form. Unsupported type versions fail visibly. Active tasks require compatible host screen/handler support; the definition designer is a separate later UI.

**AGREED policies:** no self-approval; host country/business access checks; audited reassignment and administrator overrides. Authenticated actors are supplied by the host, never trusted from client input. The engine has no direct Keycloak/security-provider dependency. Separate definition-publication permissions from task-execution permissions.

**AGREED approval-basis policy:** an immutable, identified baseline covers all approval steps in an attempt. APOLO combines application edit restrictions, a host-owned database guard for protected writes and transaction-time checks of membership, protected material and eligibility at each decision and finalization. Detected material changes block further approval of that attempt and require fresh review while preserving earlier evidence. Harmless edits remain subject to host policy. The authoritative rule and implementation boundaries are in [Approval baseline and protected material](booking-business-analysis.md#approval-baseline-and-protected-material).

APOLO may preserve the baseline in a generic JSON variable such as approvalBaseline, enforcing write-once behavior for that attempt through its ordinary engine integration. The engine owns generic storage, history and command atomicity; it neither recognizes that variable name nor interprets booking changes. APOLO owns comparison, protected-write enforcement and any cancellation/resubmission/correction response. Detailed guard implementation, protected fields, locking and historical retention belong in the APOLO booking approval feature specification.

**PROVISIONAL:** adjustment uses the same instance with a new identified approval attempt/baseline and review activity on resubmission, retaining the prior basis and decisions. Sequential steps in one attempt keep the same baseline. Do not implement same-instance adjustment as a confirmed booking rule yet.

**DEFERRED — APOLO:** overbooking calculations, partial confirmation, approver routing/source, cross-border alternatives, material-field lists and concrete access mappings. Alternative-resource handling is undecided.

## Persistence, migrations and reliability

`workflow-core` owns workflow entities, repositories, tables and migration resources, including workflow definition versions, instances, variables, tasks/activities, registry types/outcomes, workflow routing history and command receipts. Separate business-decision definitions, dependencies and evaluation tables are deferred and must not be required initially. Runtime is relational; definitions are JSON with indexed metadata.

Use foreign keys within workflow-owned data; external records are referenced by stable identifiers, without requiring APOLO tables to create the standalone schema.

`workflow-test-host` includes core's `db/changelog/workflow/workflow-master.xml` in its own Liquibase master and runs migrations against a disposable local SQL Server database. APOLO includes the same resource in its existing master and remains the single production migration owner. Core must not declare its own `SpringLiquibase` bean or global Liquibase settings. Preserve applied changesets and their resource paths; add new ordered changesets for schema evolution. JPA must not independently create/update production tables. Host sample-data migrations remain outside the workflow master.

Preserve those classpath paths and changeset identities when core moves into APOLO. Register workflow entities/repositories alongside APOLO's existing packages in the same persistence context and transaction manager; source relocation must not create a second persistence or migration lifecycle.

**AGREED production requirements:**

- Atomic business updates, workflow advancement, task completion/creation, history, command receipt and durable event handoff.
- Concurrency protection against simultaneous conflicting approvals.
- Command idempotency: identical retries return the committed result; reuse of an identifier for different input conflicts.
- Synchronous workflow commands and durable event handoff with retry-safe consumers; external effects occur after commit through host infrastructure. Required approval business updates remain inside the atomic transaction.
- Rollback verification in both directions against SQL Server, including persistence flush/commit failures.

The library owns neutral workflow/task event meaning and authoritative task history, including assignment/reassignment and completion/cancellation. APOLO owns domain events, transaction configuration, broker/email delivery, retries/recovery, migration execution and retention policy. The engine defines neutral integration contracts and required guarantees; the sandbox supplies reference adapters. Host ownership does not make durability optional.

**PENDING VERIFICATION — O7:** entity-manager and transaction-manager participation, locking/version mapping, flush behavior, compiler toolchain and effective dependencies. Neither a compiler JDK nor a concurrency-token mapping is newly selected. Durable-handoff adapter and other APOLO infrastructure details remain deferred to production integration planning.

## Delivery and acceptance criteria

### Before substantial implementation

1. Specify Workflow Definition v1 including its expression rules with schemas and executable examples. Defer Decision Definition v1 until the optional business decision component is selected.
2. Formalize agreed O1/O2/O6 in service bindings, registry API/import/capability contracts and strict workflow/expression schemas, including normalization, limits and errors.
3. Verify remaining platform dependencies and establish a Java 17 target / JDK 21 runtime build-and-test configuration.

### First working sandbox

1. Build independently of APOLO and start a minimal Spring Boot host.
2. Execute a simple workflow using the five agreed node types; validate synchronous service inputs/outputs, rollback on failure, bounded automatic execution and absence of transition business actions/automatic command retries.
3. Persist one directly assigned human task; completion/cancellation advances or terminates execution according to the definition.
4. Create/version task types through API/JSON import without a designer; verify DRAFT/PUBLISHED/RETIRED, equivalent reimport, conflict rejection and revision-checked draft updates. Check deployed-host capabilities at publication and retain active-task compatibility.
5. Create, validate, publish and retire definitions; preserve immutable versions and pinned instances.
6. Execute expression-based workflow routing in declared priority order without a business decision module, table storage or decision-management APIs. Select the first true condition, stop evaluation, and use the explicit default only if all conditions are false. Errors in evaluated conditions fail without fallback; all false without a default also fails. Preserve priority through editing and validate every condition before publication.
7. Persist runtime/history through core's JPA persistence packages and run its migrations on SQL Server through the test host.
8. Demonstrate atomic rollback, concurrency protection, idempotency and durable handoff.
9. Keep public workflow integration contracts independent of JPA entities, APOLO business entities, security-provider objects and messaging transports. Core's internal persistence packages use JPA.

### First APOLO integration

1. Deliver one cross-team booking approval through an APOLO-owned booking endpoint.
2. APOLO supplies the scenario's business rules, assignment mapping and supported task types.
3. Verify host authorization, no self-approval, audited administrative changes, material-change review invalidation and shared-transaction rollback.
4. Verify host event-handoff integration, registry compatibility and screen/handler resolution by taskType + taskTypeVersion.
5. Prove dependency alignment and Java 17 compilation compatibility on the JDK 21 runtime.

Approval-basis acceptance criteria for that APOLO integration:

- Manager and later planning steps display and validate the same identified baseline, even when separated by days; entering another step does not recapture live data.
- Application writes and supported database write paths reject protected material/membership changes while pending. Exercise the host guard independently of the UI, including applicable batch/import paths; document its enforcement boundary.
- A membership addition/removal, protected-value difference or ineligible booking prevents a new decision/finalization. If a discrepancy exists despite prevention, previous decisions/baselines remain intact and the defined host recovery requires fresh review after a detected material change.
- A concurrent protected write cannot succeed unnoticed between comparison and commit, including membership changes and shared records accessed by different instances. Normal authorized finalization remains possible through the specified host protocol.
- Harmless edits are treated according to the declared materiality policy; an incidental booking row-version change is not automatically a material mismatch.
- Generic variable updates, task inputs and service outputs cannot overwrite an attempt's baseline. A new attempt retains the previous full basis and decision links before any variable-key reuse, and previous approvals do not transfer to it.
- Failed approval transactions commit neither completion/results nor business effects; durable invalidation/recovery is verified separately, including the interval before another decision. Restoring changed values must not silently revive an already disqualified attempt. The engine does not automatically cancel on host validation failure.
- Identical authorized successful retries replay their original result without a new baseline, decision or current-state approval check.

These are required acceptance scenarios, not executed checks or authorization to run automated tests; the current owner verification policy remains in force.

Unresolved business branches are not assumed to be implemented. Same-instance adjustment/resubmission tests depend on confirming the provisional choice.

### Later designer milestone

1. Run the Angular designer against sandbox or APOLO APIs.
2. Edit the five node types and select registered task types/outcomes.
3. Provide JSON inspection, validation and controlled publication.
4. Preserve supported semantics during visual/JSON round trips; reject unsupported definitions visibly.
5. Edit/preview workflow routing expressions without arbitrary execution. Decision-table editing and standalone business-decision simulation are separate optional later work.

Designer acceptance does not block the first engine/sandbox/APOLO integration milestone.

## Out of initial scope

Business decision engine and decision tables; candidate claiming/releasing, general delegation, task expiration, timers, collective approvals, parallel branches, subprocesses, a complete BPMN implementation, arbitrary scripts, dynamic business forms, production microservice deployment, distributed transactions and migration of existing APOLO processes.

## Open-question reference

The [authoritative decision and remaining-work register](../../architecture/workflow-engine-design.md#222-review-outcomes-remaining-specifications-and-ownership) tracks agreed engine contracts, migration ownership F4, source integration F5 and UI/web adoption F6 (superseding F4's module count), plus O7 verification and O12 integration contracts. APOLO policies, source-move and release details retain their recorded status. Architecture adoption does not finalize API paths or implement libraries.
