# Project constraints

**Shared assignment amendment — 2026-11-07:** One SINGLE task has one stable USER, GROUP or ROLE assignment target. Any currently eligible member may complete it without claiming; one completion commits and records the actual actor. Hosts own membership and business authorization. Membership changes do not change the task roster or reopen completed tasks. Claiming/releasing is the next separate feature, with contracts still pending. [F016](features/F016-shared-group-role-task-assignment/spec.md) is IMPLEMENTED revision 2; see its implementation record for scoped verification. Constitution 6.1.0 supersedes earlier direct-user-only assignment and blanket candidate-group deferral for this SINGLE slice. F016 implements the closed assignment, membership, access and preserving-migration contracts; APOLO integration remains separate. Existing user-assignment examples remain valid.

**Architecture adoption — owner decision, 2026-11-07:** `workflow-ui` and `workflow-web` are AGREED under constitution 6.0.0. Target Angular 19 with APOLO's 19.2.x line; exact versions/toolchain require verification. CON-02 now defines three target Maven modules plus a separate Angular library. The [integration record](architecture/workflow-ui-web-integration.md) distinguishes adopted architecture from pending implementation. Existing dependencies, POMs and F011 remain unchanged.

**F009 test-baseline exception — owner decision, 2026-11-06:** [F009 revision 2, D17](features/F009-json-execution-variables/spec.md) removes backward-compatibility obligations for pre-F009 Java APIs, formats and existing test data. Core/test-host callers may change together; old readers, compatibility overloads, dual limit profiles and old-data conversion are unnecessary. D15 capacities remain, but fresh test-schema/rebaseline or migration setup may establish them instead of a mandatory preserving forward migration. New executions still require history/evidence, pinned immutable definitions, concurrency, transactions and authorized idempotent replay. This scoped exception supersedes contrary older wording below; it grants no production compatibility waiver and authorizes no code/data changes in this documentation-only task.

**Owner correction — 2026-11-05:** expression arithmetic follows native SpEL numeric behavior, including floating-point approximation, promotion, rounding and integral overflow. Do not reject a result solely for precision loss or compare it with an exact reference calculation. Declared DECIMAL variables enter evaluation as BigDecimal; decimal literals retain native SpEL types. Native evaluation errors and resource limits still fail the command. Exact decoding, transport, persistence and accepted-submission rules remain unchanged. This supersedes earlier expression-arithmetic exactness wording and the misinterpreted numeric owner decision.

**Version:** 6.0.0

**Recorded:** 2026-11-07

**Applies to:** constitution v6.0.0 and the current separate-development phase

Constraints are confirmed boundaries that specifications and implementations must satisfy. They are requirements, not evidence that the corresponding behavior already exists. This document makes the [constitution](constitution.md) operational without replacing the detailed [consolidated design](architecture/workflow-engine-design.md).

References below identify constitution principles and sections/decision IDs in the consolidated design, with scoped superseding decisions from [database design v1.4](architecture/Workflow_Engine_Database_Design_v1.md) as recorded in [design sources](design-sources.md). Unverified expectations belong in [assumptions.md](assumptions.md). Exact schemas, interfaces, limits and physical mappings remain specification work where the design has not finalized them.

## CON-01 — Platform and dependency boundaries

- Compile against Java 17 APIs and bytecode; the intended execution runtime is JDK 21.
- Use SQL Server with JPA persistence inside core for the initial implementation.
- Each module declares the dependencies its code directly uses. Version management must not introduce APOLO's complete application dependency stack into core.
- Verify compatibility against APOLO's effective build before integration. Standalone versions and a successful local build do not establish host compatibility.

**Trace:** C03/C10; design sections 3–4; Q2/O7; [technical summary](integrations/apolo/apolo-technical-summary.md).

## CON-02 — Three target Maven modules, Angular library and source integration

