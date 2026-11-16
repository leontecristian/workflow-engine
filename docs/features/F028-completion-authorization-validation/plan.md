# F028 implementation plan

Targets [specification revision 3](spec.md). **Approved engineering plan, 2026-11-16.** D01 is CLOSED / AGREED: completion hosts must provide both adapters. Implementation and focused verification are authorized by the owner. [Tasks](tasks.md) track execution; [implementation.md](implementation.md) records actual results.

## P01 — Contracts and integration decision

Keep core contracts in `io.github.leontecristian.workflow.core.service.runtime`, neutral DTOs in `core.dto.runtime`, failures in `core.exception.runtime`. Preserve layer-first packages and the root `WorkflowConfiguration`; no new module or migration.

Finalized shape:

```java
public interface WorkflowTaskCompletionAuthorization {
    boolean canAccess(TaskEligibilityContext context);
    boolean canComplete(TaskCompletionContext context);
}
public interface WorkflowTaskCompletionValidator {
    void validate(TaskCompletionContext context, WorkflowJsonValue result);
}
```

Use existing `TaskEligibilityContext` for current access: exact task plus trusted actor reference, not a requirement for current eligibility. `TaskCompletionContext` contains `WorkflowCommandContext commandContext`, `WorkflowTaskView task`, `long definitionVersionId`, `String nodeKey`, `String taskType`, `String outcomeKey`, `String comment`. Construct it only from refreshed verified entities and the structurally accepted command; never require pinned-definition loading or fresh-state eligibility to authorize a receipt hit. The result argument retains existing immutable value semantics. The no-argument TaskCompletionRejectedException signals sanitized business rejection, distinct from unexpected provider failure; do not expose arbitrary adapter exception messages. SQL locking/version contention preserves existing conflict categories.

Apply agreed D01 and the finalized error/API contracts. Use the existing lazy provider pattern to enforce exactly one authorization adapter at completion mutation/lookup boundaries and exactly one general validator on the fresh path before acceptance. Document both as mandatory for hosts offering completion; do not add optional activation or permissive defaults. Do not eagerly resolve the validator for receipt hits or lookup-only misses. Direct-core callers cannot bypass authorization by avoiding shared web; ordinary core import/read-only usage does not require either adapter. Authorization and validation are host extension interfaces; no parallel implementation interface is needed for the core execution service.

## P02 — Completion and replay ordering

Modify `WorkflowExecutionService.completeTask`, `findReplay(CompleteWorkflowTask, ...)` and their completion access/replay helpers only as necessary:

1. Preserve basic input/context validation. Resolve task and owning activity/instance identity without applying current-state, assignment or general read grants; reject inconsistent target association and apply current `canAccess` before receipt disclosure.
2. Reuse `RuntimeJson` receipt verification and original result consistency. Preserve fingerprint versions and authorized original-actor rules; no fresh hooks on a hit or lookup-only miss.
3. On mutation miss, preserve early aggregate version claim and refreshed task/activity checks. Recheck business access for the refreshed task. Keep engine eligibility, claimed-owner, pinned outcome/comment and time validation.
4. Refactor structural result validation from the optional F018 host validator invocation so R03's sequence is explicit. Run fresh `canComplete`, general validator and existing result-specific validator before task acceptance. Both validators stay inside the transaction, with F018 retaining its result-mode conditions.
5. Preserve completion/ALL/output/progression/history/receipt/recorder logic. Exceptions must escape to roll back the whole attempt, including the provisional aggregate write; never recover in the failed transaction.

Retain raw core read/query behavior. This is not an extraction of all execution authorization or a new retry framework. If a raced command requires recovery, existing host coordination rolls back then performs authorized lookup in a new transaction.

## P03 — Host/web composition

In `io.github.leontecristian.workflow.testhost.service.runtime`, add explicit sandbox completion authorization and a general completion-validator fixture, reusing host-private business-access checks without depending on ownership/reassignment adapters. Use controlled test-owned data for rejection and concurrency cases; do not introduce booking/budget domain modules into the sandbox.

The owner selected a concrete local test-host permission fixture using host-owned `Resource.BOOKING`, `Scope.VIEW` and `Scope.DECIDE` constants. Keep these types and fixture implementation in `testhost.service.runtime`, outside core/web public contracts. Map completion `canAccess` to VIEW and fresh `canComplete` to DECIDE through one small host-private permission evaluator. The fixture uses existing sandbox request/instance associations as trusted authorization context; it is not a production booking model. Represent role-to-permission grants and actor-to-contextual-role membership as bounded test-owned fixture data, with at least two contexts so a role in one does not grant access in another. Do not create per-object ACL tables, a generic IAM framework, new management endpoints or schema migrations.

This fixture demonstrates the non-normative Keycloak example without a Keycloak server, SDK, realm/client setup, token implementation or external authorization call. Core tests use adapter doubles; host tests exercise the local resource/scope evaluator and contextual-role selection. Names and policies are fixture-specific; production hosts can choose another authorization provider or mapping. Keep the general business validator separate from permission evaluation. The subsequent owner request authorizes this local fixture.

