# F027 implementation plan

Targets [specification revision 6](spec.md). **PROPOSED; no implementation authorized.** Execution is tracked in [tasks.md](tasks.md); actual outcomes belong in [implementation.md](implementation.md).

## P01 — Public contracts and core facade

Use `io.github.leontecristian.workflow.core.service.runtime` for the concrete `WorkflowAuthorizedRuntimeReadService`, read-policy integration and the existing authorization adapters. Use `core.dto.runtime` only for a genuinely necessary detached context; reuse `TaskEligibilityContext`, existing views/pages and query records for reads; retain `WorkflowCommandContext` only at the separate cancellation-authorization boundary. No new module, repository abstraction or service interface/implementation pair.

Proposed core policy shape (method names are design, not existing API):

```java
public interface WorkflowRuntimeReadAuthorization {
    boolean canMonitor(String actorRef);
    default boolean canAdministerTasks(String actorRef) { return false; }
    boolean canListInstance(String actorRef, WorkflowInstanceSummary instance);
    boolean canReadInstance(String actorRef, WorkflowInstanceView instance);
    boolean canReadTask(TaskEligibilityContext context);
    boolean canReadActivity(String actorRef, WorkflowActivityView activity);
    default boolean canCompleteTask(TaskEligibilityContext context) { return false; }
}
```

This corrects the earlier illustrative shared instance-summary authorization: monitor and exact-instance policy remain separate, and an exact read retains its evidence-validation boundary. No default true grants. Operation authorization is deliberately absent except for the unmatched completion hint. My Tasks uses `canReadTask` as its host predicate, followed by core current-assignee/eligibility checks; it does not require ownership `canAccess`. The ownership adapter serves only claim/release operations, their availability and recovery. Operation adapters may be supplied for advisory availability even when mutation endpoints are disabled, but are not prerequisites for read visibility or task discovery.

Implement the selected simple policy inside the host adapter: collection grants check administrator status; resource/list read decisions immediately allow administrators, otherwise retain existing ordinary-user rules. Use the host's existing trusted administrator lookup, not a new role framework or core role-name check. Do not add domain-specific booking/budget administrator filters or a per-task administration-visibility callback. The existing `canReadTask` callback suffices for administration rows because every administrator passes it. Keep `canCompleteTask` and operation adapters outside this shortcut; My Tasks keeps its personal eligibility selection.

Facade methods accept trusted `String actorRef` plus the existing query/ID/cursor/limit arguments and return the seven existing page/view types. Use `listMyTasks` to avoid confusing eligibility discovery with the existing assignee-only query. Provide neutral collection-access checks usable before web query parsing; map monitor/admin denial to the existing web 403 codes. Exact denial uses core NOT_FOUND. A small core read-access exception/category mapping may express collection denial if needed; do not import web exceptions into core or expose unclassified generic exceptions as 500 for ordinary denial.

Keep optional policy resolution lazy through the existing provider pattern, or construct the facade explicitly under runtime-read configuration; select one during P01 and test absence of new mandatory dependencies on ordinary core import. Do not register two implementations. Required web identity/read policy must fail configuration when missing. Direct-core hosts explicitly supply the same policy and actor reference. Do not add hidden transactions/caches or change the existing participation in host read transactions.

## P02 — Authorized queries and availability

Delegate persistence to `WorkflowRuntimeQueryService`. Extract the bounded instance-task filtering loop from `ConsoleRuntimeReadHost`; preserve limits and continuation exactly. Reuse existing eligible/admin algorithms rather than consolidate them into a configurable generic paging framework. Add monitor visibility filtering over summaries with retained candidate continuation. Exact reads load once per specified snapshot step and invoke read policy on the loaded DTO; `canReadTask` no longer re-fetches via an HTTP exception wrapper.

For My Tasks, pass `task -> readAuthorization.canReadTask(new TaskEligibilityContext(task, actorRef))` into the existing eligible-query predicate. Preserve core candidate selection, the current-assignee OR assignment-eligibility expression, scan bounds and continuation. The read policy already enforces ordinary-user visibility; do not add an ownership-operation access check before or after it. Keep administrator visibility broad while preserving personal eligibility. Shared membership lookup remains necessary when evaluating shared eligibility and must not be confused with ownership authorization.