- The agreed target is exactly `workflow-core`, `workflow-web` and `workflow-test-host` Maven modules, with packages separating core responsibilities. F013 now implements that reactor and the separate Angular library; its scoped runtime acceptance is complete. Architecture adoption alone authorizes no further extraction.
- Core and web are ordinary library JARs; web depends on core. The executable test host consumes them and provides sample policy/infrastructure. Hosts using core directly need not consume web.
- `workflow-ui` is a separate Angular 19 library, targeting compatible 19.2.x Angular/Material/CDK versions. It supplies embeddable pages/components and the later designer, consumed by APOLO and the sandbox shell. It does not require another Maven module or production application.
- Core has no dependency on web/UI, either host's implementation, the designer or a concrete optional decision engine. Hosts declare direct dependencies on the libraries they use.
- Core will join APOLO as a sibling Maven module of backend, retaining its boundary and joining APOLO's build/release lifecycle. Review the effective root parent before reparenting; backend must not become core's parent.
- Keep one authoritative implementation of each reusable library. APOLO backend consumes web/core; its frontend compiles workflow-ui into its existing deployment. The test host stays outside backend's production dependency graph. Designer delivery remains after first APOLO integration.

Move timing, final coordinates and the test host's future location remain open.

**Trace:** C03/C04; design sections 3, 14–19; F4 as amended by F6 on 2026-11-07, F5/O10/O12.

## CON-03 — Host ownership and neutral integration contracts

Core owns reusable workflow behavior, persistence, expressions and integration contracts. Public commands, results, events and interfaces use workflow-owned or standard Java types. They must not expose JPA/APOLO entities, security-provider objects, JDBC connections or transport-provider classes.

The host owns business data/rules, identity, authorization policy, domain endpoints, screens, datasource, transaction infrastructure, connection pool, runtime configuration and external delivery. Core must not silently create competing infrastructure or alter the host's global JSON mapper. External user/business references are stable logical identifiers, without JPA relationships or schema dependencies on APOLO entities.

Web/UI share generic HTTP and presentation implementation while hosts retain activation and policy. Explicitly activate API groups and require host adapters; missing adapters must not grant access. Scope web error handling to its controllers. UI must respect host authentication, HTTP, routing, localization and theme configuration without importing APOLO implementation services. Backend checks remain authoritative, including authorized historical reads/replay and pagination. Preserve exact IDs/JSON values and frozen recovery payloads. Neither library adds a separate security chain, server, transaction manager, migration runner or application login.

APOLO booking endpoints own business orchestration. Generic workflow endpoints must not bypass booking validation. Named extensions are registered capabilities, not class names or arbitrary bean access supplied by definition authors.

APOLO stores the engine-issued workflow instance ID in host business data and uses the engine API to query state, tasks and history. Several bookings may share that ID. A booking column can hold one current association; a host association table may support multiple/historical links. Preserve prior execution links and reviewed membership. Task-detail composition and business actions resolve authorized bookings through host data; JSON variables may contain a working selection without replacing host membership or authorization.

The engine's separate business_type/business_id fields are optional nonunique correlation metadata: both present at creation or both null, immutable thereafter. Neither correlation nor possession of an instance ID grants access or defines booking membership. Core has no APOLO FK or host association migration responsibility.

**Trace:** C03/C09; design sections 2–4, 8, 14 and 17; Q12/Q20; database design sections 1 and 9.1.

## CON-04 — Schema ownership and one migration lifecycle

- Core ships workflow changelogs through `db/changelog/workflow/workflow-master.xml`.
- The test host includes this resource in its local master. APOLO includes it in its existing master and is the single production migration owner.
- Core supplies no independent Liquibase runner or global migration configuration. JPA does not independently create/update production schema.
- Preserve applied changeset identities and resource paths, including during source relocation. Add ordered changesets for schema evolution.
- Workflow migrations must work without APOLO tables. Host fixtures belong outside the reusable workflow master.
- Store versioned definitions as JSON and runtime state, tasks, variables, receipts and history relationally. Preserve records needed by running instances and retained history.

**Trace:** C05; design section 11; F4/O9.

## CON-05 — Initial scope and delivery order

