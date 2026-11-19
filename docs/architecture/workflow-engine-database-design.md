# Workflow engine database design

**Named-output extension — 2026-11-10:** [F019 revision 2](../features/F019-named-task-output-variables/spec.md) selects creation of a JSON workflow_variable row at accepted task completion when the pinned node names its output. No row is reserved before completion and no new result store is selected. F019 uses the existing variable keys and JSON storage. Later replacements affect only the variable copy, not TASK_COMPLETED evidence. Creation/evidence/receipt share the existing transaction. F021 extends this to resolution-only ALL collection outputs; no variable is created for a nonfinal contribution. F019 also supports explicit service input bindings to the original submission by human node key, independent of the current variable value or triggering completion.

**Claim/release decisions — 2026-11-09.** [F017](../features/F017-task-claim-release/spec.md) is IMPLEMENTED revision 3 with guarded SQL Server and live sandbox acceptance complete. The owner selected per-human-node OPTIONAL (default) or REQUIRED claiming for shared tasks, exclusive claimant completion subject to current eligibility/business access, owner release and host-authorized administrator release, and a separate nullable assignee_ref column preserving the original assignment. Direct USER tasks initialize assignee_ref from assignment_ref and complete without a claim action under either policy. Claim/release actions apply only to GROUP/ROLE tasks. Eligible peers retain claimed-task visibility; current assignees retain discovery/release after membership loss under business access. Own receipt recovery survives membership/admin-privilege loss under business access. Administrative release requires a recorded reason. Constitution 6.2.0 records this additive scope. O01–O06 are closed. Earlier no-claim wording describes the F016 baseline.