Update `ConsoleRuntimeCommandHost`, `ConsoleRuntimeCoordinator` and `ConsoleRuntimeTransaction` so completion and replay rely on the new authoritative access boundary rather than an exact-read precheck that can reject a valid recovery after membership/visibility change. Keep required trusted context construction, returned-after-commit behavior and fresh-transaction post-rollback lookup. Inspect all direct-core callers/tests for new adapter wiring. Preserve implemented F027 read behavior and adapt its current completionTask helper.

In `io.github.leontecristian.workflow.web.service.runtime.RuntimeErrors`, map R05's scoped errors; keep payloads and success wrappers. Add no controller-global advice or competing transaction/security infrastructure. Maintain optional read/command activation. No frontend implementation is planned; existing generic error display handles sanitized denials. Document that advisory availability may differ from final transaction-time evaluation.

Host integration documentation must distinguish permissions from material validity and explain how business locks/guards survive until commit. The hook cannot certify remote directory consistency or prevent edits by itself. For the selected SQL fixture, use one explicit transaction/lock ordering and demonstrate it; APOLO must specify its own protected fields/associations and enforcement before production acceptance. Retain separate finalization responsibility.

## P04 — Compatibility and documentation

Document D01's mandatory registration of both adapters for existing completion callers and the new sanitized failure codes. An explicit accept-without-extra-checks validator is a host decision, never a library fallback. Preserve command/receipt representation, no schema changes and no published-definition rewriting. Update owning completion/submission integration documentation narrowly: F018's validator remains optional and mode-dependent, while this feature provides the separately selected required general boundary. Reconcile CON-10/source-precedence descriptions only for adopted behavior; no invented guard DSL or silent blanket policy change. Keep historical verification intact. F027 and the future actor proposal remain independent.

## P05 — Focused verification

Changed paths include completion authorization, receipt access, fresh validation and transaction rollback; fresh checks are justified for these boundaries, not all engine commands.

- Unit: inspect/select `RuntimeCommandsUnitTest`, `RuntimeReceiptUnitTest`, relevant `SharedAssignmentUnitTest`, submission/ALL tests and host `ConsoleRuntimeUnitTest`/`ConsoleSharedAssignmentUnitTest`. Add focused callback-order and missing-adapter cases, result modes, permission versus business rejection, failure mapping, replay after changed permissions/material, and no fresh hook on lookup-only miss. Use doubles; unit success does not establish database atomicity.
- Host permission fixture: VIEW without DECIDE permits access but rejects a fresh decision; VIEW plus DECIDE permits the authorization stages but never bypasses core eligibility/business validation; a contextual role applies only in its matching trusted context. After successful completion, losing DECIDE while retaining VIEW still permits original-actor replay; losing VIEW denies recovery. Permission-evaluator failure fails closed. These are local resource/scope contract checks, not Keycloak integration evidence; no per-object ACL is needed.
- Web: focused `WorkflowRuntimeWebUnitTest` cases for new error mapping and shared/direct completion parity; verify recovery does not require current exact-read visibility or fresh approval permission.
- Guarded SQL integration: select/extend the relevant completion cases in host suites, or add a focused `CompletionAuthorizationIT` using `DatabaseIntegrationTest`. On `localhost:1433/apolo_workflow_test` only, demonstrate rollback of rejected fresh completion including early version write, accepted receipt replay without validator invocation, nonfinal ALL preservation, and one deterministic protected-business-edit race using test-owned fixtures. Coordinate runs targeting the same database and clean only test-owned fixtures. No development database/normal host startup.
- Build: installed JDK 21, root Maven wrapper, explicit unit selection, then focused `-Pintegration-tests verify` selection. Record exact commands; avoid a second packaging command if the successful selected verify already packaged equivalent scope. Do not weaken tests or change profile discovery.

REUSED baseline, only for unchanged paths: [F017 revision 3 handover](../F017-task-claim-release/implementation.md) for ownership/membership semantics; [F018 revision 3](../F018-structured-task-submissions/implementation.md) for existing result schema and exact result encoding; [F021 revision 2](../F021-all-collective-review/implementation.md) for unchanged roster/output mechanics. Fresh checks above cover the changed acceptance/replay boundary; prior results cannot establish new hook behavior. Reuse historical evidence with applicability notes in the handover; retain earlier gaps with their owners.

Completion condition: AC01–AC06 pass at the selected boundaries, focused package/integration wiring passes, errors and migration instructions match, and the handover states APOLO limits. No broad browser walkthrough unless changed client behavior exposes a concrete gap; no new unrelated cancellation/reassignment/expression suites. Stop after the relevant checks pass. Record/stop any temporary process created for verification.

Delivery includes focused unit, guarded SQL and packaging checks above, followed by independent review. No development database or normal host startup is authorized.