Deliver a simple sandbox workflow, then one cross-team booking approval in APOLO, followed by the workflow designer. Initial execution uses START, HUMAN_TASK, SERVICE_TASK, EXCLUSIVE_GATEWAY and END, one active process position and tasks with explicit assignment targets within an activity. SINGLE is selected: a named resolver with declared validated inputs returns exactly one task with a valid assignment target; invalid count or missing target fails entry. The accepted task outcome resolves the activity and follows its route within the same command transaction. ALL and the fixed task list are also agreed. Create the full bounded nonempty roster atomically; reject missing assignees and duplicate task keys. Audited reassignment preserves obligations; no additions, removals or waivers during an active review. Collect the configured contribution outcome from every task, or resolve immediately on another permitted outcome and cancel open siblings without changing prior submissions. The first decisive command that successfully commits under concurrency protection wins without implicit outcome priority; competing commands cannot overwrite it. Final or decisive submission, resolution, cancellations and synchronous progression roll back together on failure; prior committed contributions remain. Authorized successful retries retain receipt replay. Exact limits/API contracts remain open, and these rules do not establish APOLO approval policy.

Use integer schemaVersion (initial 1), key/version fields and exactly one START node without startNodeKey. Start and human-task input contracts are explicit, with empty contracts rejecting additional input fields. Omitted guards means an empty list. Start authorization is optional via declared host guards, with no startPolicy.permission or mandatory start hook; absence of an authorization guard means no engine-side start authorization check. Human-task completion uses the same optional authorization-guard model. Language design section 12.1 defines nesting and descriptor notation under agreed L18; formal schemas, host APIs and remaining validation details still require specification.

The simple persistent task catalog, task completion/cancellation, history, authorization and command reliability belong to initial scope. A business decision engine, decision tables and their storage/APIs/editor are optional later work and must not be dependencies of the first workflow.

Claiming/releasing follows F016 as a separate feature. General delegation, timers, expiration, advanced collective policies such as ANY/QUORUM, parallel orchestration, subprocesses and active-instance migration are deferred. Audited administrative reassignment is distinct from delegation. Existing APOLO processes are not automatically migrated or replaced.

**Trace:** C02; design sections 1, 7, 9 and 21–22; Q1/Q4–Q9/F3, superseded for task multiplicity by database design sections 1 and 4.

## CON-06 — Atomic commands and bounded execution

A command must atomically persist required synchronous business changes, workflow/variable changes, task changes, history, its successful receipt and durable event handoff in the host transaction. Core must not independently commit its part of a business operation.

SERVICE_TASK actions execute synchronously with declared/validated inputs and outputs. Failure rolls back the command. Workflow business effects belong in explicit service nodes; transitions route execution. Each business effect has one owner.

Automatic execution continues to a human task or end under a specified bound. Reject automatic-only cycles; exceeding the bound fails and rolls back. Initial execution does not automatically retry failed business commands.

Concurrency protects the instance aggregate, including task/activity/variable changes, using Hibernate-managed BIGINT lock_version mapped to Long with JPA @Version. For an existing instance, compare the expected version, reserve the first history sequence and flush the version-checked instance update before business effects. Child-row writes alone do not acquire this protection. Capture the actual final managed version in results and receipts; do not assume expectedVersion + 1 or an immediate OPTIMISTIC_FORCE_INCREMENT. Provider and host behavior still require verification.

An identical authorized command retry returns its committed result before state-dependent stale checks; reuse of its identifier for different input conflicts. The fingerprint includes relevant host association inputs and optional correlation. Instance creation and required host links share the command transaction, with links available before dependent actions; a receipt replay creates neither again. A database connection alone proves none of these guarantees.

**Trace:** C06; design sections 7 and 12; Q11/O1/O7; database design sections 9, 14 and 16.

## CON-07 — Durable delivery and authoritative history

Record the external-event handoff within the command transaction, then deliver through host infrastructure after commit. Delivery must survive process failure and tolerate retries through idempotent consumers. A non-durable after-commit callback is insufficient. Business updates required for the atomic approval result remain in the command transaction.

Persist authoritative workflow/task history with relevant versions, actors, outcomes, routing evidence and correlation. Host logs do not replace this history. Capture only necessary permitted evidence and deliberate snapshots; apply host access, retention and redaction policy. Report structured failures with correlation without exposing secrets or internal stack traces.

Reliability is required before every production deployment, even one preceding the designer.

**Trace:** C06/C09/C10; design sections 12–13 and 20–21; Q11/Q20/O9.

## CON-08 — Immutable publication and stable task catalog