Compose availability in core alongside `WorkflowTaskEligibilityService`, reusing ownership, cancellation and reassignment adapters through their established failure-handling pattern. Preserve the existing web command-enabled gates in `web.service.runtime`/controller projection. Keep completion's default-false host veto and current sandbox policy; do not add ownership-business permission to completion if it would change the existing predicate. Keep command execution services untouched.

Ordinary facade calls and task-availability policies need only `actorRef` (or `TaskEligibilityContext`). Cancellation availability alone takes an explicit `WorkflowCommandContext` obtained separately after actor-only instance authorization and the ACTIVE check. Verify that its actor matches the authorized read actor before constructing the existing `WorkflowCancellationAuthorizationContext`; reject mismatch as HOST_CONFIGURATION. Do not fetch command context eagerly for all reads. Preserve existing cancellation authorization/failure handling and command execution contracts.

Retain the controller's deliberate reauthorization/token ordering and per-task activity-submission filtering. Moving the existing submission loop into the facade is unnecessary unless needed for the changed policy signature; do not broaden extraction just for symmetry. Preserve label and directory errors independently from authorization errors.

## P03 — Web and sandbox adapters

Use existing packages:

- `io.github.leontecristian.workflow.web.integration.runtime`: replace `WorkflowRuntimeReadHost` with a trusted actor identity adapter and directory/label contract.
- `io.github.leontecristian.workflow.web.configuration`: update `WorkflowRuntimeReadConfiguration`, retaining independent command activation.
- `io.github.leontecristian.workflow.web.controller.runtime`: update `WorkflowRuntimeReadController` to call the facade and check collection grants before parsing.
- `io.github.leontecristian.workflow.web.service.runtime`: preserve `RuntimeErrors`, wire projection and availability activation composition.
- `io.github.leontecristian.workflow.testhost.service.runtime`: migrate `ConsoleRuntimeReadHost`/`ConsoleRuntimeAccess` into policy/identity/directory wiring; reuse `ConsoleTaskOwnershipAuthorization`, `ConsoleTaskReassignmentAuthorization`, `ConsoleWorkflowCancellationAuthorization` and shared assignment provider.

Proposed web integration:

```java
public interface WorkflowRuntimeReadIdentity {
    String currentActorRef(); // trusted authenticated server identity only
}
public interface WorkflowAssignmentDirectory {
    default WorkflowReassignmentTargetPage reassignmentTargets(
            TaskEligibilityContext context, AssignmentTargetKind kind,
            String query, String cursor, int limit) {
        throw new WorkflowRuntimeException(403, "REASSIGNMENT_FORBIDDEN");
    }
    default String assignmentLabel(WorkflowTaskView task) {
        return task.assignment().ref();
    }
}
```

Directory is optional for hosts without reassignment UI; absent support retains denied lookup/reference-label fallback. Required read identity must not force command context construction or a command executor. Authentication/CSRF and request correlation remain host-owned.

The default search exception above is the existing `web.exception.runtime.WorkflowRuntimeException`; it remains within the web integration boundary. Label-only hosts override `assignmentLabel` without a search stub. Do not infer directory-search capability from the presence of this bean: inherited lookup remains denied after the normal access/permission gates. Keep the current response code rather than introduce a new unsupported-operation status or return an empty success page. Assignment labels describe stored targets, independently of whether assignment came from a resolver, picker or supplied reference; no manual-entry UI is planned.

Provide a separate optional web integration for the existing cancellation context requirement, in `web.integration.runtime`:

```java
public interface WorkflowCancellationAvailabilityContextHost {
    WorkflowCommandContext currentCancellationContext();
}
```

This factory supplies context only, not another cancellation permission decision. Invoke it only for cancellation availability when the existing cancellation authorization adapter is present and the instance is readable/ACTIVE. Missing support yields unavailable cancellation; configured failures remain errors, not permission grants. The sandbox can delegate to its existing `ConsoleContext.runtime(null)` construction. Direct-core callers pass their trusted operation context explicitly to the cancellation availability evaluator and need neither web identity nor this web factory. Actual cancellation commands retain their existing context path. Thus hosts providing ordinary reads alone implement only actor identity and read policy, with the existing capability-specific adapters as required.

