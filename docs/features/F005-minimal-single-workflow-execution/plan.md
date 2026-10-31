# Plan — F005 Minimal SINGLE workflow execution

Implements [specification revision 1](spec.md). Track execution in [tasks.md](tasks.md); record actual results/gaps in [implementation.md](implementation.md).

## 1. Baseline and boundaries (R01, R07, R12)

Inspect repository status, AGENTS.md, constitution/constraints/assumptions, F001 storage and F002–F004 contracts/source before implementation. Preserve the pending F006 documents. Confirm all runtime entities currently provide mappings/read access only and require invariant-preserving creation/mutation methods. Keep exactly two Maven modules, Java 17 APIs/bytecode on JDK 21 and unchanged F001/F004 changesets. No schema migration is planned for F005.

Use technical layers beneath `io.github.leontecristian.workflow.core`:

| Package | Planned responsibilities |
| --- | --- |
| `service.runtime` | WorkflowExecutionService, WorkflowRuntimeQueryService; internal command validation, receipt codec/fingerprint, progression/history helpers; SingleTaskAssignmentResolver, StartLinkAction and WorkflowEventRecorder extension interfaces. |
| `dto.runtime` | Commands, trusted context, HostStartIntent, assignment/start-link contexts/results, immutable views/results/pages/submissions/events. Reuse established enums. |
| `exception.runtime` | WorkflowExecutionException and fixed categories. |
| `entity.runtime` | Controlled constructors/transitions for instance/activity/task/history/receipt, immutable evidence access; existing scalar FK mappings retained. |
| `repository.runtime` | Narrow exact/ownership/cursor/receipt queries; no generic CRUD facade or bulk instance writes bypassing @Version. |
| `service.definition` | Shared internal minimal-model/integrity loading separated from publication-only checks. |

Keep concrete services; use interfaces only for the three real host extensions. Do not create `api/impl/common` hierarchies, new modules, unbounded repositories or a general workflow framework for the one selected graph.

## 2. Shared pinned-model loader (R01, R11)

Refactor F004 validation's pure structural/graph parsing into a focused definition-owned loader that returns its immutable MinimalWorkflowDefinition. Keep diagnostic/schema rules and canonical hashes unchanged. Runtime must reuse those rules without invoking validateDraft/publish, checking authoring selectability or changing validation evidence. Designer content remains retained and outside execution.

Introduce a documented core-to-core `WorkflowDefinitionRuntimeReader` service in service.definition, with immutable `ExecutableDefinition` DTO in dto.definition.document. It returns exact version identity/hash, immutable minimal model and lifecycle. `loadForStart(versionId)` is MANDATORY: lock the managed version PESSIMISTIC_WRITE, refresh under the lock and recheck PUBLISHED. `loadPinned(versionId)` is read-only REQUIRED and allows PUBLISHED/RETIRED. Both check stored identity/canonical integrity and supported subset; no entities cross the boundary. Runtime must not query definition repositories directly. The narrow internal integration is not a public host editing API.

The start eligibility lock is held until host completion, orders against F004 retirement's update and does not itself mutate/increment definition metadata. Ordinary retirement may read first, but its versioned update must wait behind the held lock; conversely a winning retirement must be visible after refresh. Verify actual SQL Server behavior. Existing-instance completion claims the instance before reading its pinned version, without a definition write lock; avoid introducing an opposite instance/definition lock order.

Resolve task catalog with documented F002 exact lookup including disabled rows. F004's publication lock/selectability operation is not the runtime lookup. Snapshot real executable resolver IDs once, reject duplicates and ensure host capability consistency at command time. Missing runtime dependencies should fail commands, not prevent management-only bootstrap. F006 validation fixtures alone must fail runtime resolver availability.

## 3. Commands, normalization and replay (R06–07, R11)

Implement immutable API records and strict bounds first. Centralize UTC conversion and identifier/comment validation without modifying host-global ObjectMapper/Hibernate settings. Start/complete command shapes cannot carry variables/results/guards. Validate the host callback/intent pairing before any invocation.

Implement deterministic receipt request normalization exactly as spec, using explicit fixed-field JSON construction and Long strings. Reuse/extract only the necessary pure canonical string encoding from definition code; preserve F004 behavior and do not introduce a second near-identical definition codec. Store SHA-256 and `wf-runtime-request-v1`; deserialize result schema 1 into detached DTOs with bounded input and known fields. Copy collections/arrays defensively. Unsupported stored versions must be explicit.

Both commands and findReplay first validate basic shape/context, then match the receipt before state-dependent checks. Store execution snapshots independently of the replayed flag. Distinguish the known receipt PK violation from unrelated constraints, including null constraint names; preserve causes. Transactions invalidated by optimistic/unique failure are allowed to fail, never queried for recovery. Document the host's fresh read-only findReplay after complete rollback and its access check before exposing a result. No internal retry loops, REQUIRES_NEW or hidden transaction manager.