Workflow definition versions use DRAFT/PUBLISHED/RETIRED. Validation is tied to the exact draft revision, not a separate VALIDATED state. Publication checks the current revision and deployed-host capabilities. Published content is immutable and instances pin workflow versions. Human-node outcomes, inputs, comments, guards and completion policies belong to that pinned definition.

The workflow_task_type catalog has a code immutable from creation, retained rows, editable descriptive metadata, selectable and a technical lock_version. It has no business versions or DRAFT/PUBLISHED/RETIRED lifecycle. Renaming codes and deleting rows are unsupported even when unused. Disabling selectability prevents new authoring selection without invalidating published definitions or their execution, including new instances. The activity stores task_type_id; tasks inherit the type through their activity.

Catalog administration and any import schemas require specification without reviving a task-type publication lifecycle. Catalog content must not encode host implementation names, routes or messaging destinations. Metadata alone does not prove executable host support. Workflow publication validates node contracts and catalog references against deployed capabilities.

APOLO resolves screens and handlers using the stable task code and retains support for published workflow contracts. Unsupported codes/contracts fail visibly; separate form key/version fields are outside V1.

**Trace:** C07; database design sections 8, 10 and 18 supersede versioned task-registry rules in consolidated design sections 9–10 and Q6/Q10/F1/F2/O2.

## CON-09 — Strict inputs and restricted routing

Use restricted SpEL over explicit validated `#facts` and `#vars`. Conditions return Boolean values. Evaluate non-default conditions in explicit declared priority order; the first true result selects a route and stops evaluation. Later conditions are not evaluated or recorded as false. If all conditions are false, use the separately identified unconditional default or fail if none exists. At most one default is allowed. Errors in evaluated conditions fail the command without fallback.

Validate all conditions' syntax and permitted features before publication. Preserve route order in the definition and expose it in the designer independently of diagram layout. Published order remains immutable and instances retain their pinned version. Exact notation and diagnostics remain specification work. This owner-selected revision of 2026-10-31 supersedes multiple-match rejection; it does not add a decision-table engine.

Expression rules are centrally enforced and determined by the workflow-language version. Definitions have no independent expressionPolicy field; host configuration cannot replace those semantics. Engine upgrades preserve supported language versions or reject unsupported versions explicitly. Incompatible expression changes require a new language version. The legacy expression_policy database column requires separate persistence reconciliation, without rewriting applied migrations.

Reject unsupported schema versions and semantics. Prevent type/constructor/bean access, general method invocation, assignment, mutation and arbitrary object access. Use explicit structural enforcement of the context's necessary syntax restrictions and bounded evaluation/input complexity. Expressions cannot fetch business data or read hidden time/random/global state. Within that controlled context, retain standard SpEL operator, precedence, short-circuit and null semantics; do not prohibit ordinary arithmetic by default. Decode declared workflow types exactly before evaluation; native SpEL numeric behavior governs arithmetic, and missing required data still fails. Context enforcement and upgrade compatibility require verification.

Scalar types remain STRING, LONG, DECIMAL, BOOLEAN, DATE and DATETIME; execution variables also support JSON under CON-14. Preserve exact declared-input decoding/transport/storage and use explicit dates/unambiguous timestamps; expression arithmetic keeps native SpEL behavior. Required inputs and declared service bindings must be validated. Exact encodings, nullability, limits and error codes remain to be specified.

**Trace:** C08; design sections 5, 7.3, 8.4 and 10.4; O1/O6.

## CON-10 — Authorization and domain policy

Obtain trusted identity from the host. For human-task completion, core executes the selected outcome's declared guards before accepting the submission, including authorization only when configured. Omission means no engine-side business-permission check. A declared guard must be registered and pass; denial or failure prevents completion and committed effects. Core rechecks assignment, state, version, declared outcome, comments, inputs/results and guards, then applies completion policy. Outcomes have no dedicated permission field; an authorization guard may bind a fixed permission argument through literal/value. Client-provided actor identity and arbitrary variable updates are not accepted; available-action queries are advisory.

APOLO enforces no self-approval, country/business access and audited reassignment/overrides. Harmless booking edits may proceed; material changes invalidate review and require resubmission. APOLO defines materiality and owns booking rules, forms, approver sources and outcomes. The engine must not invent overbooking, routing or alternative-resource policy.