Do not relocate sandbox assignment-history access policy into the engine. Existing graph/history and command-orchestration callers of `ConsoleRuntimeAccess` may retain wrappers over the facade/policy as necessary; remove only duplicated retrieval/paging now owned by the library and avoid recursion between wrappers and policy callbacks. An existing raw core query inside a host policy is legitimate when needed to decide access.

For revision 3, place the administrator read grant before the existing OPEN-task business-access check in host read policy. Preserve that check and all existing assignment/history logic for ordinary users. Inspect shared-helper consumers so this read grant does not become an operation authorization bypass; command adapters and authoritative command checks remain unchanged. Existing graph/history read consumers honor administrator visibility while retaining their ordinary-user checks. This is the narrow owner-selected policy refinement, not an assertion that all administrator behavior already matches the baseline.

For revision 4, remove the sandbox read policy's indirect dependency on `ConsoleTaskOwnershipAuthorization`: the current `ConsoleRuntimeAccess.canAccessBusiness` delegates there. Reuse a host-private business-access helper or the same existing host business check in both adapters, preserving ordinary-user decisions and error categories. This is internal host composition, not a new public authorization interface. Ownership command/availability/recovery callers continue using the ownership adapter; read-only construction must not need that adapter. Inspect callers before moving the helper so replay and operation checks retain their present behavior.

For revision 5, use that same host-private helper to remove `ConsoleTaskReassignmentAuthorization`'s constructor dependency on `ConsoleTaskOwnershipAuthorization` and its `ownership.canAccess(...)` delegation. Place the concrete helper in `io.github.leontecristian.workflow.testhost.service.runtime` (proposed name `ConsoleTaskBusinessAccess`); it is not a new core/public integration contract. Preserve the existing check of authenticated console actor plus `SandboxRequestStore.linkedToInstance(task.instanceId())`. Inject the helper directly into ownership authorization, reassignment authorization and the read-policy consumer that needs ordinary-user business access. Keep administrator read bypass in read policy, outside the helper. Preserve each adapter's operation-specific decisions and the existing caller's exception mapping. No changes to core reassignment execution/replay ordering or authorization signatures are required. The earlier references to unchanged command adapters mean unchanged contracts/decisions; this explicitly selected host dependency refactoring is included.

### Instance-task list flow

1. Host endpoint obtains `actorRef` from its authentication adapter, or shared web obtains it from `WorkflowRuntimeReadIdentity.currentActorRef()`.
2. Endpoint calls `authorizedReads.listInstanceTasks(actorRef, instanceId, afterId, limit)` under the host's existing read transaction arrangement.
3. Facade loads the instance, applies `canReadInstance` and conceals missing/denied access.
4. Facade queries candidates through core, applies `canReadTask(new TaskEligibilityContext(task, actorRef))`, and scans within the existing 500-candidate budget.
5. Facade returns `WorkflowTaskPage` with source continuation; web preserves serialization/size bounds. Host writes no workflow paging loop.

For My Tasks, omit the general instance grant: call `listEligibleTasks` with the `canReadTask` predicate; core then requires current assignee OR assignment eligibility. No ownership adapter is resolved or invoked for this flow. For administration, require its grant and use one bounded candidate page. These are separate use cases, not flags on the instance-task algorithm.

## P04 — Compatibility and documentation

Update `workflow-core`/`workflow-web` integration documentation and sandbox registration examples to describe required policy/identity and optional operation/directory adapters. Explicitly document Java source migration from all `WorkflowRuntimeReadHost` methods and extra custom permission restrictions. Do not retain an automatic legacy fallback that bypasses authorized reads. Preserve HTTP/UI contracts and the root `WorkflowConfiguration` marker. No database migration, dependency upgrade, frontend redesign or schema work is planned.

On implementation, reconcile F015 R02's paging wording narrowly and add source-precedence/feature-index entries without rewriting historical verification. Constitution principles need no amendment. Keep F027 DRAFT until the proposed integration contract is accepted for implementation; readiness and authorization are separate from completed code.

## P05 — Focused verification

Changed boundaries are authorized read orchestration, policy registration, paging-loop ownership and advisory availability. Select existing suites before adding tests:

- Core: `RuntimeInstanceQueryUnitTest`, relevant `SharedAssignmentUnitTest` read/eligibility cases, and focused facade tests for AC01/02/04/05. Use repository/adapter doubles only.
- Web: `WorkflowRuntimeWebUnitTest`, `WorkflowReassignmentWebUnitTest`; preserve concealed errors, read-only completion/ownership asymmetry, snapshot ordering and directory validation. Add missing cases only for moved boundaries.
- Host: `ConsoleRuntimeAdaptersUnitTest`, `ConsoleInstancesUnitTest`, relevant `ConsoleSharedAssignmentUnitTest`/`ConsoleReassignmentUnitTest` and configuration tests. Retain the bounded empty-page regression and old policy expectations. Inspect test methods before deciding whether larger runtime suites are needed.

For revision 2, add focused isolation assertions to these checks: ordinary reads receive only trusted actor identity and work without invoking/providing a command-context factory; shared-web and direct-core calls preserve the same actor; cancellation availability uses its separate factory only after the read/state gates and rejects actor mismatch. Missing support and configured failures remain distinct. No new command execution tests are needed for this context separation.

For revision 3, adapt the focused host-policy/read tests to prove administrator visibility across different business associations and OPEN tasks, ordinary-user visibility preservation, and separate denied operation hints despite administrator read access. Confirm administration rows need no additional per-task administration grant and My Tasks still selects personal eligible work. Reuse existing pagination checks; no new domain permission matrix, business approval flow or database test is selected.

For revision 4, verify My Tasks without an ownership adapter (including no indirect read-policy invocation), unreadable-but-eligible exclusion, readable-but-ineligible exclusion, current-assignee visibility after membership loss and administrators still needing personal eligibility. Retain ordinary-user business-access denial via read policy. Reuse existing scan/continuation assertions. Check that claim/release availability still uses its adapter and is unavailable when absent; this change does not select fresh command/recovery/database suites because their contracts and execution paths remain unchanged.

For revision 5 (AC06), select focused host adapter tests, including relevant `ConsoleReassignmentUnitTest` cases: construct reassignment authorization without an ownership adapter; verify the shared business check's allow/deny/failure outcomes and that reassignment versus administrative-release permissions remain separate. Inspect existing recovery assertions and reuse unchanged core replay evidence; add a focused isolated regression only if the changed host wiring lacks coverage of its recovery access decision. Do not rerun unrelated database/concurrency/mutation suites for this helper extraction. Core-only/shared-web capability wiring must not require ownership support merely to supply reassignment authorization.

For revision 6 (AC07), add a focused web unit fixture implementing only `assignmentLabel`: verify friendly-label projection and inherited search denial after successful access/permission checks. Retain existing fallback and explicit search-override assertions. No new browser, database or command execution checks are selected for this default method.

REUSED baseline, subject to confirming unchanged paths: [F015 revision 2 handover](../F015-runtime-task-instance-extraction/implementation.md) for unchanged wire/UI behavior; [F017 revision 3 handover](../F017-task-claim-release/implementation.md) for unchanged command/replay/transaction semantics; [F022 revision 1 handover](../F022-previous-decision-comments/implementation.md) for unchanged decision evidence; [F025 revision 1](../F025-workflow-cancellation/implementation.md) and [F026 revision 2](../F026-task-administration-reassignment/implementation.md) for unchanged mutation/persistence behavior. These sources do not establish the new read boundary. Their historical gaps remain with their owning features.

During implementation, select installed JDK 21 and root Maven wrapper; explicitly select known unit suites per module (or the existing unit-only default after inspecting discovery), then package once without repeating a successful equivalent build. Record exact selected commands/results in the handover. Since persistence queries/migrations/command transactions are not changing, no database suite is selected merely for this extraction. If a relevant query or transaction boundary changes, first revise this plan with the specific necessary test on the authorized test database.

Fresh focused HTTP/configuration tests are required for changed bean wiring and response parity. Browser checks are conditional on changed rendering/client interaction or wiring not demonstrated by those tests; no whole workflow replay just to reach a screen. No host startup against the development database is authorized by this plan. Stop when AC01–AC07 relevant checks and packaging pass; do not rerun unrelated command/replay/concurrency suites.

For this planning delivery only: content/source review, local-link existence, exactly four process documents, diff/whitespace checks; no builds, tests, services or database access.