**Owner-confirmed task columns — 2026-11-07:** [F016 O05](../features/F016-shared-group-role-task-assignment/spec.md#o05--storage-wire-and-immutable-evidence-compatibility) selects `assignment_kind VARCHAR(5) NOT NULL` and `assignment_ref NVARCHAR(200) COLLATE Latin1_General_100_BIN2 NOT NULL` on `wf.workflow_task`, replacing assigned_user_ref through a preserving USER backfill. The original target remains after completion; closed_by records the actual person. O05 owns the exact constraints/indexes and migration sequence and supersedes earlier direct-user-only column descriptions below. Membership stays host-owned; claiming columns are outside F016. Migration 004 is implemented and applied to the guarded test database; production migration execution remains host-owned.

**Shared assignment amendment — 2026-11-07:** One SINGLE task has one stable USER, GROUP or ROLE assignment target. Under the original F016 unclaimed-task contract, any currently eligible member could complete it; one completion commits and records the actual actor. Hosts own membership and business authorization. Membership changes do not change the task roster or reopen completed tasks. F017 subsequently implemented claim/release and now qualifies completion through pinned claim policy and ownership. [F016](../features/F016-shared-group-role-task-assignment/spec.md) is IMPLEMENTED revision 2; see its implementation record for scoped verification. Constitution 6.1.0 supersedes earlier direct-user-only assignment and blanket candidate-group deferral for this SINGLE slice. F016 implements the closed assignment, membership, access and preserving-migration contracts; APOLO integration remains separate. Existing user-assignment examples remain valid.

**F009 test-baseline exception — owner decision, 2026-11-06:** [F009 revision 2, D17](../features/F009-json-execution-variables/spec.md) removes backward-compatibility obligations for pre-F009 Java APIs, formats and existing test data. Core/test-host callers may change together; old readers, compatibility overloads, dual limit profiles and old-data conversion are unnecessary. Within that historical baseline D15 capacities remained, but fresh test-schema/rebaseline or migration setup may establish them instead of a mandatory preserving forward migration. New executions still require history/evidence, pinned immutable definitions, concurrency, transactions and authorized idempotent replay. This scoped exception supersedes contrary older wording below; it grants no production compatibility waiver and authorizes no code/data changes in this documentation-only task.

**F009 capacity decision — 2026-11-06:** [F009 D15](../features/F009-json-execution-variables/spec.md) raises each JSON variable/start/service value to 256 KiB UTF-16 and retains the 1 MiB UTF-16 current-variable total. Its table specifies larger JSON-profile input/service/history envelopes, history-read capacity and a required history-only forward constraint migration. It supersedes older 64 KiB variable/input and smaller related-envelope wording only for that profile. Scalar legacy limits, task-result scope and receipt/handoff bounds remain unchanged. This records the decision-time scope. F009 subsequently delivered its baseline; F021 supersedes these capacities and implements migration 006 (sections 5 and 19).

**D04 owner correction — 2026-11-06:** for F009, reading a missing property of an initialized JSON object returns null, just like explicit JSON null. APOLO may omit null-valued fields. This supersedes earlier missing-JSON-property failure wording only; undeclared/uninitialized variables and other evaluation errors remain failures. Stored missing/null distinctions remain. D05 subsequently selects explicit per-access `?.` property navigation; ordinary dot access on null fails. F009 subsequently specified and implemented array/index navigation and exact-value handling; section 12.2 reflects the delivered contract.

**Owner decision — 2026-11-06:** the initial JSON execution-data slice uses declared JSON variables and explicit start/service whole-value mappings without per-field structure schemas. APOLO supplies DTO-derived snapshots; core evaluates bounded read-only object/array navigation under `#vars`. Arbitrary nested fields are accepted within JSON validity, exact-value and resource constraints. Structure schemas are DEFERRED for these start inputs, service values and variables; workflow-document validation and task-result contracts are unchanged. This scoped amendment supersedes earlier mandatory embedded-schema wording for this slice, not the remaining schema design. See [F009](../features/F009-json-execution-variables/spec.md). The dated decision authorized documentation only; F009 subsequently implemented this scope.

APOLO embedded workflow library

**Version 1.5  |  14 November 2026  |  Persistence design reconciled through F022**

This design defines the SQL Server persistence model for the reusable workflow library embedded in APOLO. It preserves immutable JSON definitions and relational execution state, and supports multiple USER/GROUP/ROLE-targeted tasks within one human-task activity. A workflow still has one active process position. The design covers table contracts, relationships, constraints, indexes, collective completion, history, idempotency, migration ownership and verification criteria.

The implementation retains nine core tables. One activity can contain several required work items. A successful individual task completion can leave the activity waiting; only activity completion produces the outgoing workflow transition. Task types are identified by a simple catalog, while permitted outcomes and input contracts belong to the immutable workflow definition. [S0]

Revision 1.1 incorporates the owner-requested changes of 25 October 2026: replace versioned task types and their outcome table with `workflow_task_type`, resolve task types through the activity, and replace SQL Server `ROWVERSION` with JPA-managed numeric `lock_version` columns. Task codes are immutable from creation, catalog rows are retained, and selectability controls new authoring choices. Published workflow versioning, draft content revisions and instance-level concurrency remain separate concerns.

Revision 1.2 retains `business_type` and `business_id` as optional engine-side business correlation. APOLO owns the authoritative associations between bookings and workflow instances. The two columns support filtering and lookup without defining the set of business records participating in an execution.

Revision 1.3 added structured task submissions: a configured outcome, optional comment and node-declared JSON result, retained in the existing TASK_COMPLETED history record. Engine APIs expose the accepted submission by exact task/activity identity. That revision retained scalar-only variables; revision 1.4 extends them as described below. No additional task-result table or JSON column is required for submissions; completion-record constraints and lookup indexes extend the proposed initial schema.

Revision 1.4 adds JSON alongside the six scalar workflow-variable types. JSON variables hold mutable objects/arrays with nested content in workflow_variable.json_value; task results remain immutable submissions. Declared bindings support validated variable reads/writes and restricted read-only path access for routing. This does not move authoritative booking associations into core or automatically merge task results into shared state. Those details were specification gates at revision 1.4; F009 and F018–F022 now govern the delivered subset.

Revision 1.5 reconciles the body with migrations 001–006 and delivered contracts through F022: stable assignment targets and claims, structured results, named outputs, captured inputs, ALL collections and previous-decision reads. Earlier revision descriptions retain their historical meaning. Broader recovery, loops, guards and host-owned integration remain distinct from implemented support.

## 1 Scope and decision status

**Language decisions and delivered subset:** the 31 October decisions selected integer schemaVersion, exactly one START, pinned node-wide contracts and SINGLE/ALL fixed-roster completion. F018 implements structured result presence/schema validation; F020 supersedes scalar human submission inputs with captured incoming bindings, retaining only published legacy empty inputContract compatibility. F017 adds claim policy, F019 named outputs and submission bindings, F021 resolved ALL collections, and F022 previous-decision presentation. Nonempty start/outcome guards and assignment resolver inputs remain broader agreed design, not accepted current definitions. Hosts authorize commands/reads/replay and may supply F018 business validation. See the [language design](workflow-definition-language-design.md) for exact supported syntax and deferred scope.

**Later owner decision — 31 October 2026:** expression rules are centrally enforced as part of the workflow-language version, with no independent expressionPolicy field in definitions. This supersedes the separate policy selector described in the original v1.4 envelope. F001 originally included expression_policy; F004 migration 002 removed it with an empty-definition-version-table precondition. Populated legacy definitions require a separately specified conversion; the column is absent from the current schema. This note does not alter applied changesets, the pinned F001 contract or historical evidence. See [language design section 11.1](workflow-definition-language-design.md#111-rules-for-evaluating-expressions).

**Confirmed direction.** Multiple tasks per activity are included in the first iteration, as requested on 24 October 2026. Retain the embedded Spring/JPA library, SQL Server persistence, one active process position, immutable published workflow versions, host-owned business data, shared transactions, audit and durable event handoff.

**Selected simplifications.** Use a simple task-type catalog with a code immutable from creation and a selectable flag. Retain catalog rows; disable new authoring choices with selectable = false instead of renaming or physically deleting entries. The published human node owns outcomes, captured input bindings, result contract, comments, claim/completion policy, optional outputVariable and previousDecisions. Declared guards remain broader design. Store `task_type_id` only on the activity; tasks inherit that type. Use `BIGINT lock_version` mapped to a Java `Long` with JPA `@Version`. These choices supersede the versioned task registry and SQL rowversion proposals in revision 1.0.

**Business association ownership.** APOLO owns booking-to-instance associations and the business membership of a review. Core retains the optional, immutable pair business_type/business_id for correlation and lookup, with no APOLO FK. Both fields are supplied together at creation or both are null. Their presence is not required for engine execution, and their values do not authorize access or establish booking membership.

**Structured submissions.** F018 implements bounded object results under the pinned human-node contract, alongside outcome and comment. Accepted submissions remain immutable TASK_COMPLETED evidence, with authorized exact-task/activity retrieval and specified schemas, codecs, bounds and errors. Incoming F020 inputs are separate read-only context. Draft form saving, partial submission of one task and implicit result merging remain excluded.

**Structured execution data.** JSON variables are selected in addition to STRING, LONG, DECIMAL, BOOLEAN, DATE and DATETIME. A declared JSON variable may hold an object or array, including nested combinations. Current writes use declared start/service whole-value bindings or explicit human-output creation under the instance transaction; standalone variable-update commands remain deferred; implicit merges and in-place nested mutation are excluded. Human incoming bindings derive scalar/JSON types from their sources; reviewer answers use the separate structured result contract.

**Agreed execution behavior — 31 October 2026.** Use SINGLE and ALL completion policies, a fixed set of required work items per activation, one shared task type per activity, and stable USER/GROUP/ROLE assignment targets. Under ALL, a configured outcome contributes toward completion; another permitted outcome resolves the activity immediately. Sections 4.1–4.2 record the fixed-list, early-completion, concurrency and rollback rules. F021 specifies and implements the exact limits and runtime contracts; these generic rules do not select APOLO approval policy.

**Physical choices and verification.** The nine-table schema, collations, constraints and numeric JPA mapping are implemented. Feature records identify scoped SQL Server evidence for early writes, flush ordering, final version capture and shared transactions; this document does not replace that evidence or close historical gaps. APOLO's effective database/JPA compatibility and production acceptance remain separate integration gates under O7.

The initial design excludes graph-level parallel branches, ANY and QUORUM policies, weighted voting, optional participants, changing the task roster after activation, delegation, timers, external workers, live migration, and the optional business decision engine. Unsupported policies must fail validation. A task due date is metadata only.

This document specifies persistence; it does not introduce another datasource, transaction manager, migration runner, or workflow service deployment. Core owns its schema resources. The test host runs them locally; APOLO includes the same resources through its existing Liquibase master.

## 2 Core invariants

- An instance pins exactly one workflow definition version for its lifetime.
- At a committed waiting position, an ACTIVE instance has one ACTIVE human-task activity and at least one OPEN task within that activity. Previously completed contributions may coexist with those open tasks.
- An activity's task membership and required task count are fixed at activation. Claim/release changes shared-task ownership without changing the effective target or roster. F026 allows authorized reassignment of an OPEN task within that fixed roster.
- The schema can retain repeated activations with distinct identities; current executable definitions are acyclic. Re-entry remains broader design.
- Every task in an activity inherits the same catalog type through the activity. Its outcomes, input bindings and result contract come from that activity's node in the instance's pinned workflow definition. Each task has a stable key unique within its activity.
- A terminal instance has no active activity and no open tasks. Reaching END with result REJECTED is normal workflow completion.
- A partial ALL completion changes task state, history, receipt and the instance token, but does not create a workflow transition or execute downstream service nodes.
- Every accepted mutation of an existing instance, activity, task or variable performs an early version-checked update of the owning instance. Hibernate owns `lock_version`; changing a child row alone does not protect the aggregate.
- Domain effects, workflow effects, history, durable handoff and the successful command receipt share one transaction.
- Current variables, immutable captured inputs/submissions and the original successful-command result are distinct. ALL creates a named output only at resolution, never on a nonfinal contribution. Previous-decision reads use a fixed receiving-activity entry cutoff and current receiving-task authorization.
- Every completed task has one authoritative TASK_COMPLETED submission record; open/cancelled tasks have none. Structured results are task-local evidence, not shared variables or business membership. Downstream actions can read the final submission in their shared transaction before commit.
- Optional business correlation is fixed at instance creation. APOLO-owned associations identify the participating business records and preserve the membership reviewed by each activity.

## 3 Relationships and table inventory

```text
Runtime ownership and task multiplicity

Definition version
[Immutable graph JSON]
  |
  +-- 1 to many --> Instance
                   [One pinned definition]
                     |
                     +-- 1 to many --> Activity
                     |                 [One active human position]
                     |                 [Frozen task membership]
                     |                   |
                     |                   +-- 1 to many --> Tasks
                     |                                     [Multiple assigned work items]
                     |                                     [Unique task keys per activity]
                     |
                     |  Each table below belongs to an instance
                     +---------------> Variables
                     |                 [Current typed scalar or JSON values]
                     +---------------> History
                     |                 [Ordered immutable evidence]
                     +---------------> Command receipts
                                       [Original successful results]

Task type catalog
[Immutable code; retained row; editable metadata; selectable flag]
  |
  +-- 1 to many --> Activity [task_type_id]
                     |
                     +-- 1 to many --> Tasks [type inherited through activity]

Pinned definition version + activity node_key
  +-- defines ----> Outcomes, captured inputs, result contract, claim/completion
                    policy, named outputs and previous-decision policy
```

The diagram shows runtime ownership. The definition master owns versions whose content is immutable after publication, including each human node's outcome and input contracts. The catalog identifies known kinds of work and APOLO resolves their screens by stable code. An activity is a particular human-node activation; several tasks share that activation and its catalog reference. The model contains no execution-token or parallel-branch table.

| **Table** | **Responsibility** |
| --- | --- |
| `workflow_definition` | Stable workflow family and catalog identity. |
| `workflow_definition_version` | Versioned graph JSON, publication and validation metadata. |
| `workflow_task_type` | Stable task code, descriptive catalog metadata and authoring selectability. |
| `workflow_instance` | Pinned execution, current position, lifecycle and concurrency. |
| `workflow_activity` | One human-node activation and its aggregate completion state. |
| `workflow_task` | One assigned required work item within an activity. |
| `workflow_variable` | Current typed scalar or JSON execution values belonging to an instance. |
| `workflow_history` | Ordered runtime events and immutable decision evidence. |
| `workflow_command_receipt` | Idempotency identity and original successful result. |

The optional task-type dependency index in section 18 is not part of the required nine tables. No separate task-type version/outcome, standalone decision-definition or decision-trace tables are created. Host-owned durable event storage is a required integration dependency, not an additional core table.

APOLO-owned booking associations are also outside these nine core tables. They reference the engine's instance identity and support one or several bookings per execution without adding a list-valued core variable.

## 4 Multiple tasks and activity completion

### 4.1 Work items and assignment

At node entry, the host assignment resolver returns a nonempty bounded collection of task slots: a stable task key and a USER/GROUP/ROLE assignment target. Initial limit agreed under L16 on 31 October 2026: 100 required tasks per activity. The engine creates the entire activity and task set atomically. A missing assignee, duplicate task key, unsupported task contract or exceeded limit fails the entering command.

The initial activity uses one task type resolved from the pinned node's stable `taskType` code. All task slots are required; there is no optional flag and no denominator that shrinks when a task is cancelled. `required_task_count` records the frozen count and must equal the number of task rows for the activity.

Task keys identify obligations, not people. The schema deliberately does not make assignees unique within an activity. A host may require distinct assignees or distinct actual decision makers for a particular approval process; it must enforce that rule at assignment and completion and preserve it when validating a reassignment destination or authorizing an override. ALL means all required work items have contributed. It does not by itself prove that different people made the decisions.

TASK_CREATED retains the original assignment target. F017 claim/release sets or clears the responsible individual on OPEN shared tasks without changing id, activity_id, task_key or roster membership. Direct USER tasks need no claim action. [F026 reassignment](../features/F026-task-administration-reassignment/spec.md) updates the effective target and records TASK_REASSIGNED while preserving the task and its frozen inputs. Adding, deleting, waiving or individually cancelling a required task while its activity remains active is unsupported.

### 4.2 Initial completion policies

SINGLE, ALL and the fixed task list are agreed by the 31 October 2026 owner decision. F021 now specifies and implements these rules, bounds and contracts; its implementation record owns scoped verification.

| **Policy** | **Required tasks** | **Activity completion** |
| --- | --- | --- |
| SINGLE | Exactly one | Its permitted task outcome becomes the activity outcome. |
| ALL | One to 100; initial runtime limit agreed under L16 | Every task must return the configured contribution outcome. Any other permitted outcome resolves the activity immediately with that outcome. |

`contribution_outcome_key` is configured by the published human node and copied into the activity at activation. It is required for ALL and null for SINGLE. The engine does not hard-code business outcome names. A node can use APPROVE, CHECK_PASSED or another key declared in that node's permitted outcomes.

For ALL, the final contribution produces the same key as the aggregate outcome. Every other permitted task outcome is decisive and produces its own key as the aggregate outcome. Publication requires one outgoing human-node transition for each permitted resulting outcome. A key declared only at another node, even one using the same task code, cannot be submitted at this node.

When a decisive outcome resolves the activity, remaining OPEN tasks become CANCELLED in the same transaction with reason `ACTIVITY_RESOLVED`. Earlier completed contributions remain COMPLETED. Cancelled siblings do not acquire the decisive outcome and are never presented as votes from their assignees.

The first decisive command whose serialized transaction commits determines the result. There is no implicit ordering such as REJECT taking priority over REQUEST_CHANGES. A rolled-back command has not won. New commands for closed siblings fail state or concurrency checks and cannot overwrite the result. An identical authorized retry of a successful command still returns its recorded result. Outcome display order and click order confer no priority.

The final or decisive submission, activity resolution, sibling cancellations, routing and synchronous downstream work share one transaction. Failure rolls back all changes from that command, including its submission and cancellations; previously committed contributions remain. Early resolution stops collection: cancelled tasks cannot submit, so a review that needs every answer should collect a common contribution outcome such as REVIEW_SUBMITTED and aggregate its structured results afterward.

### 4.3 Example with three work items

| **Step** | **Task change** | **Activity state** |
| --- | --- | --- |
| Activation | Create team A, team B and finance tasks | ACTIVE with three OPEN tasks. |
| Team A returns APPROVE | A becomes COMPLETED | ACTIVE; no transition. |
| Team B returns APPROVE | B becomes COMPLETED | ACTIVE; no transition. |
| Finance returns APPROVE | Finance becomes COMPLETED | COMPLETED with APPROVE; take one transition. |
| Alternative finance result REJECT | Finance completes with REJECT | COMPLETED with REJECT; cancel any still-open siblings and take one transition. |

Each completion records its own task evidence and receipt. Activity completion and the resulting transition are recorded once. Downstream service actions run only after the activity has resolved.

All participants review the same captured material. Partial task completions cannot change the shared review snapshot or shared workflow variables in V1. Their comments and accepted structured results are per-task evidence; F020/F021 incoming inputs are immutable entry context shared by the activity. Material changes follow APOLO's invalidation policy and require a fresh review activity; previous contributions cannot be combined with approvals of a different review revision. Structured results do not change ALL outcome semantics; section 11.1 describes the common REVIEW_SUBMITTED contribution for host aggregation of per-booking decisions.

## 5 Physical conventions

The implemented schema name is `wf`. Resolve it consistently in mappings and Liquibase configuration. SQL examples use this name. All foreign keys use NO ACTION on update and delete; explicit retention operations handle deletion in dependency order.

| **Value** | **SQL representation** | **Contract** |
| --- | --- | --- |
| Surrogate entity ID | BIGINT IDENTITY | Local database identity; never a portable definition reference. |
| Definition version | INT | Positive integer within a workflow key; task types have no business version. |
| Revision and sequence | BIGINT | Positive content revisions and monotonic per-instance sequences. |
| Command ID | UNIQUEIDENTIFIER | Client-generated UUID, unique across the core schema. |
| Technical key | VARCHAR(100) BIN2 | ASCII letters, digits, dot, underscore and hyphen; initial letter; case-sensitive; no whitespace. |
| External reference | NVARCHAR(200) BIN2 | Opaque host-defined ID; preserve case and content; host disallows ambiguous surrounding whitespace. |
| Display label | NVARCHAR(200) | Human-readable Unicode. |
| Description or reason | NVARCHAR(2000) | Bounded text; larger evidence belongs in the validated payload. |
| Operational time | DATETIME2(7) | UTC only; normalize at the application boundary. |
| Workflow DATE | DATE | Calendar date with no implicit time or timezone. |
| Workflow DATETIME | DATETIMEOFFSET(7) | Require an input offset, validate exact precision and normalize to UTC before persistence. |
| Workflow DECIMAL | DECIMAL(38,10) | At most 28 integer digits and 10 fractional digits. Reject inexact narrowing. |
| Content hash | BINARY(32) | SHA-256 of the defined canonical content representation. |
| Lock version | BIGINT | Technical numeric optimistic-lock value managed by JPA `@Version`; distinct from definition version and content revision. |
| Structured document | NVARCHAR(MAX) | Validated JSON; NOT NULL where required; bounded before persistence. |

BIN2 means the explicit column collation `Latin1_General_100_BIN2`. Apply consistent comparison rules to related key columns. SQL comparison and Java key lookup must agree. User-facing labels can use the host's presentation collation. [S4]

The owner selected DECIMAL(38,10) capacity under L10 on 31 October 2026; the exact number-spelling rules are in language design section 10.2. This capacity is not a statement that all business amounts need that scale. Accept values exactly representable in DECIMAL(38,10), including values whose excess fractional places are all zero. Reject values requiring rounding or overflowing the range. Apply the same rule to JSON decoding, expression inputs, service outputs and history encoding. SQL Server may otherwise round narrower decimal conversions. [S3]

Operational timestamps identify UTC instants. For declared workflow DATETIME values, the owner selected UTC normalization and exact precision of up to seven fractional-second digits under L10 on 31 October 2026. Require an explicit input offset, normalize to UTC before persistence and return UTC using Z without unnecessary trailing fractional zeros. Extra fractional digits are accepted only when they are trailing zeros removable without loss; reject values requiring rounding. DATETIMEOFFSET can preserve an offset, but this contract stores the normalized UTC offset. Business timezone information belongs to the application. Language design section 10.2 defines the rules; they do not rewrite published documents or arbitrary JSON strings. [S5]

Current physical JSON ceilings, measured in stored UTF-16 bytes using DATALENGTH, are 2 MiB per definition, 256 KiB per validation-evidence/receipt-result document, and 24 MiB per history-details document after migration 006. Runtime receipt/durable-handoff limits are stricter: 128 KiB in both UTF-16 and UTF-8. The original F001 256 KiB history ceiling was raised to 4 MiB by 003 and then 24 MiB by 006; preserve those applied migrations.

An individual task result retains its 64 KiB canonical UTF-16 and depth-16 limits and shares the complete TASK_COMPLETED event budget with outcome, comment and envelope. F021 permits 8 MiB UTF-16 / 12 MiB UTF-8 for an ordinary JSON execution value or resolved ALL collection, depth 18 for execution values, 10 MiB / 15 MiB for initialized variables or one start/service/human-input map, and 64 MiB / 96 MiB cumulative history details per command. Every record/envelope must also fit its applicable limit; no truncation or per-task reservation is allowed.

The [F021 capacity contract](../features/F021-all-collective-review/spec.md#c01--capacity-contract) governs complete request/read/history envelopes, including evidence copies and separate transport encodings. F022 previous-decision pages have their own 256 KiB dual-encoding bound. Current-state measurement counts initialized scalar/JSON values and explicit nulls; submissions/history are separate. Generic value bounds and node result schemas apply independently; JSON execution-data field schemas remain deferred.

**Nested JSON numbers — agreed under L10, 31 October 2026:** preserve standard JSON numeric meanings and use exact parsing, validation and serialization. Numbers inside structured start inputs, task results and JSON variables must be exactly representable within DECIMAL(38,10) capacity; reject overflow or required rounding. This is an engine validation limit, not a change to JSON storage. Numeric strings stay strings, with application validation for precision-sensitive amounts encoded that way. Language design section 10.2 is authoritative; client-side precision already lost before submission cannot be recovered by the engine.

Fields marked N in the following dictionaries are NOT NULL; Y means nullable. `key` and `ref` are shorthand for the technical-key and external-reference types above, not user-defined SQL types. JPA schema generation is disabled for production; Liquibase owns physical changes.

### 5.1 Content identity and canonicalization

Workflow JSON is authoritative for graph semantics. Indexed identity and language-version columns must agree with it. The removed expression_policy column is historical only. Implemented canonicalization `wf-json-c14n-v2` sorts object keys by Unicode code point, preserves array order, string content and exact JSON numeric scale, and hashes compact canonical UTF-8 with SHA-256. It uses no floating-point intermediate. F018 uses a separate mathematical projection for schema comparison without rewriting accepted values or fingerprints. Typed decimals and unsafe-size integers follow the definition codec's declared string encoding. The full immutable workflow document, including designer metadata when present, participates in its content hash.

The full workflow document includes task codes, node outcome labels/order, input contracts, embedded result contracts with supported local references, and completion rules, so those fields participate in the definition content hash. Catalog names, descriptions, selectability and locking versions do not change that document or its hash. The catalog has no content hash or content revision. Workflow lifecycle, locking versions, validation and publication metadata remain outside the workflow content hash. A future canonicalization change must not silently re-hash old published records.

### 5.2 JPA optimistic-lock values

Use `lock_version BIGINT NOT NULL` on `workflow_definition`, `workflow_definition_version`, `workflow_task_type` and `workflow_instance`. Map each to a provider-managed Java field: [S2] [S12]

```java
@Version
@Column(name = "lock_version", nullable = false)
private Long lockVersion;
```

Hibernate initializes and advances this value. Application code must not assign or increment it, and SQL triggers must not compete with the provider. Verify initialization and updates against the effective provider; do not add a database-generated token mapping. Catalog `lock_version` is a technical edit token, not a task-contract business version. Workflow `version` selects published content; `content_revision` identifies the draft content validated.

Activities, tasks and variables use the owning instance's concurrency boundary in this design; they have no independent version columns. History and successful receipts are append-only application records. All runtime writers must follow the instance protocol in section 16; unmanaged SQL and bulk updates must not bypass it. Wire encodings must preserve the full numeric token exactly, including values outside JavaScript's safe integer range; delivered web contracts encode BIGINT IDs/versions as exact decimal strings.

## 6 Workflow definition version envelope

`workflow_definition_version` includes every field below in addition to its specific fields. This lifecycle belongs to the workflow definition, including its embedded node outcomes and input contracts. The task-type catalog does not use this envelope.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `status` | VARCHAR(12) | N | DRAFT, PUBLISHED or RETIRED. |
| `content_revision` | BIGINT | N | Starts at 1; increments only when content changes. |
| `content_hash` | BINARY(32) | N | Hash of current canonical content. |
| `canonicalization_version` | VARCHAR(40) | N | Content hashing contract identifier. |
| `created_at` | DATETIME2(7) | N | UTC creation time. |
| `created_by` | ref | N | Trusted principal reference. |
| `updated_at` | DATETIME2(7) | N | UTC time of the latest permitted row change. |
| `updated_by` | ref | N | Principal responsible for that change. |
| `validation_status` | VARCHAR(12) | Y | PASSED or FAILED; null when no current validation is retained. |
| `validated_content_revision` | BIGINT | Y | Content revision actually checked. |
| `validated_content_hash` | BINARY(32) | Y | Content hash actually checked. |
| `validation_context_hash` | BINARY(32) | Y | Digest of language version (including expression rules), relevant validator context and host capabilities; implemented validator/schema, canonicalization, expression and required host-capability provenance define the context. |
| `validation_details_json` | NVARCHAR(MAX) | Y | Versioned diagnostics and capability provenance. |
| `validated_at` | DATETIME2(7) | Y | UTC validation time. |
| `validated_by` | ref | Y | Validation actor. |
| `published_at` | DATETIME2(7) | Y | First successful publication time. |
| `published_by` | ref | Y | Publishing actor. |
| `retired_at` | DATETIME2(7) | Y | Retirement time. |
| `retired_by` | ref | Y | Retiring actor. |
| `lock_version` | BIGINT | N | JPA-managed technical concurrency value; distinct from content revision. |

Content changes are permitted only in DRAFT. They increment content_revision, recompute content_hash, and clear all current-validation fields atomically. Saving validation metadata changes lock_version but not content_revision. Validation freshness is checked against the content revision/hash and relevant capability context, so saving validation does not immediately invalidate itself.

All validation fields are either null together or populated together. Validation diagnostics contain their payload schema version and necessary context identifiers. Publication rechecks the current content and deployed capabilities; an old PASSED flag alone is insufficient.

DRAFT has no publication or retirement actor/time. PUBLISHED requires publication actor/time and has no retirement actor/time. RETIRED requires both pairs. Published and retired content cannot change; retirement changes only the lifecycle envelope. No uniqueness constraint restricts a workflow to one published version or one draft.

Every definition mutation, including edits to embedded node outcomes or input contracts, checks the expected lock_version of the definition-version row. Published immutability is enforced by the service update contract and restricted write paths; a status CHECK alone cannot compare old and new JSON. If direct database writers must be controlled, add database-side write protection as an explicitly reviewed operational control.

## 7 Workflow definition tables

### 7.1 Workflow definition

Physical table: `wf.workflow_definition`.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `workflow_key` | key | N | Immutable portable family key. |
| `catalog_name` | NVARCHAR(200) | N | Current catalog label; not a historical version label. |
| `catalog_description` | NVARCHAR(2000) | Y | Current catalog description. |
| `created_at` | DATETIME2(7) | N | Creation time in UTC. |
| `created_by` | ref | N | Creating principal. |
| `lock_version` | BIGINT | N | JPA-managed concurrency value for catalog metadata edits. |

Primary key: id. Unique constraint: workflow_key. The key is immutable. There is no latest-version pointer: start resolves an eligible published version once and stores its exact ID on the instance. Current catalog labels may change, while historical displays use the pinned version's metadata.

### 7.2 Workflow definition version

Physical table: `wf.workflow_definition_version`. Includes the complete workflow definition version envelope in section 6.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `definition_id` | BIGINT | N | FK to workflow_definition.id. |
| `version` | INT | N | Positive immutable version number. |
| `schema_version` | SMALLINT | N | Supported workflow JSON language version. |
| `definition_json` | NVARCHAR(MAX) | N | Complete graph, declarations, node outcome/input contracts and optional designer metadata. |

Unique constraint: (definition_id, version). Enforce positive version/schema values and ISJSON(definition_json) = 1. The document must be a supported workflow object; syntax validity alone does not prove that, and ISJSON does not reject duplicate property names. The codec performs duplicate-property detection, typed decoding and semantic validation. [S1]

At import/publication, document key, version and language version must agree with the envelope. The language version determines expression rules; the revised document has no expressionPolicy field. Migration 002 removed the legacy column under the precondition described in section 1. Workflow references use key/version in portable JSON and local IDs in relational records. Human nodes reference the task catalog by stable `taskType` code, without a task-type business version. Definition and node outcome labels are read from the pinned document. Published content, hash and identity cannot be edited in place.

Node keys and transitions remain in JSON. Runtime node/edge references are checked against the pinned graph by the engine, not against a duplicated node/edge table. A corrected definition becomes a new version; existing instances keep their original version.

Retirement prevents new direct selection. Existing instances keep loading the retired version. Deletion is restricted while referenced or retained for audit. The host must preserve executable compatibility for dependencies of active and startable versions, including future human nodes.

## 8 Task type catalog and node contracts

### 8.1 Task type catalog

Physical table: `wf.workflow_task_type`.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Internal primary key for relational references. |
| `code` | VARCHAR(100) BIN2 | N | Unique portable identifier, immutable from creation; follows the technical-key rules. |
| `name` | NVARCHAR(200) | N | Current human-readable catalog name. |
| `description` | NVARCHAR(2000) | Y | Optional current catalog explanation. |
| `selectable` | BIT | N | Whether authors may select the code for new workflow definitions. |
| `lock_version` | BIGINT | N | JPA-managed optimistic-lock value for catalog edits. |

Primary key: id. Unique constraint: code. The code is immutable from creation, even when no workflow references it. Catalog rows are retained permanently in V1; physical deletion and code renaming are unsupported. Names, descriptions and selectability may change through authorized, version-checked catalog administration. Record before/after values and the trusted actor in the host's management audit. The catalog has no business version, DRAFT/PUBLISHED/RETIRED lifecycle, content revision, validation checksum or outcome rows.

Catalog administration, imports and retention jobs must enforce the same rule: never rename an existing code or delete its row. Do not expose a catalog delete operation or configure cascade removal/orphan removal for these entries. Correct an unsuitable code by creating a new entry and setting the old entry's selectable flag to false. Its code remains reserved. This removes the rename/delete race with concurrent definition saves, including references stored only in JSON; no check for whether a code is currently unused is needed to authorize removal.

Portable workflow JSON and task API projections use the stable code. Numeric IDs may differ across development, test and production. APOLO maps codes such as BOOKING_REVIEW, BOOKING_REVISION and DOCUMENT_CHECK to its screens/handlers in host code. The catalog stores no component names, bean names, routes or identity-provider objects. Adding a row does not deploy support; an unknown or unsupported code fails validation or execution explicitly.

Setting selectable = false prevents selection for new definitions. Publication/import of a new workflow version must reject unavailable codes; previously published workflows remain executable, including new instances of an otherwise startable published version and future activities reached by existing instances. Disabling a code does not disable those workflows. Stopping new starts requires retiring or otherwise disabling the relevant workflow versions.

Retaining every catalog row protects references from activities, drafts and published definitions, including future nodes with no runtime activity yet. Dependency inspection remains useful for host compatibility and change-impact analysis, as described in section 18; it does not authorize catalog deletion. Compatible host screen changes can keep the code. Incompatible meaning requires a new code and explicit workflow-definition changes, with host support retained for existing references.

### 8.2 Outcomes and input contracts in workflow nodes

Each HUMAN_TASK node in `workflow_definition_version.definition_json` owns its permitted outcomes, labels/presentation order, captured incoming bindings, structured result contract, comment requirements, claim/completion policy, optional outputVariable and previousDecisions. Nonempty declared guards remain unsupported. These are immutable with the published workflow version. There is no relational task-type outcome table and no outcome-membership FK. Runtime validation resolves the exact node through instance.definition_version_id and activity.node_key; sharing a task code does not imply sharing permitted actions or result contracts.

For example, MANAGER_REVIEW can use BOOKING_REVIEW with APPROVE, REJECT and REQUEST_CHANGES, while FINAL_REVIEW uses the same code with only APPROVE and REJECT. APOLO displays actions returned by the workflow API; the backend rechecks the selected node's permitted outcome, host authorization, assignment/claim eligibility, comment/result validation and optional host business validation when executing the command. Catalog label edits do not change those actions or routing. If an audit requires the catalog label as seen at the time, capture it in history; current catalog metadata is not a historical snapshot.

This illustrative node fragment shows the responsibility split. It omits common name, transitions, assignment and resultContract fields; it is not a complete publishable node. The language design and delivered feature schemas define those fields.

```json
{
  "key": "MANAGER_REVIEW",
  "type": "HUMAN_TASK",
  "taskType": "BOOKING_REVIEW",
  "outcomes": [
    { "key": "APPROVE", "label": "Approve", "requiresComment": false },
    { "key": "REJECT", "label": "Reject", "requiresComment": true },
    { "key": "REQUEST_CHANGES", "label": "Request changes", "requiresComment": true }
  ],
  "completion": {
    "policy": "ALL",
    "contributionOutcome": "APPROVE"
  }
}
```

Publication validates a nonempty outcome set with keys unique within the node, well-formed input/result contracts and exactly one outgoing transition for each possible aggregate outcome. For ALL, the contribution key must be in the node's permitted outcomes. Labels/order are presentation metadata, not decisive-outcome priority. The workflow schema versions the embedded contract format. F020 incoming inputs derive scalar/JSON types from variable or submission bindings and are captured at entry. New authoring rejects human inputContract; loading published legacy definitions permits its historical empty array only. Structured task results and JSON execution variables have separate contracts and lifecycles; neither permits unrestricted JSON Schema evaluation.

The human node declares whether result is forbidden, optional or required, and the accepted object structure, which may contain nested objects and arrays. Its schema is embedded in the immutable definition, with only the supported local acyclic references under F018; external schema references are rejected. Deployments retain the validators/readers and host capabilities needed by published workflows and retained evidence. No task-type business version is introduced. F018 specifies declaration syntax, supported schema features, reference resolution and compatibility; old simple tasks must remain valid without a result and must not acquire permission to submit arbitrary JSON.

Generic validation must enforce structure and bounded total payload size, nesting depth, object/array cardinality and string length. Reject duplicate object keys, malformed/unsupported values and inexact numeric conversions. Under the 31 October 2026 selection, top-level result null is rejected in every mode; absence is allowed only for forbidden/optional results, and any supplied result must be a valid object. Nested nulls follow the result schema. F009/F018 specify exact encoding, schema features and engine-private validation. A schema-valid object is not business authorization: configured host validation checks membership, duplicate/missing domain decisions, eligibility, permissions and review identity/material after the instance claim and before acceptance or effects. Validation must not apply business effects. Snapshot/revision mechanisms remain host-owned and are not supplied by a result document.

At activation, resolve taskType to the catalog row, verify host support and store task_type_id on the activity. Its tasks inherit the type through activity_id. No duplicated type field is needed on workflow_task. Every completion validates the outcome, comment and structured result against the pinned node; captured inputs are not resubmitted or refreshed. SQL constraints still enforce lifecycle shape, nullability and ownership; they cannot establish membership in the embedded outcome set or validate the result's node-specific schema.

## 9 Workflow instance

Physical table: `wf.workflow_instance`. This row is the serialization boundary for every execution mutation, including nonfinal task completion and claim/release; future mutations must use the same boundary.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `definition_version_id` | BIGINT | N | FK to the exact immutable workflow version. |
| `business_type` | key | Y | Optional opaque business-correlation category; present only with business_id. |
| `business_id` | ref | Y | Optional opaque business-correlation reference; present only with business_type; no APOLO FK. |
| `current_node_key` | key | N | Current stable node after commit. |
| `status` | VARCHAR(12) | N | ACTIVE, COMPLETED or CANCELLED. |
| `result_key` | key | Y | END business result for normal completion. |
| `started_at` | DATETIME2(7) | N | UTC start time. |
| `started_by` | ref | N | Trusted initiating principal. |
| `ended_at` | DATETIME2(7) | Y | UTC completion or administrative termination time. |
| `ended_by` | ref | Y | Responsible principal. |
| `termination_reason` | NVARCHAR(2000) | Y | Required for administrative cancellation. |
| `last_history_sequence` | BIGINT | N | Starts at zero; reserves the first event for the early instance claim and allocates subsequent events. |
| `lock_version` | BIGINT | N | JPA-managed numeric aggregate concurrency value. |

ACTIVE requires null result_key, ended_at, ended_by and termination_reason. COMPLETED requires result_key and end actor/time, with null termination_reason. CANCELLED requires end actor/time and termination_reason, with null result_key. A normal END result named CANCELLED still uses lifecycle COMPLETED; administrative cancellation is a different operation.

Enforce ended_at >= started_at when ended_at is present, and last_history_sequence >= 0. An ACTIVE committed waiting instance is at a HUMAN_TASK node with exactly one active activity. Although the storage shape can represent automatic-only completion, the current graph subset requires at least one human stage on every START-to-END path. [F025 cancellation](../features/F025-workflow-cancellation/spec.md) closes the waiting instance, its active human activity and the activity’s OPEN tasks atomically. Temporary automatic-node positions exist only inside the transaction.

No current_activity_id pointer is stored; the filtered active-activity index provides that lookup and avoids a circular FK. current_node_key must agree with the active activity's node when waiting. It is validated against the pinned JSON; a database FK cannot enforce that relationship without a graph projection.

Use a nonunique index (business_type, business_id, id) for category filtering and exact business-correlation lookup, and the definition_version_id access path for pinned-version lookup. Do not universally enforce one active instance per business reference: that is a host process policy. A correlation match may return several executions, including resubmissions or different processes. If APOLO requires one active approval per booking, enforce that rule against its authoritative booking associations through an identified host boundary or a deliberately scoped database invariant. Two start command IDs are not an idempotency match.

All activity, task and variable writes acquire the instance claim. Updating a child table does not automatically change this row's lock_version. For an existing instance, explicitly compare expectedInstanceVersion with its managed lockVersion, reserve the first history sequence by changing last_history_sequence, and flush that ordinary field update before business effects. Hibernate writes the old version predicate and the new version value. A stale claim fails and rolls back. Read the actual managed version after the final flush for the command result; do not calculate expectedInstanceVersion + 1. See section 16 for transaction and sequence ownership.

### 9.1 Optional correlation and APOLO-owned associations

The two correlation fields are both populated or both null. Enforce their presence together with a row constraint:

```sql
CHECK (
    (business_type IS NULL AND business_id IS NULL)
    OR (business_type IS NOT NULL AND business_id IS NOT NULL)
)
```

When supplied, both fields follow the key/ref validation rules in section 5. The pair is immutable from instance creation, including the all-null case; V1 has no later assignment or replacement operation. Immutability is enforced by the command/update contract and restricted write paths, not by this presence CHECK. Keep the fields separate; no concatenated business_key or parsing convention is required. They are independent of command_id for idempotency and correlation_id for request tracing.

For example, an instance may carry business_type = BOOKING_APPROVAL and business_id = APR1042, while APOLO links that instance to bookings B101, B102 and B103. The pair identifies a searchable business reference, not a list of booking IDs. An instance with both fields null can still have APOLO-owned booking associations. Core must not infer membership or authorization from the optional pair.

APOLO owns the association by storing the engine-issued workflow_instance_id alongside its business data. This is the instance's primary identity, distinct from the optional business_type/business_id correlation pair. After starting a workflow, APOLO retains the returned instance ID and uses it through the engine's API to retrieve instance state, tasks or history, and to address instance operations. Host callers use this API rather than depending on core table queries. Knowing an instance ID does not grant access; APOLO must authorize the caller for the related business records.

The host storage shape follows APOLO's relationship needs. A workflow_instance_id column on the booking table is sufficient for one current workflow per booking; several bookings can contain the same instance ID for a collective approval. A host-owned booking_workflow association containing booking_id and workflow_instance_id supports multiple or historical executions per booking. This design does not mandate that additional table. If APOLO stores only the current ID on a booking, it must preserve earlier execution links and reviewed membership in host history or snapshots before replacing that ID.

For task details, APOLO resolves the task's instance ID through the engine API, loads the authorized related bookings from its own data and composes the task response. APOLO service actions resolve the same host associations when applying business effects. A JSON variable may hold booking IDs as working context or, under explicit APOLO policy, the preserved submitted membership and material. Storage alone does not establish authority or grant authorization: host associations remain authoritative for current membership, and APOLO compares them with its identified approval basis. Displaying a booking list does not require duplicating it in variables.

Creating an instance and its required business associations belongs to the same host transaction and command idempotency boundary. Establish those associations before any action that needs to resolve them. APOLO preserves an identified approval basis across all review activations in the same approval attempt; a changed current association must not rewrite what an earlier activity reviewed. See [Approval baseline and protected material](../integrations/apolo/booking-business-analysis.md#approval-baseline-and-protected-material) for the host policy. Optional engine correlation is a lookup convenience and does not duplicate ownership of that membership.

## 10 Workflow activity

Physical table: `wf.workflow_activity`. One row represents one activation of a HUMAN_TASK node. Automatic nodes are recorded in history.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `instance_id` | BIGINT | N | FK to workflow_instance.id. |
| `node_key` | key | N | Human node in the pinned workflow. |
| `activation_sequence` | BIGINT | N | Positive sequence allocated under the instance claim. |
| `task_type_id` | BIGINT | N | FK to workflow_task_type.id; shared by every task in this activation. |
| `completion_policy` | VARCHAR(12) | N | SINGLE or ALL. |
| `contribution_outcome_key` | key | Y | Required for ALL; null for SINGLE. |
| `required_task_count` | INT | N | Frozen roster size; 1 to 100. |
| `review_revision_ref` | ref | Y | Optional opaque host review/snapshot identity shared by all tasks. |
| `status` | VARCHAR(12) | N | ACTIVE, COMPLETED or CANCELLED. |
| `outcome_key` | key | Y | Final aggregate outcome when COMPLETED. |
| `entered_at` | DATETIME2(7) | N | UTC activation time. |
| `closed_at` | DATETIME2(7) | Y | UTC aggregate completion/cancellation time. |
| `closed_by` | ref | Y | Principal whose command closed the activity. |
| `closure_reason` | VARCHAR(40) | Y | Explicit closure category for the event/history contract. |

Unique constraints: (instance_id, activation_sequence) and (instance_id, id). The latter supports the history ownership FK. A filtered unique index on instance_id WHERE status = 'ACTIVE' permits one active human activity while allowing many open tasks inside it. task_type_id has a direct FK to workflow_task_type.id; no composite activity/type key is needed for tasks. [S6]

SINGLE requires required_task_count = 1 and a null contribution_outcome_key. ALL requires a non-null contribution_outcome_key. The engine validates the contribution key and completed aggregate outcome against this activation's human node in the pinned definition. There are no outcome FKs: their authority is immutable JSON, not the catalog. Validate that the resolved catalog code agrees with that node's taskType at activation.

ACTIVE requires no outcome or closure fields. COMPLETED requires outcome_key, closed_at, closed_by and closure_reason. CANCELLED requires closure fields and a null outcome_key. Require closed_at >= entered_at. Current SINGLE/ALL completion writes closure_reason = TASK_OUTCOME, also recorded with outcomeKey in ACTIVITY_COMPLETED. The earlier SINGLE_TASK_COMPLETED, ALL_TASKS_CONTRIBUTED and DECISIVE_OUTCOME categories were proposals, not delivered values. Whole-instance cancellation sets the activity closure reason to INSTANCE_CANCELLED. Supersession remains separate scope.

Identity, node, task type, policy, contribution key, review reference and required count are immutable after activation. The policy fields are a runtime copy of the pinned node's contract; they cannot be edited independently. There is no approved_count or rejected_count cache. Derive completion from task rows while holding the instance claim.

The database checks cannot assert that a cross-table task count equals required_task_count. The executor creates all rows atomically and checks the committed-state invariants. Never put a cross-table task count into a conventional row CHECK through an ad hoc function.

F020 retains SINGLE incoming snapshots in TASK_CREATED evidence. F021 stores one ACTIVITY_INPUTS_CAPTURED fact for nonempty ALL inputs and references it from each task's creation evidence. These immutable snapshots need no new activity/task column. activation_sequence identifies the retained ACTIVITY_ENTERED history sequence; F022 uses that entry sequence as the receiving activity's exclusive previous-decision cutoff.

At an ACTIVE ALL activity, completed tasks have the contribution outcome, at least one task is open, and no task is cancelled. After all contributions, every task is completed with that outcome. After a decisive outcome, earlier contributions remain completed, the decisive task is completed, and all remaining tasks are cancelled.

## 11 Workflow task

Physical table: `wf.workflow_task`. There are multiple task rows per activity. There is deliberately no UNIQUE(activity_id) constraint.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key and exact completion target. |
| `activity_id` | BIGINT | N | Parent activity. |
| `task_key` | key | N | Stable slot/obligation key within that activity. |
| `assignment_kind` | VARCHAR(5) | N | Effective target kind (USER, GROUP or ROLE). |
| `assignment_ref` | ref | N | Effective assignment target reference. |
| `assignee_ref` | ref | Y | Responsible individual: initialized for new USER tasks, or set by a shared-task claim. |
| `status` | VARCHAR(12) | N | OPEN, COMPLETED or CANCELLED. |
| `outcome_key` | key | Y | Submitted task outcome; populated only for COMPLETED. |
| `created_at` | DATETIME2(7) | N | UTC creation time. |
| `due_at` | DATETIME2(7) | Y | Optional due-time metadata; no expiry behavior. |
| `closed_at` | DATETIME2(7) | Y | UTC completion/cancellation time. |
| `closed_by` | ref | Y | Actual trusted actor responsible for closure. |
| `closure_reason` | VARCHAR(40) | Y | TASK_OUTCOME, ACTIVITY_RESOLVED, INSTANCE_CANCELLED or REVIEW_SUPERSEDED. |

Unique constraints: (activity_id, task_key) and (activity_id, id). No uniqueness is imposed on assignment targets, assignee_ref or closed_by within an activity. If distinct-person approvals are required, the host policy must enforce them explicitly; ALL itself counts required work items.

FK activity_id references workflow_activity.id. The task's instance and task type are derived through activity; no redundant instance_id, task_type_id or task-type business version is stored on the task. A task API projection includes the resolved catalog code. The engine validates outcome_key against the activity's node in the instance's pinned definition; the database cannot enforce that JSON membership with an outcome FK.

OPEN requires null outcome and closure fields. COMPLETED requires outcome_key, closed_at, closed_by and closure_reason = TASK_OUTCOME. CANCELLED requires closure actor/time and a cancellation reason, with null outcome_key. Enforce closed_at >= created_at where present. All nullable conditions must be expressed with IS NULL or IS NOT NULL so CHECK does not accidentally accept UNKNOWN. [S7]

A task outcome named CANCEL is a normal completion when that outcome is permitted by the pinned node. It is different from an administratively cancelled task. Cancellation by another task's decisive result records the initiating command and actor; it does not impersonate the cancelled task's assignee.

Completion commands identify the exact task, outcomeKey, expected instance version, commandId, optional comment and node-declared structured result. Scalar submission inputs are unsupported; retained legacy inputs fields must be empty. The engine resolves the parent activity and verifies it is the instance's current active activity. A transition key alone cannot identify one task among several.

Task evidence and comments are persisted in history, without a new task result/input column. F016 migration 004 replaced assigned_user_ref with the preserving USER assignment backfill. F017 migration 005 added assignee_ref and backfilled only OPEN USER tasks: historical closed USER rows may retain null ownership. New USER tasks initialize their owner from assignment_ref; shared tasks start unclaimed.

With OPTIONAL claim policy, an eligible member may complete an unclaimed shared task; REQUIRED needs a claim first. A claimed task requires its current eligible owner for completion. Owner release requires host business access but no reason and remains possible after membership loss; administrator release requires authorization and a recorded reason. USER tasks reject claim/release. Administrative release does not authorize completion on another person's behalf; F026 requires separate permission to reassign an OPEN task. General completion overrides remain deferred.

### 11.1 Structured completion and result retrieval

Task status, configured outcome and structured result have separate meanings. COMPLETED means the reviewer finished the required work, even when the business decisions include rejections. A result does not change the activity completion policy. Under the agreed ALL semantics, a non-contribution outcome still resolves the activity and cancels remaining tasks.

For a collective booking review, configure REVIEW_SUBMITTED as the contribution outcome and put each reviewer's per-booking decisions in their own result document. Once all required tasks contribute, an APOLO service aggregates the exact activity's submissions and applies its specified per-booking policy. Two reviews of three bookings still create two tasks. This supports mixed booking results after review completion; it does not introduce incremental completion of individual bookings or saving an unfinished task. A missing required booking decision fails host validation.

Illustrative domain result using the delivered outcomeKey/comment/result field names, omitting command identity and expected instance token:

```json
{
  "outcomeKey": "REVIEW_SUBMITTED",
  "comment": "Reviewed all three bookings.",
  "result": {
    "bookingDecisions": [
      { "bookingId": 101, "decision": "APPROVE" },
      { "bookingId": 102, "decision": "REJECT", "comment": "Allocation exceeds capacity." },
      { "bookingId": 103, "decision": "APPROVE" }
    ]
  }
}
```

Delivered core reads are `findTaskSubmission(taskId)` and `listActivitySubmissions(activityId, afterTaskId, limit)`, exposed through host-authorized integration boundaries. TaskSubmission carries exact task/activity/instance and history identities, command ID, trusted actor/time, outcomeKey, comment, an empty legacy inputs map and optional result. The activity page contains completed submissions only; task roster/status reads separately expose open/cancelled work. Missing required completion evidence is an integrity failure, not an omitted OPTIONAL result.

Host callers use these APIs rather than parsing arbitrary history or querying engine tables. Authorize both task and activity reads, including host restrictions on visibility between reviewers; task access does not automatically authorize sibling submissions. Activity submission pages use task-ID ordering and cursor pagination, default 50 and maximum 100, subject to response-byte bounds. Read by exact task/activity identity, never by the latest completion for a node. F019–F021 service/human submission bindings identify a preceding human node in the supported acyclic graph and resolve original accepted evidence; skipped, incomplete or absent OPTIONAL SINGLE results fail explicitly. Loop occurrence selection remains deferred. Metadata is derived from trusted rows and pinned context, not client-supplied actor or identity fields.

The same retrieval contract must work inside the command transaction, including the final just-accepted submission before commit. Use coherent managed state or a controlled flush when querying persisted history; do not open a separate read transaction that omits that submission. Ordinary external reads expose only committed results. Persist and retrieve the accepted semantic document without silently dropping fields or rounding values; preserving raw whitespace/property order is not required. Corrections use an explicit fresh-review/supersession process retaining the original submission; no result-edit endpoint is introduced.

There is no task-specific optimistic token in the delivered contract. Every accepted task mutation uses and advances the instance token. Two participants acting from the same instance revision may produce one accepted command and one stale-version response; the latter refreshes and deliberately resubmits. This preserves the existing aggregate concurrency contract.

## 12 Workflow variables

Physical table: `wf.workflow_variable`. Primary key: (instance_id, variable_key). instance_id references workflow_instance.id.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `instance_id` | BIGINT | N | Owning instance. |
| `variable_key` | key | N | Top-level declaration or named human-output producer in the pinned definition. |
| `value_type` | VARCHAR(12) | N | STRING, LONG, DECIMAL, BOOLEAN, DATE, DATETIME or JSON. |
| `string_value` | NVARCHAR(4000) | Y | Current string value. |
| `long_value` | BIGINT | Y | Signed 64-bit integer. |
| `decimal_value` | DECIMAL(38,10) | Y | Exactly representable decimal. |
| `boolean_value` | BIT | Y | True/false. |
| `date_value` | DATE | Y | Calendar date. |
| `datetime_value` | DATETIMEOFFSET(7) | Y | Timestamp with explicit offset. |
| `json_value` | NVARCHAR(MAX) | Y | Current bounded JSON object/array for value_type = JSON; SQL NULL represents explicit variable null. |

No row means uninitialized/absent. A present row with its declared type and all value columns null means explicit null. Otherwise, only the column matching value_type is populated. No is_null column is required. The engine validates the declaration/producer, exact type, nullability, allowed writer and availability before a required read. Named output producers reserve no row before creation.

The following CHECK pattern permits typed nulls and prevents mismatched or multiple typed values:

```sql
CHECK (value_type IN
  ('STRING','LONG','DECIMAL','BOOLEAN','DATE','DATETIME','JSON')),
CHECK (string_value IS NULL OR value_type = 'STRING'),
CHECK (long_value IS NULL OR value_type = 'LONG'),
CHECK (decimal_value IS NULL OR value_type = 'DECIMAL'),
CHECK (boolean_value IS NULL OR value_type = 'BOOLEAN'),
CHECK (date_value IS NULL OR value_type = 'DATE'),
CHECK (datetime_value IS NULL OR value_type = 'DATETIME'),
CHECK (json_value IS NULL OR value_type = 'JSON'),
CHECK (json_value IS NULL OR ISJSON(json_value) = 1)
```

`value_type` is NOT NULL, which is essential to that pattern. Definition-specific nullability stays in the engine because variable declarations are in immutable JSON. For JSON, non-null content must be an object or array. The single-argument ISJSON check accepts those roots; it checks syntax, not duplicate property keys or business meaning. The codec checks exact JSON values and bounds; execution-data field schemas remain deferred and the host owns business meaning. This pattern does not depend on the SQL Server 2022 json_type_constraint argument. [S1]

For a nullable JSON variable, no row means absent and a row with every value column SQL NULL means explicit null, preserving existing semantics. JSON text containing only null is not stored as another null representation. Nested JSON nulls are permitted within generic bounds; missing properties, nested nulls, empty objects and empty arrays are distinct. Top-level primitive values use the existing scalar types. This root-shape choice keeps the typed model unambiguous without restricting nested JSON combinations.

Changing a current value does not rewrite earlier decision evidence. Snapshot and routing history contains the values actually used, with explicit scalar types, exact scalar decimal/integer strings and scale-preserving JSON numbers. A future resubmission contract must preserve previous activity evidence; current replacement uses explicit service bindings.

ALL participants cannot mutate shared review variables, including nested JSON content, through individual completion input or structured result. The node's captured snapshot and review_revision_ref identify what is being reviewed. Results are not automatically merged into variables or exposed as expression objects. A declared host service may explicitly aggregate exact-activity submissions and return validated scalar or JSON outputs through declared bindings. F019–F021 specify submission bindings and explicit named-output creation; business aggregation remains host-owned and the engine does not interpret booking decisions. Copying a result into a variable creates independent mutable execution data and never edits the original submission or shares a mutable object with it.

The composite primary key supports loading all values for an instance. Add indexes on particular typed values only when an identified query requires them. Business reporting continues to use APOLO's authoritative domain data.

### 12.1 JSON declarations, bindings and mutation

Top-level JSON variables declare type and nullability; F009 defers execution-data field schemas and external contract references. Generic bounds and explicit start/service mappings govern values and writers. F019 adds named human outputs without duplicate top-level declarations or result schemas; names must not collide across declarations/producers, including mutually exclusive branches, and share the 100-variable limit. No row exists until an accepted SINGLE result or resolved ALL collection creates it. OPTIONAL SINGLE without a result creates no output. Start cannot initialize it; subsequent service mappings may replace it only after creation, with a nonnull JSON value. Original submission evidence remains unchanged.

An illustrative variable approvalContext could hold:

```json
{
  "bookingIds": [101, 102, 103],
  "summary": { "approvedCount": 2, "rejectedCount": 1 },
  "additionalData": { "department": "CONSULTING" }
}
```

An array-valued declaration can instead hold the selection directly. The example describes execution context, not booking association or authorization. A task submission is separate immutable evidence even if some fields are copied into this variable later.

Initialize declared JSON variables through start bindings; replace values through service output mappings. F019/F021 human outputs are explicit creation boundaries. Standalone variable-update commands, clearing to absent, nested-path bindings, JSON Patch, implicit merge and array append remain deferred. All current writes are whole-value operations under the instance transaction; immutable incoming snapshots are not refreshed by later writes.

Use detached immutable/read-only data projections at API, validator, service and expression boundaries. Do not persist Java class metadata, polymorphic serialized application objects or expose live mutable JPA/JSON-provider objects. Validate replacements on ingress and on service outputs; use explicit persistence replacement/dirty tracking, not an assumption that mutating an internal tree triggers a JPA update. Invalid structure, unauthorized writer, stale version or downstream failure rolls back the command. F009 defines WorkflowValue/WorkflowJsonValue and exact transport; F019–F021 define the added binding/output contracts.

### 12.2 JSON reads, expressions and queries

Provide authorized bounded whole-value reads with explicit type and absent/null distinctions. F009 implements read-only JSON navigation under #vars. Missing object properties and valid out-of-range indexes return null; undeclared/uninitialized variables, invalid index types, unsafe null traversal and evaluation errors fail the command. Explicit null-safe accesses follow the supported F009 contract. Conditions remain Boolean and ordered first-match routing applies. F019 adds only literal-known-key #hasVariable existence checks. Arbitrary API path projections and #facts remain deferred.

Adding JSON must not enable arbitrary method calls, bean/type/constructor access, reflection, mutation, host-object traversal or unrestricted query scripts. Do not infer that the existing SpEL sandbox safely handles maps/arrays without verification. Preserve exact numeric values through parse/store/read and typed expression ingress; arithmetic follows native SpEL promotion, approximation, rounding and overflow. Preserve exact decoding and reject duplicate keys, non-JSON values and lossy conversion. Objects/arrays contain JSON values, not implicit DATE/DATETIME or Java types; nested date/identifier representations and numeric bounds must be declared. Conversion to a typed scalar binding uses that type's existing exactness rules.

Structured storage and in-memory navigation do not imply indexed filtering over arbitrary JSON paths. Keep ordinary instance/variable lookups; do not index json_value as a whole or create speculative indexes for every property. If a workload needs cross-instance filtering, specify a bounded extracted projection/index and its consistency rules separately. Whole-value read authorization must cover its contained data; field-level projections/redaction need explicit contracts and cannot silently change the value used in execution.

### 12.3 JSON idempotency and evidence

Current start/completion fingerprints include their full submitted JSON inputs/results and other semantic command inputs. Derived service replacements are retained in execution evidence, not supplied as standalone variable-update requests. Any future variable-update command must fingerprint its target, operation and complete replacement. Object-key order and formatting are insignificant; arrays remain ordered; no set-normalization extension is implemented. F009/F018 specify null/default/number handling without binary floating-point loss, retaining accepted JSON scale. Identical authorized successful retries resolve through receipts before state-dependent validation; changed nested content conflicts under the same successful command ID.

Current variables are not historical evidence. VARIABLES_WRITTEN retains explicit writes and their provenance; SERVICE_TASK_COMPLETED retains required service evidence and GATEWAY_ROUTED records evaluated routes and actual read paths/values. F009 owns the versioned envelopes and exact codecs; F021 owns current combined capacities. No absence-clearing operation is implied. Preserve task submissions independently, and never truncate required evidence. Host retention/redaction policy remains separate from delivered encoding contracts.

## 13 Workflow history

Physical table: `wf.workflow_history`. Primary key: (instance_id, sequence_number). No additional history surrogate is required.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `instance_id` | BIGINT | N | FK to the owning instance. |
| `sequence_number` | BIGINT | N | Positive per-instance event order. |
| `event_type` | VARCHAR(60) | N | Stable workflow-owned event category. |
| `command_id` | UNIQUEIDENTIFIER | N | Correlation to the originating execution command. |
| `correlation_id` | NVARCHAR(100) | Y | Trusted host trace/business correlation. |
| `actor_kind` | VARCHAR(12) | N | USER or SYSTEM. |
| `actor_ref` | ref | N | Trusted principal; system actions use a stable system identity. |
| `occurred_at` | DATETIME2(7) | N | UTC event time. |
| `activity_id` | BIGINT | Y | Human activity when relevant. |
| `task_id` | BIGINT | Y | Exact task when relevant. |
| `source_node_key` | key | Y | Source node for a transition event. |
| `target_node_key` | key | Y | Target node for a transition event. |
| `transition_key` | key | Y | Selected edge for a transition event. |
| `details_schema_version` | SMALLINT | N | Versioned event evidence format. |
| `details_json` | NVARCHAR(MAX) | N | Validated, bounded event-specific evidence; TASK_COMPLETED holds the authoritative accepted submission, including any structured result. |

Composite FK (instance_id, activity_id) references activity(instance_id, id). Composite FK (activity_id, task_id) references task(activity_id, id). Require task_id IS NULL OR activity_id IS NOT NULL. Together these prevent an event from pointing to another instance's activity or another activity's task. Nullable composite FKs need the explicit presence rule. [S8]

command_id is a correlation value, not an FK to command_receipt. This preserves the documented write order and allows receipt retention to differ from history retention. Atomic persistence still requires every successful command's applicable history and receipt to commit together. There is no reverse receipt-to-history FK cycle.

Delivered event categories include INSTANCE_STARTED, ACTIVITY_ENTERED, TASK_CREATED, TASK_COMPLETED, TASK_CANCELLED, ACTIVITY_COMPLETED, TRANSITION_TAKEN, SERVICE_TASK_COMPLETED, GATEWAY_ROUTED, VARIABLES_WRITTEN and INSTANCE_COMPLETED. F017 adds TASK_CLAIMED/TASK_RELEASED; F021 adds ACTIVITY_INPUTS_CAPTURED for shared nonempty inputs. Earlier names ACTIVITY_OPENED, SERVICE_EXECUTED and ROUTING_EVALUATED were proposals, not aliases. F025 records TASK_CANCELLED for each newly cancelled task, followed by ACTIVITY_CANCELLED and INSTANCE_CANCELLED. F026 records TASK_REASSIGNED. Supersession events remain deferred.

Evidence is event-specific: assignment target/claim ownership and release reason; accepted outcome/comment/result; captured typed inputs and references; pinned policy and frozen roster; cancellation cause; service inputs/outputs and variable-write provenance; evaluated routing paths/values; and the opaque host review reference. ACTIVITY_COMPLETED contains outcomeKey and closureReason = TASK_OUTCOME, not duplicate submissions or an aggregate result array. F021 reconstructs original ALL collections from accepted task evidence. Record required bounded evidence, not full host entities or security/context objects.

For a mutation of an existing instance, reserve the first event sequence by changing last_history_sequence and flushing the instance as the early concurrency claim. Use that reserved value for the command's first actual history event; do not increment it again before assigning that event. Allocate further event numbers from the same counter under the claim. At commit, last_history_sequence is the highest persisted event sequence; do not commit unused reservations or a fabricated claim event. Failure rolls back the reservation with the rest of the command. Multiple events from one command receive different sequence values and share command_id. Timestamps are explanatory metadata; sequence_number defines the authoritative within-instance order. A partial task completion has no fake self-transition.

Application commands append evidence and do not rewrite it. Retention/redaction is a separately authorized host operation with documented limits on later reproduction. Append-only application behavior is not a claim of tamper-proof storage. Definition/catalog management audit is host administration audit, not a fake runtime instance in this table.

### 13.1 Authoritative task submissions

TASK_COMPLETED details contain outcomeKey, comment, an empty legacy inputs object and optional structured result under a versioned envelope. That empty field does not contain F020 captured inputs or enable scalar submissions. The outcome must agree with workflow_task.outcome_key. details_schema_version versions that envelope, while the pinned workflow version and human node identify the result contract; these are different version concerns. The event's task/activity/instance ownership, actor and time come from trusted history columns. The activity's immutable review_revision_ref identifies the reviewed context. Retain only declared submission data; do not serialize arbitrary host entities, security objects or execution context into result.

At committed runtime state, each COMPLETED task has exactly one TASK_COMPLETED record; OPEN and CANCELLED tasks have none. F001 already supplies the TASK_COMPLETED task/activity presence constraint and filtered unique task_id index for that event type. This enforces at most one completion per task. The executor enforces existence, state and outcome agreement atomically; a row CHECK cannot enforce those cross-table rules. Controlled retention of the associated runtime records follows section 19.

There is one authoritative submitted document in history, without another editable copy on workflow_task or workflow_instance. The command receipt remains the authoritative replay response, not a second independently maintained submission. A result reference can use the existing history identity (instance_id, sequence_number); no new history surrogate is required. Task/activity retrieval uses the supported projections in section 11.1. A host reporting projection is optional and derived, with a defined consistency/rebuild policy rather than independent result edits.

## 14 Workflow command receipt

Physical table: `wf.workflow_command_receipt`. The implemented contract uses a globally unique command ID within the workflow schema. No command_namespace column is needed for the initial single-host installation.

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `command_id` | UNIQUEIDENTIFIER | N | Primary key; idempotency identity. |
| `instance_id` | BIGINT | N | FK to the affected or newly created instance. |
| `operation_type` | VARCHAR(40) | N | Stable command category. |
| `actor_kind` | VARCHAR(12) | N | USER or SYSTEM. |
| `actor_ref` | ref | N | Trusted initiating principal. |
| `request_fingerprint` | BINARY(32) | N | SHA-256 of the complete canonical semantic request. |
| `fingerprint_version` | VARCHAR(40) | N | Canonical request contract identifier. |
| `recorded_at` | DATETIME2(7) | N | Time the result was recorded inside the transaction; not a measured SQL commit timestamp. |
| `result_schema_version` | SMALLINT | N | Version of the immutable replay result. |
| `result_json` | NVARCHAR(MAX) | N | Original successful command result. |

Use a nonclustered primary key on command_id and a clustered index on (instance_id, command_id) as implemented by F001. Workload-specific performance still requires verification; no second mutable identity is introduced. The table contains successful command results, not queued attempts or business errors.

The fingerprint includes operation, trusted actor/scope, relevant host business references and association inputs, exact task target, outcome, expected revision, comment and every domain/workflow input affecting the command. For start, include the optional business_type/business_id pair, representing the all-null case consistently. Optional correlation does not permit omitting booking membership or review-revision inputs that affect a host command. Exclude tracing headers and other incidental transport metadata. Canonicalization is server-controlled. A changed request with an existing command ID conflicts; an identical authorized retry returns the stored result before stale-version rejection.

For completion, the fingerprint includes the entire structured result; changing a single nested decision/comment changes the semantic request even when outcome and task ID are unchanged. Parsing must reject duplicate keys before canonicalization. Object-property order and insignificant JSON formatting do not affect identity. Array order remains significant; order-independent normalization is not implemented. F009/F018 specify exact number/string/default/presence handling and versioned fingerprints; never drop unknown input or coerce it lossily to obtain a match. Compare the canonical request before state-dependent host validation and do not rerun that validation for an authorized successful replay. Retain the fingerprint-version handling needed for the supported retry horizon.

The delivered WorkflowExecutionResult contains commandId, operation, instance/activity/task snapshots, and first/last history sequence bounds. Completion identifies the submitted task and its owning activity, even if the instance now waits at a successor; a nonfinal ALL result retains that activity as ACTIVE. Start identifies the entered activity and its task-key-first representative task. Current work is discovered through authorized reads, not a proposed createdTaskIds array or transitionTaken flag. The instance snapshot retains the actual managed lockVersion after final flush, exactly encoded; replay may show an older version than current state.

Runtime request fingerprints use wf-runtime-request-v3 for no-result commands and wf-runtime-request-v4 for result-bearing completion. F021 writes receipt result schema v6 for ALL; SINGLE continues v5 with supported earlier readers retained. These versions differ from definition canonicalization and history detail versions. Claim/release/admin-release also use receipts. F025 cancellation uses result schema v7 and F026 reassignment uses v8. A standalone variable-update command remains deferred.

Do not reconstruct the old result from today's mutable instance. Reauthorize access to the stored result on retry. Return only stable command-result data; do not cache authorization decisions or an available-actions projection as permanent authority.

At the APOLO entry point, receipt resolution precedes state-dependent stale checks and business mutations. The transaction encloses host-owned effects as well as engine effects. The coordinator outside that transaction observes commit/rollback and performs a qualifying duplicate-race receipt recheck only after rollback, in a fresh transaction. That recheck does not rerun actions.

Receipt retention defines the supported retry horizon, particularly for start commands. Before purging a receipt, define whether old command IDs remain reserved through an alternative tombstone or whether their replay guarantee has expired. No fixed duration is selected here. History can remain after receipts expire because there is no history-to-receipt FK.

## 15 Constraints and access paths

### 15.1 Required keys and indexes

| **Table** | **Key or index** | **Purpose** |
| --- | --- | --- |
| workflow_definition | PK id; UQ workflow_key | Stable identity and portable lookup. |
| workflow_definition_version | PK id; UQ definition_id, version | Exact version lookup and duplicate prevention. |
| workflow_task_type | PK id; UQ code | Stable catalog identity and portable code lookup. |
| workflow_instance | PK id; nonunique IX business_type, business_id, id | Optional business-correlation lookup and category filtering. |
| workflow_instance | IX definition_version_id, status, id | Active/startable compatibility and operational queries. |
| workflow_activity | PK id; UQ instance_id, activation_sequence | Activation identity and ordered re-entry. |
| workflow_activity | UQ instance_id filtered to ACTIVE | One active human position per instance. |
| workflow_activity | UQ instance_id, id | Target for the history ownership FK. |
| workflow_task | PK id; UQ activity_id, task_key | Multiple work items with unique slot keys. |
| workflow_task | UQ activity_id, id | Target for history ownership FK. |
| workflow_task | IX assignment_kind, assignment_ref, id INCLUDE activity_id, filtered to OPEN | F016 target-based inbox lookup. |
| workflow_task | IX id INCLUDE assignment_kind, assignment_ref, activity_id, filtered to OPEN | F016 bounded inbox scan with host membership/access checks. |
| workflow_variable | PK instance_id, variable_key | Current values and declaration identity. |
| workflow_history | Clustered PK instance_id, sequence_number | Ordered timeline; efficient instance reads. |
| workflow_history | IX command_id, instance_id, sequence_number | Trace a command's committed events. |
| workflow_history | UQ task_id filtered to TASK_COMPLETED | One authoritative completion per task and exact-task lookup; event requires task/activity references. |
| workflow_history | IX activity_id, sequence_number filtered to TASK_COMPLETED | Bounded exact-activity result retrieval; task states/roster come from workflow_task. |
| workflow_command_receipt | PK command_id; clustered instance_id, command_id | Retry lookup and instance-based retention. |

Migration 004 replaces the original assigned_user_ref/created_at inbox index with the two OPEN-task indexes above. Migration 005 adds the assignee_ref CHECK: bounded nonempty ownership when present, required ownership for OPEN USER tasks, and exact assignment-reference equality for any populated USER owner (including byte length). Historical closed USER owners may be null. Resolve task codes through activity.task_type_id. Do not add speculative claim-owner or JSON-path indexes; use identified workload/query-plan evidence. PK/UQ indexes already supply their access paths, while SQL Server does not automatically index every FK. [S9]

### 15.2 Essential SQL patterns

The activity restriction is the only active-position uniqueness rule. It must never be copied into a one-task-per-activity restriction.

```sql
CREATE UNIQUE INDEX UX_activity_one_active
ON wf.workflow_activity(instance_id)
WHERE status = 'ACTIVE';

ALTER TABLE wf.workflow_task
ADD CONSTRAINT UQ_task_activity_key
UNIQUE (activity_id, task_key);

ALTER TABLE wf.workflow_activity
ADD CONSTRAINT FK_activity_task_type
FOREIGN KEY (task_type_id)
REFERENCES wf.workflow_task_type(id);

ALTER TABLE wf.workflow_task
ADD CONSTRAINT FK_task_activity
FOREIGN KEY (activity_id)
REFERENCES wf.workflow_activity(id);
```

Migrations 001–006 define the applied DDL; the dictionaries describe the resulting schema. These excerpts illustrate constraints; they are not an executable migration package. Close the old activity before inserting the next ACTIVE activity so the filtered unique index sees a valid statement order. Do not rely on JPA flush ordering without verification.

## 16 Command transactions and concurrency

### 16.1 Completion of one task

1. Authenticate and validate the command envelope. Obtain the trusted actor and validate supplied business-reference fields. Null engine-side business correlation does not bypass host authorization or business validation; operation-state checks follow successful-receipt lookup.
2. Enter the host transaction and resolve an authorized identical successful receipt before state-dependent checks or mutations.
3. Load the instance and exact definition. Explicitly compare expectedInstanceVersion with the managed lockVersion. Reserve the first history-event sequence by changing last_history_sequence and flush the instance update before any domain, activity, task or variable mutations. Hibernate manages the version predicate and increment; application code does not write lockVersion. A stale comparison or failed checked update rolls back the command.
4. Load the exact task and parent activity. Resolve required host associations and verify ownership, ACTIVE/OPEN state, current node, assignment/claim eligibility, permitted outcome and comment. Validate the result against the pinned F018 contract and optional host business validator before acceptance/effects. Captured inputs are not resubmitted. Nonempty declared outcome guards remain unsupported; host authorization uses the delivered integration contract. Parsing/size checks may occur earlier; state-dependent checks follow receipt resolution and the claim.
5. Complete that task and append its authoritative TASK_COMPLETED submission using the reserved first sequence, including the accepted structured result when present. Preserve the shared review snapshot; further events allocate additional sequence values under the instance claim. Ensure submission retrieval in this transaction can see this record before downstream execution.
6. Derive the aggregate from the fixed task set under the instance claim. Ensure the calculation includes the just-completed task, either through coherent managed state or an explicit flush before SQL count queries. For a partial ALL contribution, leave the activity/current node unchanged.
7. If resolved, close the activity, cancel its remaining open tasks when appropriate, record outcome/closure evidence and create any declared named output under F019/F021 before downstream traversal. Transitions have no guards; routing selects the declared outcome edge. Flush the old activity closure before any insert of a new ACTIVE activity; this ordering remains inside the same transaction.
8. Only if the activity resolved in step 7, traverse bounded automatic service/decision nodes. Pass the exact completed activity context through declared bindings to actions that need its submissions; the final submission must be visible alongside earlier committed results in this same transaction. Do not look up the latest or currently active activity to infer the source. If another human node is reached, capture its incoming bindings and create its full activity/task set atomically; if END is reached, complete the instance. A partial contribution skips this traversal.
9. Persist all history, host durable event records and the final instance state. Flush and read the actual final managed lockVersion before recording the replay result. A command may flush the instance more than once, so do not derive the result token from expectedInstanceVersion + 1. Do not dirty the instance again after capturing the result; if a further update is necessary, flush and recapture the result token before inserting the receipt.
10. Insert the successful receipt and commit the encompassing transaction. Produce the successful HTTP result only after that commit succeeds.

An action failure, invalid output, host business-validation denial after the claim, routing failure, assignment failure, persistence failure or event-recorder failure rolls back all database effects of this command. Earlier committed task contributions remain intact. Use a rollback-triggering exception or an explicitly equivalent mechanism, then translate errors outside the transaction. An ordinary failure-valued return object does not itself trigger rollback. [S10]

The final contributor can fail a downstream service action. Its completion and structured submission then roll back and its task remains OPEN; previously committed contributions and submissions are preserved. The activity remains at the previous valid waiting position. This follows the chosen synchronous transaction model. Failed attempts do not create successful completion evidence or receipts; operational logging is separate.

The early flush is a write within the shared transaction, not an independent commit. Host orchestration must not dirty business entities before it: EntityManager.flush() can flush other managed changes too. Load the activity/task aggregate after the claim, or explicitly reload previously cached state, so the completion calculation cannot use stale child state from before the claim. Verify actual SQL ordering, isolation and persistence-context behavior under O7.

Do not substitute `entityManager.lock(instance, OPTIMISTIC_FORCE_INCREMENT)` followed by flush for this ordinary field update. JPA permits deferred forced increments, so that call does not establish the required early checked write. The explicit comparison to the client's expected value is also necessary: a freshly loaded managed entity may already have a newer version than the user's request. [S2]

A start command has no pre-existing instance to claim. Resolve its successful receipt first, validate the optional correlation pair, then create the instance, initial history and any activity/tasks in the shared transaction. Host association writes share that boundary; the host integration must provide a point after instance ID allocation and before actions needing those associations to establish them. A failure rolls back both the instance and its new business links. Let Hibernate initialize its lockVersion and capture its actual final value after flushing. Every successful mutation of an existing instance, including claim/release, reassignment and whole-instance cancellation, follows the history-reservation claim protocol. An identical receipt replay performs no new mutation, association write or sequence allocation.

### 16.2 Concurrent participants and duplicate requests

All mutations serialize on the instance; there is no separate lock per parallel branch. Task A completing advances the instance token even when task B is untouched. B's stale client refreshes and deliberately submits a new attempt. Identical successful retries retain their original command ID and expected token and resolve through the receipt first. After refreshing and changing a request, use a new command ID rather than altering an already successful command's fingerprint.

Two tasks cannot independently close the activity. A decisive result cancels the remaining open tasks atomically; a later sibling command cannot contribute to an already closed activation. A uniqueness race at receipt insertion or another concurrency failure rolls back the full outer transaction. The outer coordinator can then perform a fresh read for the winning receipt without replaying effects. PROPAGATION_REQUIRED alone does not make an inner method return equal an outer commit. [S11]

Task completion, claim/release, reassignment, whole-instance cancellation and current variable writes use this instance boundary. Any future standalone variable mutation must retain it. The document retains the existing required instance-token contract. Introducing task-specific tokens to reduce sibling conflicts is a later API/concurrency change, not implicit in task multiplicity.

### 16.3 Lock order and host effects

The APOLO command coordinator must define one lock order covering the instance, business records and any shared capacity/allocation records, with the checked instance write before domain effects. Business edits cannot occur outside the command's idempotency boundary. Instance-level locking does not protect overlapping bookings against different instances; the host must protect shared business invariants. Repository flush behavior, isolation, provider-managed version updates and commit-time error handling require verification against the actual host.

## 17 Historical evidence and operational recovery

At activation, ACTIVITY_ENTERED records the pinned human-node context, policy, count and optional review reference; TASK_CREATED records each target and ownership. SINGLE incoming inputs are retained in task creation evidence; nonempty ALL inputs are captured once in ACTIVITY_INPUTS_CAPTURED with task references. TASK_COMPLETED retains accepted answer evidence, and ACTIVITY_COMPLETED separately records resolution outcome/closure. An activity outcome transition occurs only on resolution.

F022 previous-decision reads require retained receiving ACTIVITY_ENTERED and source ACTIVITY_COMPLETED/TASK_COMPLETED evidence. The receiving entry sequence is an exclusive cutoff: only earlier completed source activities contribute accepted decisions. Same-stage ALL peers, open/cancelled tasks and later decisions are excluded. Current receiving-task authorization governs this narrow actor/time/outcome/comment projection without granting source-result access. No snapshot table, write, receipt or migration is added; missing/corrupt required evidence fails explicitly.

For ALL, the review snapshot is fixed for the activity. APOLO's [approval-basis policy](../integrations/apolo/booking-business-analysis.md#approval-baseline-and-protected-material) additionally keeps one identified immutable basis across sequential approval steps within the same business attempt. APOLO may store it in a generic JSON variable such as approvalBaseline and enforce write-once behavior through permitted writers and host validation; the name and meaning have no special engine semantics. The existing opaque review_revision_ref can identify the same basis across relevant activities. If a later attempt reuses a variable key, APOLO preserves the old full basis and immutable evidence reference before replacement.

APOLO owns application edit restrictions, its database guard for protected host writes and mandatory transaction-time verification of membership, material and eligibility at decisions and finalization. Those checks must protect the check-to-update interval under the host concurrency protocol. Host business checks may inspect current conditions, but their consequential values and provenance are recorded. These are host integration obligations, not core booking tables, triggers, snapshot types or automatic engine mismatch outcomes.

A host validation failure rejects and rolls back the attempted command; it does not itself persist cancellation or an invalidation marker. APOLO defines durable disqualification and recovery that prevent further approval of a materially invalid attempt. If an explicit invalidation command is selected, it follows the existing transaction/idempotency contract and must close/cancel the old review and either create a permitted recovery position or terminate the instance atomically. It must never commit an ACTIVE waiting instance without a viable active activity and open task. Previous submissions and the identified basis remain evidence; fresh review cannot reuse the old approvals.

Same-instance resubmission remains an APOLO integration decision. The database shape supports repeated-node activations, but current publication rejects cycles; same-instance loops/resubmission are not delivered runtime behavior. All previous activity/task IDs and snapshots remain intact. Technical supersession of an execution is distinct from business cancellation of a booking; preserve predecessor/replacement instance references in the host recovery event evidence.

A permanent defect in an immutable published workflow cannot be repaired by changing its JSON in place. Correct permitted host data or deploy a compatible implementation fix when appropriate. When changed graph content is necessary, the host must specify and authorize the linked replacement process. F025 supplies cancellation for an ACTIVE instance waiting at a human task. A new definition version does not move existing instances automatically.

Workflow history is authoritative execution evidence. Host logs and outbound messages do not replace it. Failed fully rolled-back commands can be recorded in host operational logs with their correlation IDs, but do not appear as committed transitions or successful receipts. Administrative definition and task-catalog changes use the host's management-audit facility. Record actor, action and result; include content revision/hash for definition changes and code, before/after metadata and lock versions for catalog changes.

Host durable event storage must participate in the same transaction. It can use an existing transactional outbox or publication registry. Core defines event meaning and identifiers; the host owns storage adaptation, delivery, retry and retention. No email, external call or nontransactional publication is treated as reversible SQL work. Delivery is at least once and consumers must be idempotent.

## 18 Optional dependency index

The initial nine-table schema can resolve task-code dependencies by parsing relevant workflow definitions. Compatibility checks include every dependency of workflow versions with nonterminal instances and every version still eligible to start, including future task nodes. Change-impact analysis may also inspect draft definitions and runtime activity references. Catalog entries remain retained regardless of the scan result.

If reverse-reference queries for compatibility or change-impact analysis need relational support, add `workflow_definition_task_type_dependency` with primary key (definition_version_id, task_type_id), foreign keys to workflow_definition_version and workflow_task_type, and a reverse index (task_type_id, definition_version_id). Populate it atomically at publication by resolving the node codes to catalog IDs and treat it as a derived index of immutable JSON. This optional tenth table covers published dependencies; it does not include draft references or establish deployed-host compatibility. It is not required to prevent catalog deletion, which V1 does not support.

This optional table is not a node/edge authority and is not the deferred business-decision dependency table. Do not create a generic extension-dependency framework solely for future reuse. Host code extension compatibility still needs a release policy because a database FK cannot pin the behavior of a Java action or resolver.

## 19 Migration and retention plan

Create objects in dependency order: workflow definition; workflow version; task-type catalog; instance; variable; activity; task; receipt; history. Add the optional dependency index only if selected. Supporting UNIQUE keys must exist before creating their composite FKs. Every FK uses NO ACTION; no cascading deletion is configured for retained execution evidence.

Package versioned Liquibase resources under `db/changelog/workflow/` with stable entry point `db/changelog/workflow/workflow-master.xml`. Keep applied changeset identifiers and resource paths stable through the later source move into APOLO. Core does not instantiate a migration runner. Test fixtures belong to the test host's changelogs.

The initial migration contains only the selected nine-table schema, indexes and constraints. It uses BIGINT lock_version columns on the four versioned entities in section 5.2, with values managed by JPA. It contains no SQL ROWVERSION columns, task-type business-version/outcome tables, business decision tables, general node/edge tables, parallel execution tokens, deferred policy names or independently owned core outbox.

Revisions 1.1–1.4 were design revisions, not upgrade scripts. The implemented core master includes this ordered chain; preserve every applied changeset identity/path:

| Migration | Delivered change |
| --- | --- |
| 001 | Nine tables, typed JSON variable column/checks, completion presence/uniqueness and activity lookup indexes. JSON storage is already in the initial schema. |
| 002 / F004 | Remove expression_policy only when the legacy definition-version table is empty; populated legacy conversion remains separate work. |
| 003 / F009 | Raise history details from 256 KiB to 4 MiB UTF-16. |
| 004 / F016 | Preserve direct USER assignments while replacing assigned_user_ref with assignment_kind/assignment_ref and replacing OPEN-task indexes. |
| 005 / F017 | Add nullable assignee_ref; backfill OPEN USER tasks only and add consistency checks. |
| 006 / F021 | Raise history details to 24 MiB UTF-16, retaining ISJSON and existing rows. |

F009's historical test-baseline exception does not waive later preserving migrations or authorize production data loss. Deploy compatible engine/web/UI/host readers and writers together; stop incompatible old writers before 004/005 and apply 006 before larger ALL evidence. Do not rewrite published definitions, fabricate old submissions or reset databases to reconcile the design. Host booking-association migrations remain APOLO-owned.

Workflow retirement retains definition versions used by instances or audit. All task-type catalog rows and their immutable codes remain retained, even after every referencing instance or definition has been purged. Disabling task-type selectability preserves host support needed by existing definitions and activities. Receipt retention and runtime-history retention are separate policies. Purging completed instances requires an explicit host-approved procedure that first establishes retention eligibility, preserves required evidence, respects outgoing delivery obligations and deletes child records in controlled order. Catalog rows are excluded from these purge operations. Do not implement routine cleanup through ORM cascade remove or orphan removal.

TASK_COMPLETED history is operational submission storage as well as audit evidence. TASK_CREATED and shared ACTIVITY_INPUTS_CAPTURED facts supply immutable inputs, and ACTIVITY_ENTERED/ACTIVITY_COMPLETED sequences supply F022 historical cutoffs. Retention must preserve these dependencies while affected tasks remain operationally readable, not only while an instance is running. Keep it readable for all required execution dependencies and the agreed audit period; a still-running instance must not lose a result it may consume. Until a dependency-aware retention policy is specified, retain all completion submissions for running instances. Do not purge completion records independently while their completed task rows remain in the retained runtime set. Retain the pinned contracts/readers needed to interpret evidence. Cleanup, archival and authorized redaction must account for execution dependencies and legal/access policy; an exceptional removal must explicitly resolve affected execution and report unavailability rather than returning an empty result or silently changing a decision. Exact retention durations and archival/redaction behavior remain host specification work.

The migration must be verified against the effective SQL Server compatibility level and configured collation. JPA mappings validate the managed schema; they do not create or update production tables. Hosts run migrations before admitting compatible work and JPA validates mappings; publication and runtime integration validate applicable capabilities. No universal startup readiness/capability verifier beyond delivered checks is implied. Existing published versions remain compatible with the deployed codec and expression policies.

## 20 Verification scenarios

These scenarios preserve required guarantees and broader design expectations. Delivered feature specifications and implementation records own actual acceptance evidence; future-only scenarios are not claims of implemented commands. Select checks for the changed feature or an explicitly selected integration gate rather than rerunning the entire matrix. This documentation reconciliation adds no execution evidence or APOLO acceptance.

| **Scenario** | **Required evidence** |
| --- | --- |
| Optional business correlation | Creation accepts a valid complete pair or two nulls and rejects a partially populated pair. Subsequent attempts to assign, replace or clear the pair are rejected. |
| Correlation lookup | Category and exact-pair queries return matching instances. Multiple instances may share a pair; correlation does not grant access or determine booking membership. |
| APOLO booking associations | APOLO stores the returned instance ID and uses the engine API to retrieve its state, tasks and history, including when the correlation pair is null. Several bookings may share that ID. Task details and host actions resolve authorized members through host data; possession of the ID does not grant access. Prior execution links and review membership survive resubmission. |
| Atomic start and associations | Required links exist before a start-time action needs them. Failure rolls back the new instance and links; an identical successful retry does not recreate either. Changed membership inputs with the same successful command ID conflict. |
| Catalog identity | Reject duplicate codes and attempts to rename any existing code, including an unused one; portable definitions resolve the same code even when catalog IDs differ across environments. |
| Catalog edits and selectability | Version-checked name/description edits leave published node rules unchanged. A nonselectable code is rejected for new publication but remains usable by existing published workflows, new starts of those workflows and their future activities. |
| Catalog retention | Catalog administration/imports reject physical deletion regardless of reference count; retention jobs preserve entries after all referencing runtime data is purged. Disabling an entry leaves its row and code intact and reserved. |
| Concurrent catalog references | While a definition save introduces a reference to a catalog code, attempts to rename or delete that code are rejected independently of reference scans. The saved reference cannot be orphaned by catalog administration. |
| Host support | Unknown or unsupported host codes fail explicitly; a retained catalog row alone does not establish executable support. |
| Multiple tasks | Create three tasks in one activity; the schema accepts all three and rejects a repeated task_key in that activity. |
| Shared task type | Each task projection resolves its code through the activity; there is no independent task-type column to drift. An unknown activity.task_type_id is rejected by its FK. |
| One process position | A second ACTIVE activity is rejected. Sequential entry follows closure of the prior activity; same-node re-entry remains schema-supported future loop scope. |
| Partial contribution | One task completes; instance token and history change; activity stays ACTIVE; no transition/service finalization occurs. |
| Final contribution | Last required task closes the activity and causes one transition and one set of finalization effects. |
| Decisive outcome | One permitted non-contribution outcome closes the activity, cancels remaining open tasks, and preserves earlier contributions. |
| Competing reviewers | Two completions based on the same instance version cannot both pass the early checked write. In particular, two final contributions cannot leave an ACTIVE activity with every task completed. Stale sibling behavior matches the documented client contract. |
| Numeric locking and early claim | Hibernate initializes and updates BIGINT lock_version without application assignment. A stale expected version fails before business effects, including when the loaded entity already has a newer version. SQL evidence shows the checked parent update before child/domain changes. |
| History reservation | The early claim reserves the first actual event; later events use distinct sequence values. Commit leaves last_history_sequence at the highest persisted event, and rollback removes the reservation with the command's effects. |
| Final result version | A command with multiple instance flushes returns and saves the actual final managed version, not expectedVersion + 1. JSON round trips preserve its full BIGINT precision. |
| Failure after final vote | Downstream failure rolls back the final task completion, aggregate closure and all current-command effects, preserving earlier committed contributions. |
| Node outcome membership | Two nodes sharing one task code may allow different outcomes/inputs. The engine rejects a completion allowed only at the other node, rejects an undeclared ALL contribution key at publication, and continues using the pinned rules after another workflow version is published. SQL is not claimed to enforce JSON outcome membership. |
| Ownership | History cannot reference another instance's activity or another activity's task. |
| Reassignment | F026 allows authorized reassignment of OPEN tasks and preserves task identity and roster. F017 claim/release changes ownership within the effective target. |
| Actor policy | Host distinct-person/no-self-approval policies remain required where selected. Administrator release grants no completion override; general override commands remain deferred. |
| Snapshot consistency | Material changes cannot combine old contributions with decisions for a new review revision. |
| Duplicate result | Retrying an earlier command returns its original result even after the instance has advanced. Changed domain input with the same successful command ID conflicts. |
| Draft validation | Saving validation metadata does not invalidate an unchanged content revision; changed content does. |
| Scalar values | Missing, explicit null and populated values remain distinct; wrong typed columns and inexact numeric narrowing are rejected. |
| Structured result contract | Simple tasks complete without a result where allowed. Required/forbidden results, nested schema errors, duplicate JSON keys, unsupported contracts and exceeded payload/depth/collection/string limits are rejected without committed effects. Exact values survive storage/readback. |
| Host decision validation | Reject foreign, duplicate or missing bookings, unauthorized decisions and mismatched review material under the declared host contract. A valid JSON shape alone never grants membership or access. |
| Completion integrity | Each completed task has one matching TASK_COMPLETED record; duplicate completion events and missing task/activity references fail constraints. Open/cancelled tasks have no successful completion evidence. |
| Result idempotency | Formatting/property order differences replay identically; changed nested decisions/comments conflict. Array order and omitted/null/number handling follow the versioned canonicalization contract. Successful replay bypasses state-dependent validation. |
| Result retrieval | Exact task/activity submission reads distinguish no completion from an omitted OPTIONAL result and enforce their host access contracts. Activity pages contain completed submissions only; roster reads supply other states. Missing required evidence fails. F022 receiving-task context is a separate narrow projection. |
| Final-result visibility and rollback | The downstream action sees all activity submissions, including the final uncommitted one, in the shared transaction. Failure removes only the current command's completion/result/effects; prior contributions remain. |
| Mixed booking decisions | Both tasks contribute REVIEW_SUBMITTED; host aggregation applies its declared per-booking policy. Nested rejections do not implicitly trigger a group REJECT or partial task lifecycle. |
| Result isolation | Completion JSON neither overwrites another reviewer's result nor implicitly modifies variables/SpEL facts. Explicit service bindings may copy validated scalar or JSON outputs into separate current variables; later replacements never change original submissions. |
| Result retention and compatibility | Cleanup preserves operational dependencies and retained task evidence. Older valid no-result completions remain readable; result contracts/readers remain available. Exceptional redaction/unavailability is explicit. |
| JSON values and nulls | Round-trip declared objects, arrays and nested combinations exactly. Distinguish absent variable, explicit SQL NULL, nested null, missing property, empty object and empty array. Reject mismatched typed columns, malformed JSON, duplicate keys, unsupported root/scalar values and undeclared writes. |
| JSON bounds and precision | Reject oversized/deep/large collections and lossy numeric conversions before acceptance; enforce per-document, per-instance and full request/read/history budgets. No required evidence is truncated to fit. |
| JSON mutation protocol | Explicit start/service writes and named human-output creation follow the instance transaction and atomic rollback. Provider persistence stores whole-value replacements. Standalone variable-update commands remain deferred. |
| JSON reads and routing | Authorized whole-value reads and F009 expression navigation see correct transaction-local values. Missing properties and valid out-of-range indexes return null; uninitialized reads, invalid index types and unsafe null traversal fail. Forbidden methods, mutation and host-object traversal remain rejected. |
| JSON replay and evidence | Formatting/key order replay identically; changed nested content conflicts. Array/null/number semantics follow the pinned contract. History retains accepted writes and actual routing inputs after later variable replacements. |
| JSON compatibility and ownership | Old scalar definitions/rows remain valid; unsupported codec/expression versions fail. JSON booking IDs do not override host membership/authorization or mutate a frozen review; current execution data cannot rewrite task results. |
| Captured inputs and ALL outputs | F020/F021 retain immutable incoming snapshots, one shared ALL input fact, no output on nonfinal contribution, and resolution-only output creation with original-answer provenance. |
| Previous decisions | F022 uses retained entry/completion sequences, a stable cutoff and receiving-task access; later or same-stage decisions are excluded and corrupt required evidence fails. |
| Preserving migrations | 004 retains original USER assignments; 005 backfills only OPEN USER owners; 006 enlarges history capacity without rewriting retained facts. Use the owning feature SQL evidence. |
| Durability and commit | Event handoff and receipts survive committed process restart; outer commit failure never reports success. |

The original architecture recorded a no-automated-tests instruction. The owner's 2026-11-01 policy in [AGENTS.md](../../AGENTS.md) supersedes that restriction for isolated unit tests and guarded integration tests on `localhost:1433/apolo_workflow_test`. Use that environment for repeatable SQL Server evidence; concurrency, rollback and recovery still require scenario-specific passing results before production approval.

## 21 Decisions before implementation approval

The following register distinguishes the selected simplifications from remaining proposals and verification gates. Review them as a coherent contract; changing one may affect columns, constraints, command results and client behavior. The schema and supported executor subset are implemented through F022; the register distinguishes those delivered contracts from remaining host verification and future scope.

| **Decision** | **Status and baseline** |
| --- | --- |
| Task multiplicity | Confirmed requirement: multiple tasks per activity in V1. |
| Business associations | Selected: APOLO stores the engine instance ID in host data and uses the engine API for instance queries. A booking column or host association table represents the links according to host cardinality/history needs. Review membership, host data and migrations remain outside the core schema. |
| Engine business correlation | Selected: retain separate nullable business_type/business_id columns, both present or both null, immutable from creation and indexed without global uniqueness. Correlation is optional lookup metadata, not membership or authorization. |
| Process concurrency | One active process position and one active human activity per instance. |
| Completion policies | SINGLE and ALL only; ALL contribution key and first committed decisive outcome semantics. |
| Roster and assignment | Fixed required task slots with USER/GROUP/ROLE targets, F017 claim ownership and F026 audited reassignment of open tasks. Hosts own distinct-person policy. |
| Task catalog | Selected: workflow_task_type with code immutable from creation, retained rows, editable metadata, selectable flag and technical lock_version. Physical deletion and code renaming are unsupported, including for unused entries; use selectable = false. No business version or publication lifecycle. |
| Node contracts | Pinned outcomes, incoming bindings, result/comment contracts, claim/completion policy, optional outputVariable and previousDecisions. Delivered schemas validate the supported subset; nonempty guards and resolver bindings remain deferred. |
| Structured submissions | Implemented F018: bounded object result alongside outcome/comment, immutable TASK_COMPLETED evidence with empty legacy inputs; no new result table/column. F020 incoming data is captured separately. |
| Submission validation and retrieval | F018 specifies/implements pinned schema validation, exact codecs/bounds, optional host business validation and exact-task/activity reads. F019–F021 bindings preserve same-transaction final-result visibility; F022 has separate receiving-task comment access. |
| Completion storage | F001 implements presence/filtered uniqueness and exact-activity lookup indexes. Executor integrity checks enforce required evidence and matching state/outcome; feature records own scoped database verification. |
| Submission lifetime | Selected: preserve operational dependencies and audit evidence; no ordinary result edits or independent cleanup of retained task completions. Host retention/redaction/archival details remain open. |
| Task type ownership | Selected: one task_type_id on each activity; tasks inherit it through activity_id and expose the resolved code in projections. |
| Client concurrency | Required instance token for mutations; nonfinal contributions can make siblings stale. Delivered web contracts preserve BIGINT IDs/versions as decimal strings. |
| Row token mapping | Implemented BIGINT/Long JPA @Version with Hibernate ownership. Feature records cover scoped early-write/final-version evidence; APOLO provider compatibility remains an integration gate. |
| Instance coordination | Selected approach: change last_history_sequence and flush before business mutations; preserve the first reserved event number and use the actual final managed version in the receipt. No assumption of an immediate OPTIMISTIC_FORCE_INCREMENT. |
| Exact values | DECIMAL(38,10), bounded strings, DATE and DATETIMEOFFSET(7); no silent rounding. |
| JSON execution variables | Selected: JSON alongside six scalar types, mutable object/array values in nullable workflow_variable.json_value with matching-column and syntax checks. Typed null uses SQL NULL; nested nulls remain JSON data. No new table. |
| JSON access and writes | Implemented bounded whole-value reads, start/service bindings, human output creation and restricted #vars navigation. No implicit result merging, patch API, standalone update command or arbitrary API path projections. |
| JSON specification gates | F009/F018–F021 specify exact values, supported bindings/navigation and capacities; their records own scoped safety/persistence evidence. Execution-data field schemas, JSON-path search/indexing and APOLO compatibility remain separate scope. |
| Evidence | Versioned history JSON and immutable receipt result JSON; current variables remain relational. |
| Dependency projection | Optional tenth table keyed by definition_version_id and task_type_id only if relational reverse lookups justify it. |
| Publication and retirement | Workflow definitions retain immutable published content and a mutable lifecycle envelope. Task codes use selectability; retained definitions/activities and executable host compatibility remain protected. |
| Host operations | Shared command transaction, durable recorder, management audit, recovery and retention contracts required. |

The database design remains applicable if a later iteration adds richer collective policies, but those policies require explicit semantics and migrations. Do not preload ANY or QUORUM into accepted enum values as if their execution rules already existed.

## 22 References

[S0] Reusable Workflow Engine, Technical Design, APOLO-first Embedded Library, version 0.8, 24 October 2026, sections 7 to 13 and decision register; database review in this conversation; project-owner instruction on 24 October 2026 to support multiple tasks per activity in the first iteration; owner-requested observations incorporated on 25 October 2026 for the simple task catalog, workflow-node outcomes, activity-owned task type and numeric JPA locking; subsequent owner decisions to retain immutable catalog codes and to use APOLO-owned business associations with optional two-column engine correlation. These later directions supersede the earlier one-task-per-activity, versioned task-registry, SQL ROWVERSION and mandatory business-reference proposals for this design. This is original provenance; the current reconciled architecture and language documents below govern the present baseline.

Revision 1.3 additionally records the owner's 25 October 2026 acceptance of structured completion results in existing history, pinned validation contracts, exact-task/activity retrieval, full-payload idempotency and operational retention. It extends the scalar-only task submission contract while retaining scalar variables and host-owned business associations/material. Project constitution v2.1.0 and supporting guidance record this scoped precedence; older illustrative contracts do not override it.

Revision 1.4 records the owner's subsequent selection of JSON workflow variables alongside existing scalar types, extending the earlier scalar-only execution model. Mutable execution context, immutable task submissions and authoritative host business associations remain distinct. Project constitution v2.2.0 and supporting guidance record the new precedence. Microsoft ISJSON object/array semantics and duplicate-key limitations were checked on 25 October 2026 for the proposed storage checks [S1]; actual host SQL Server/JPA compatibility remains unverified.

**Current repository sources — revision 1.5:** use the [architecture v0.12](workflow-engine-design.md), [language design v0.3](workflow-definition-language-design.md), [source precedence](../design-sources.md) and [core migration master](../../workflow-core/src/main/resources/db/changelog/workflow/workflow-master.xml). The historical references below retain their original dates and do not establish current APOLO compatibility.

| Feature source | Contract / evidence ownership |
| --- | --- |
| [F001](../features/F001-initial-database-schema/spec.md), [F004](../features/F004-definition-management-validation/spec.md) | Initial DDL, definition lifecycle/provenance and expression-policy removal. |
| [F007](../features/F007-typed-bindings-service-tasks/spec.md), [F009](../features/F009-json-execution-variables/spec.md) | Typed values, whole-value bindings, exact JSON and retained execution evidence. |
| [F011](../features/F011-sequential-human-stages/spec.md) | Acyclic sequential stages and mandatory human participation. |
| [F016](../features/F016-shared-group-role-task-assignment/spec.md), [F017](../features/F017-task-claim-release/spec.md) | Assignment/claim columns, preserving migrations, eligibility and receipts. |
| [F018](../features/F018-structured-task-submissions/spec.md), [F019](../features/F019-named-task-output-variables/spec.md), [F020](../features/F020-human-task-input-bindings/spec.md) | Immutable results, original-submission bindings, named outputs and captured inputs. |
| [F021 specification](../features/F021-all-collective-review/spec.md), [implementation](../features/F021-all-collective-review/implementation.md) | ALL resolution, shared inputs, receipt compatibility, capacities and migration 006 evidence. |
| [F022 specification](../features/F022-previous-decision-comments/spec.md), [implementation](../features/F022-previous-decision-comments/implementation.md) | Bounded previous-decision projection, stable cutoff, retention/access and SQL/read evidence; no migration. |

Each feature's sibling implementation.md records actual checks and remaining gaps. Reuse applicable evidence with its scope; this document neither reruns those checks nor converts historical NOT RUN results into passes.

[S1] Microsoft Learn. ISJSON Transact SQL. Syntax validity and duplicate-property limitation. <https://learn.microsoft.com/en-us/sql/t-sql/functions/isjson-transact-sql>

[S2] Jakarta Persistence 3.1. Sections 3.4.2, 3.4.4.1 and 11.1.56: provider-managed version attributes, deferred forced increments and supported version types. [Jakarta Persistence specification](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html).

[S3] Microsoft Learn. decimal and numeric Transact SQL. Precision, scale and conversion behavior. <https://learn.microsoft.com/en-us/sql/t-sql/data-types/decimal-and-numeric-transact-sql>

[S4] Microsoft Learn. Collation and Unicode support. Identifier comparison and BIN2 behavior. <https://learn.microsoft.com/en-us/sql/relational-databases/collations/collation-and-unicode-support>

[S5] Microsoft Learn. datetimeoffset Transact SQL. Offset preservation and precision. <https://learn.microsoft.com/en-us/sql/t-sql/data-types/datetimeoffset-transact-sql>

[S6] Microsoft Learn. Create filtered indexes. Unique filtered indexes and their limits. <https://learn.microsoft.com/en-us/sql/relational-databases/indexes/create-filtered-indexes>

[S7] Microsoft Learn. Unique constraints and check constraints. NULL and UNKNOWN behavior. <https://learn.microsoft.com/en-us/sql/relational-databases/tables/unique-constraints-and-check-constraints>

[S8] Microsoft Learn. Create foreign key relationships. Referenced unique keys and nullable composite relationships. <https://learn.microsoft.com/en-us/sql/relational-databases/tables/create-foreign-key-relationships>

[S9] Microsoft Learn. Primary and foreign key constraints. Referential integrity and FK indexes. <https://learn.microsoft.com/en-us/sql/relational-databases/tables/primary-and-foreign-key-constraints>

[S10] Spring Framework reference. Rolling back a declarative transaction. <https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/rolling-back.html>

[S11] Spring Framework reference. Transaction propagation. <https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html>

[S12] Hibernate ORM 6.6 User Guide. Dedicated version numbers and provider ownership of the version value. [Hibernate optimistic locking](https://docs.hibernate.org/orm/6.6/userguide/html_single/#locking-optimistic).

The original SQL Server and Spring mechanism references were checked on 24 October 2026. The Jakarta Persistence and Hibernate locking references were checked for revision 1.1 on 25 October 2026. They do not establish APOLO's installed dependency versions. Use the matching versioned references during O7 verification.