**Trace:** C09; design sections 8–9 and 17; Q17/Q18/O3–O5/O8; [booking analysis](integrations/apolo/booking-business-analysis.md).

## CON-11 — Designer and preview integrity

At the later designer milestone, visual/JSON round trips must preserve supported semantics. Define handling of non-semantic extension metadata and reject unsupported semantic features rather than dropping them. Layout cannot affect runtime routing.

Backend validation and execution are authoritative; the frontend does not execute SpEL or grant permission. Editing, preview/simulation, publication and administration have distinct permissions. Publishing is an explicit reviewed operation, not a side effect of saving a draft.

Predicate preview is side-effect-free evaluation of supplied facts. Real sandbox commands execute actions and transactions; they must not be labelled a dry run merely because rollback is possible. Isolate development actors and seed data from production configuration.

**Trace:** C02/C07–C09; design sections 15–17; later designer acceptance criteria in [approval capability](integrations/apolo/approval-capability.md).

## CON-12 — Specification and verification discipline

Specify substantial behavior and its acceptance criteria before implementing dependent code or schema. Trace implementation tasks and evidence to requirements. Resolve relevant open contracts; do not promote illustrative examples into approved behavior.

Follow the owner's 2026-11-01 clarification in [AGENTS.md](../AGENTS.md): isolated unit tests and database integration tests against `localhost:1433/apolo_workflow_test` are authorized, including project migrations and test-owned fixture cleanup. Other targets require task-specific authorization. Protect shared development data and unrelated processes. This supersedes older testing restrictions without changing acceptance requirements. Packaging without test execution uses `-Dmaven.test.skip=true`. Mark unperformed checks NOT RUN. This working policy does not establish production readiness or waive mandatory behavioral guarantees.

For temporary authorized runtime verification, stop processes afterward unless asked to leave them running. Record what was actually verified, the environment and remaining gaps.

**Trace:** C01/C10; Q19; design section 20; subsequent owner instructions; [development workflow](specification-driven-development.md).

## CON-13 — Structured task submissions

- Keep status, configured outcome, comment, scalar inputs and structured result distinct. Each pinned human node explicitly declares one simple-input contract and one result contract shared by all outcomes; outcome-specific overrides are excluded initially. Permissions, guards and comment requirements belong to individual outcomes. Use inputContract: [] for no additional simple inputs. A forbidden result must be absent, an optional result may be absent, and a required result must be present. Any supplied result must be a valid object; reject top-level null and validate nested nulls against the schema. Preserve simple completions without results. No task-type business versions are introduced.
- Validate bounded JSON object structure (including nested objects/arrays) in core, and business membership, completeness, permissions and reviewed material through configured host validation after the early instance claim and before acceptance/effects. Reject duplicate keys, unsupported values and lossy conversions. Exact schema features, bounds, null/default and number handling remain specification gates.
- Store one authoritative immutable accepted submission in TASK_COMPLETED.details_json for each completed task, with required task/activity references and outcome agreement. Open/cancelled tasks have no successful submission. The proposed schema enforces at most one completion with a filtered unique task index; the executor enforces cross-table existence/state consistency. No separate result table/column is selected.
- Expose authorized exact-task/activity completion APIs. Distinguish an open task from a completed task with no optional result; a missing required event is an integrity error. Respect sibling-review visibility, repeated activation identity and bounded retrieval. Hosts must not depend on arbitrary history parsing.
- Fingerprint the full result. Formatting/property order do not affect identity; array order remains significant unless explicitly normalized by the pinned contract. Version canonicalization; successful authorized replay precedes state-dependent validation. Changed nested content conflicts under the same successful command ID.
- Make the final submission visible to downstream actions in the shared transaction through explicit completed-activity context. Rollback removes that command's result and effects while preserving prior committed contributions.
- JSON does not change ALL semantics: use a common configured contribution such as REVIEW_SUBMITTED when the host must combine all reviewers' per-item decisions. Host aggregation may produce validated scalar or JSON outputs through declared bindings; results do not automatically modify variables or expression facts. Current-value replacements never rewrite original task submissions.
- Preserve completion records and their readers for operational dependencies and audit. Do not independently purge completion evidence while its task remains retained. Exceptional redaction/unavailability must resolve affected execution explicitly. Ordinary result edits, unfinished form saving and incremental task completion are outside this capability.