## 4. State mutation and history (R02–05, R08, R11)

Add controlled entity factories/transitions, retaining protected JPA constructors and private fields. History/receipt remain insert-only; no public setters/deletes. Persist through EntityManager from runtime services; use targeted repository reads with scalar ownership verification. Do not generate task.instance_id/task_type_id/result_json columns or touch variable rows.

Start sequence: validate/check replay and required adapters; load/lock exact published model; insert initial instance to obtain ID; invoke optional trusted start-link callback; resolve/validate assignment; create activity/task; append specified history and set current position/last sequence; final flush; capture detached result; persist receipt and call durable recorder; flush failures before return. Any exception aborts the host transaction. Provider IDENTITY gaps on rollback are acceptable; committed orphan state is not.

Complete sequence: validate/check replay; load/refresh instance and check expected token; reserve first event sequence with overflow check; flush that managed @Version update; refresh owned activity/task after the claim; verify pinned model, capabilities, ownership/roster, actor, outcome/comment; close task/activity and advance to END; append remaining history; update sequence/final state; final flush; capture actual token and serialize receipt; record events and flush. Revalidate after the claim rather than trusting an earlier persistence-context snapshot. A losing claim invokes no host recorder or child mutation.

Keep history sequence allocation explicit and overflow-safe; activation sequence comes from ACTIVITY_ENTERED. Final result/receipt sees actual managed lockVersion after all instance writes, including early claim and final progression. Receipt/history-only flushes must not dirty the instance again. Add a scoped command-reentry guard with finally cleanup; callbacks must not recursively execute runtime commands or mutate core entities through unsupported paths.

Use a bounded minimal progression helper: START and END cost one each, human/transition cost zero, unsupported nodes fail. Preserve the limit-of-50 contract without adding support for future node types or manufacturing complex graphs just to claim boundary coverage. Full 50/51 execution evidence remains for a later feature capable of those paths.

## 5. Durable events and reads (R08–10)

Build one immutable WorkflowEvent per committed history fact using deterministic instanceId:sequence IDs and the exact limited payloads in the spec. Require a recorder per runtime command; call once with the ordered batch inside the transaction. No default no-op recorder, in-memory outbox or Spring after-commit publisher. Recorder implementation must use the host's participating durable storage; this feature defines the SPI and preserves the integration gate rather than adding host-specific schema to core.

Runtime query service maps immutable scalar views under read-only REQUIRED. Add SQL-bounded ownership joins for instance tasks and assigned OPEN tasks; use limit+1 cursor queries. History lists return envelopes, exact reads return bounded details. Dedicated task/activity submission readers validate the TASK_COMPLETED schema and owner/state agreement. Keep same-transaction visibility and no implicit latest-review lookup. Apply page count/byte limits and continuation after the last emitted item; fail if a single item cannot fit. Do not use total-count scans or load full histories into memory.

## 6. Verification and handover (R12)

Review contract/DDL compatibility, state invariants, receipt equality and call/flush/lock ordering before packaging. Select installed JDK 21 explicitly and run `./mvnw.cmd '-Dmaven.test.skip=true' package`. Do not generate/run automated tests or an automatic scenario harness. Static walkthroughs and compilation are not behavioral PASS results.

Manual acceptance AC01–AC14 requires a trusted host transaction facade, real assignment/task support and a durable recorder with inspectable storage. Current host/F006 management fixtures do not supply these runtime adapters. During implementation, document available adapters and scope with the owner before dependent host-specific schema/application work; do not expand F006 or create production business rules implicitly. Core implementation can progress independently. Any unavailable runtime scenario stays NOT RUN/BLOCKED with the missing adapter/authorization stated; it does not justify a substitute no-op sink.

When manual runtime verification is authorized and real adapters exist, record isolated development identities, explicit definition version, command UUIDs, before/after rows, history/receipt snapshots and durable event records. Exercise both new-command paths, semantic-conflict/replay variants, disabled-catalog/retired-version behavior, invalid assignment/comment/actor, host rollback/failure and same-transaction submission reads. Observe true concurrent requests for races; sequential stale attempts prove only stale-token rejection. For duplicate-command failure, demonstrate rollback then fresh receipt resolution without rerunning mutations. Capture SQL/flush evidence for early claim/final tokens and lock ordering.

Durable-record insertion/rollback is not evidence of eventual delivery/recovery. Actual host dispatch/restart/at-least-once obligations remain explicit production gates. Do not create automated tests, alter applied schema/checksums, clear data or start a database merely to complete a checklist. Keep raw logs under ignored target, stop only owned processes after identity checks, and record retained records and process state.

Finish by reconciling spec/plan/tasks/implementation and navigation, checking relative links and whitespace. No build/startup is needed for this documentation-preparation change. Commit/push only on request. F006 stays a separate management console until a later request explicitly adds runtime pages/endpoints and real sandbox adapters.
