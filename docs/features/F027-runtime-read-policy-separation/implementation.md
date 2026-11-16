# F027 preparation and handover

**2026-11-14 — Documentation preparation only.** [Specification revision 6](spec.md) is DRAFT; [tasks](tasks.md) track execution. Runtime implementation has not started and is not authorized by this request.

## Actual work

Inspected current `WorkflowRuntimeReadHost`, sandbox read/access adapters, core query/eligibility and operation authorization contracts, runtime controller/error handling, governing feature contracts and relevant test inventory. Baseline: `d62bfda0eccab656e14e2d16253f355c0043ffce`; working tree was clean at preparation start.

Prepared four process documents and feature-index navigation. A focused independent read-only evidence review confirmed distinct paging algorithms, narrower task versus instance grants, snapshot reauthorization and read-only action-flag asymmetry. No application source, migration, configuration or historical feature evidence was changed.

The proposed design corrects two simplifications from the preceding discussion: monitor metadata authorization is separate from exact-instance policy, and not all action hints are command-activation-gated. It also explicitly preserves the implemented bounded instance-task refill despite F015's older blanket wording. These findings are requirements/proposals in the specification, not implemented changes.

## Verification

Revision 6 makes `reassignmentTargets` default to the existing 403 REASSIGNMENT_FORBIDDEN behavior, so hosts may override only `assignmentLabel`. The plan includes the default method sketch and focused label-only fixture checks; AC07/T06b and the index are aligned. Clarified that assignment labels resolve stored task targets regardless of how the reference was selected; no manual-entry flow is introduced. Documentation content/link/whitespace checks were repeated; no implementation or runtime tests were performed.

Revision 5 records removal of reassignment authorization's ownership-adapter dependency through the same host-private business-access helper planned for read-policy separation. Inspected the existing `ConsoleTaskReassignmentAuthorization` constructor/delegation to confirm the dependency. The specification and plan preserve current business decisions, independent operation grants and recovery semantics; T06a and AC06 cover the concrete wiring change. No public helper contract or runtime implementation was added. Focused documentation content/link/whitespace checks were repeated; no runtime tests, services or database access.

Revision 4 adopts the owner's My Tasks clarification: task read policy plus core current-assignee/eligibility selection replaces the ownership-business-access predicate. Ownership authorization remains only for claim/release operations, availability and recovery. The plan covers removing the sandbox read policy's indirect ownership dependency without changing ordinary-user business-access decisions or command behavior. The administrator read grant remains subject to personal eligibility in My Tasks. Specification, plan, tasks and feature index were reconciled; focused documentation checks were repeated, with no runtime changes or tests.

Revision 3 records the owner's simple administrator visibility policy: all instances/tasks/activities are readable by administrators across business domains, ordinary users retain existing visibility, and operation permissions remain separate. No domain-scoped administration model or per-task administration grant is introduced. The plan explicitly identifies the narrow change from the current OPEN-task business-access-before-administrator ordering; My Tasks remains personal eligible work. Specification, plan, tasks and index were reconciled. Documentation-only content/link/whitespace checks were repeated for this revision; no independent agent review or runtime checks were requested or performed.

Revision 2 adopts the owner's actor-only read clarification: facade/read-policy signatures and task-list flow now use `String actorRef`, with `WorkflowRuntimeReadIdentity.currentActorRef()` for shared web. Cancellation availability retains a separate optional trusted context factory for the unchanged cancellation authorization adapter. Ordinary reads require no command timestamp/correlation or factory. Tasks and index were reconciled; no runtime files changed. The earlier independent review below applies to revision 1; revision 2 received focused lead content/link/whitespace review, not another independent review.

- Source/contract inspection: completed for the planning scope; not runtime PASS evidence.
- Documentation content review: PASS, including a bounded independent review of the completed draft; no material contradictions found.
- Documentation structure/link/whitespace checks: PASS using PowerShell (exactly four process documents, all relative document links resolve, no trailing whitespace); `git diff --check` passed for tracked changes. The first link-check attempt could not launch the Windows Python alias; the PowerShell check replaced it successfully.
- Unit tests, packaging, database/integration tests and browser checks: NOT RUN (documentation-only task).
- Prior runtime results: linked as planned REUSED baseline in P05; no fresh runtime acceptance claimed.
- Processes started: none. Database access: none.

## Remaining work

Review/adopt the proposed Java integration shape and authorize implementation before executing T04–T08. Apply the selected administrator read grant while preserving ordinary-user visibility, pagination and separate operation permissions. Custom-host migration, optional-adapter behavior and core-only import isolation require explicit focused verification during implementation. Production APOLO policy and compatibility remain outside this slice.

Changes are uncommitted. No implementation completion or runtime acceptance is claimed.