**Trace:** C06–C09; database design sections 8.2, 11.1, 12–16 and 19–21; ASM-10. Business review snapshots and host association ownership remain separate concerns.

## CON-14 — JSON execution variables

- Add JSON alongside scalar variable types, with nullable json_value and matching-type/JSON-validity checks. Non-null roots are objects or arrays; top-level primitives use scalar types. Preserve absent row versus explicit SQL NULL, nested null, missing property and empty collection distinctions.
- Pin declarations, nullability and writer/binding permissions to the workflow version. In [F009](features/F009-json-execution-variables/spec.md), field schemas are deferred for JSON start/service/variable values: arbitrary nested fields require no separate declarations. Reject unknown top-level contract keys, duplicate JSON keys, malformed JSON, lossy numeric conversions and exceeded bounds. Workflow-document and task-result schemas are unaffected. F009 C01–C07 finalize its codec/API/navigation contracts; its implementation record separates local automated evidence from remaining browser/APOLO acceptance.
- Initialize or replace entire values via declared start/service bindings or authorized commands. Existing-instance updates require the early version-checked instance claim; start-time initialization shares the new instance's transaction. Stale replacements fail. No patch/deep-merge/in-place nested-write API is implied. Public boundaries expose detached/read-only data, not mutable provider/application objects.
- Provide authorized bounded reads and explicit path projections. Restricted #vars object-key/array-index access is read-only; missing object properties in F009 project to null, matching explicit JSON null. Undeclared/uninitialized variables, incompatible operations and invalid index types remain failures; F009 D06 makes out-of-range integer array reads return null without reverse indexing or mutation; F009 D05 selects explicit per-access `?.` property traversal; ordinary property access on null fails. D09 selects standard SpEL navigation including explicit null-safe indexing, with the agreed JSON adaptations; concrete integration remains engineering specification/verification work. Retain the expression allowlist and prohibit arbitrary methods, reflection, mutation and host-object traversal. F009 C01–C07 finalize path/binding syntax; see its implementation record for local safety checks and remaining acceptance.
- Fingerprint full replacement values and preserve accepted write/routing evidence before later mutations. Same-transaction consumers see accepted updates; failures roll back values, effects and evidence. Persist replacements explicitly rather than relying on incidental JSON-tree dirty tracking.
- Keep immutable task submissions, current execution context and authoritative APOLO membership separate. No implicit result-to-variable merge or partial-ALL mutation of frozen material. JSON-path filtering/indexing requires a separate justified projection design; storing JSON does not create indexed search.
- F009 D15 selects 256 KiB UTF-16 per JSON start/service/variable value and retains 1 MiB UTF-16 current state; use its complete related-envelope table. History capacity needs schema setup under D17 (fresh test baseline/rebaseline or migration); selected receipt/handoff bounds remain.
- F009 D17 permits coordinated breaking API/codec/schema changes and disposable pre-F009 test rows. Implement scalar and JSON behavior in one new baseline; no old readers, compatibility overloads or dual profiles are required. Establish coherent fresh test-schema/rebaseline or migration setup before execution. Continue history recording and all runtime guarantees for new executions; no production exception is implied.

**Trace:** C06/C08/C09; database design sections 5, 12, 19–21; ASM-11.

## Current local configuration

The current development host targets `localhost:1433`, database `apolo_workflow_dev`, using Windows native authentication. Its matching x64 JDBC DLL is a generated local build artifact. The current Spring Boot 3.4.5, JDBC 12.8.1 and Lombok 1.18.36 versions describe the standalone build. They are not permanent architectural version pins or production authentication/TLS policy.

See the [project README](../README.md) for runnable commands and the [source record](design-sources.md) for historical checks. Compilation/packaging does not start the host; host startup runs its configured Liquibase migrations.

## Change control

Feature specifications should cite applicable CON identifiers. Changes to agreed architectural constraints follow the constitution's amendment rules and must update the corresponding design decisions. Changes to local configuration or verification instructions must be recorded with their scope. Unverified compatibility is tracked as an assumption, not handled by silently weakening a constraint.
