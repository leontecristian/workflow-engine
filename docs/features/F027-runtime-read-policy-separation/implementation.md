# F027 implementation and handover

**2026-11-14 — Documentation preparation only.** [Specification revision 6](spec.md) was DRAFT; [tasks](tasks.md) track execution. This historical preparation did not authorize runtime implementation; the owner subsequently approved revision 6 on 2026-11-16.

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

## Historical preparation handoff

Review/adopt the proposed Java integration shape and authorize implementation before executing T04–T08. Apply the selected administrator read grant while preserving ordinary-user visibility, pagination and separate operation permissions. Custom-host migration, optional-adapter behavior and core-only import isolation require explicit focused verification during implementation. Production APOLO policy and compatibility remain outside this slice.

The preparation made no runtime implementation or acceptance claim.

## Implementation — 2026-11-16

The owner approved revision 6 and relevant verification. Before dependent code changes, spec/plan/tasks were reconciled to READY/adopted status. Initial `git status --short` was clean; the existing F028 planning and roadmap contents remain outside this implementation.

Engineering choices: construct the concrete authorized-read facade explicitly in runtime-read web configuration, rather than adding core scanning prerequisites. Its required actor-only policy is separate from web identity; optional operation adapters resolve through providers. Neutral core `WorkflowRuntimeReadAccessException` distinguishes MONITOR/ADMINISTRATION denial, mapped by shared web to existing 403 FORBIDDEN/ADMINISTRATION_FORBIDDEN. Exact denial remains core NOT_FOUND. The optional cancellation factory is requested only after read/ACTIVE/support gates and its actor must match. Directory search defaults to 403 while label fallback stays in web.

No persistence queries, migrations, command services or frontend source are selected for change. Instance-task bounded filtering moves verbatim into the facade; the existing eligible/admin query algorithms remain distinct. F015's no-refill wording is reconciled only for instance-task bounded refill, with historical evidence retained.

Integrated implementation, focused verification and independent review are complete. Selected JDK: Corretto 21.0.13, verified by its `java -version`; compilation remains release 17. No database access or normal development host startup is selected. No temporary service process has been started.

### Focused verification

- PASS — JDK21 root wrapper: `./mvnw.cmd -B -ntp -pl workflow-core,workflow-web -am '-Dtest=AuthorizedRuntimeReadUnitTest,RuntimeInstanceQueryUnitTest,SharedAssignmentUnitTest,WorkflowRuntimeWebUnitTest,WorkflowReassignmentWebUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`: core 23/23 and web 28/28, no failures/errors/skips. The initial sandbox attempt could not open Maven's local-cache lock; the same authorized check succeeded with cache access outside the sandbox. No test scenario failed.
- PASS — after identifying missing composed personal-selection coverage and adding neutral-denial transport assertions, reran only affected suites: same command with `-Dtest=SharedAssignmentUnitTest,WorkflowRuntimeWebUnitTest`: core 6/6, web17/17. This adds one core case; final selected unique core count is 24. Retained tests cover source continuation/scan bounds, narrow task evidence, post-completion submission reauthorization, read-only completion/ownership asymmetry, directory validation and snapshot order.
- Host suites, packaging and review results follow below.

### REUSED baseline and limits

At implementation start `4b061cd0bde502c8d6602ae3e2215b925debb95a`, the linked handovers below were present. REUSED — F015 revision2 handover for unchanged wire-size truncation/pinned projection, independent activation and UI behavior; F017 revision3 for unchanged core ownership commands, receipt/transaction semantics; F022 revision1 for unchanged original decision evidence and narrow receiving-task contract; F025 revision1 and F026 revision2 for unchanged cancellation/reassignment command, replay and persistence behavior. Their specific source documents are linked in P05. Fresh isolated tests establish the changed read composition and host adapter dependencies; historical evidence does not establish these new boundaries. Historical gaps remain with their owning features.

Database/integration, frontend builds and browser: NOT RUN. No persistence, schema, query algorithm or rendering source changed; focused MVC/configuration tests demonstrate changed wiring. No broader command/replay suite is selected merely for preservation claims. APOLO policy/production compatibility remains outside scope. F028 and future assignment-resolver work are excluded.

- PASS — host test compilation included all 47 test sources, including guarded integration sources without executing them. Root wrapper `-pl workflow-test-host -am '-Dtest=ConsoleRuntimePolicyUnitTest,ConsoleRuntimeAdaptersUnitTest,ConsoleInstancesUnitTest,ConsoleSharedAssignmentUnitTest,ConsoleReassignmentUnitTest,ConsoleSequentialStagesUnitTest,ConsoleRuntimeUnitTest,ConsoleInstanceGraphUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test` selected 43 isolated cases. Initial result: 36 pass, seven graph cases fail because the mocked console administrator had no actor reference. Corrected the fixture to supply its authenticated actor; no production authorization was weakened.
- PASS — affected rerun with `-Dtest=ConsoleInstanceGraphUnitTest,ConsoleRuntimePolicyUnitTest`: 17/17. Also covered the final viewer-wrapper error projection after static migration cleanup. Combined final unique host coverage: 43/43. The selected small runtime adapter/recovery suites exercise changed `completionTask` consumers and prove denial prevents replay lookup; this is the concrete reason for those checks, not a broad command/replay gate.

Completion consumer inspection preserved the existing dispatch/recovery check through `completionTask` in the three identified callers. It checks business access before administrator permission for OPEN tasks and returns the current owner before membership lookup. General reads use the new broad administrator policy. Graph/history wrappers preserve their existing concealed errors and 422 scan-limit projection. The sandbox completion hint has no additional host veto, so core owns the existing state/claim/assignee/eligibility calculation; no completion command authorization hook was added.

- PASS — packaging: `./mvnw.cmd -B -ntp '-Dmaven.test.skip=true' package`, verified Corretto21.0.13, BUILD SUCCESS for core/web/test-host. Tests are separately reported above. No repeated build selected.

All implementation workers returned final handoffs and completed before independent review. Core/web/host checks and packaging are inspected; implementation is stable for review. Temporary Maven JVMs exited; no service process was started.

### Independent review and final handover

PASS — a fresh GPT-6.1 Sol Medium reviewer inspected the stable integrated source after all implementation agents completed and checks/packaging passed. No actionable defects found. Review covered actor-only authorization, separate operation grants, personal task selection, paging, concealed/narrow evidence reads, snapshot/submission ordering, cancellation context gating, label-only directory, optional configuration and preserved completion dispatch/recovery guards. No repeated review or broader verification selected.

PASS — final intended diff review, relative documentation links, exactly four F027 process documents, tracked `git diff --check` and newly added Java whitespace checks. Final selected unique tests: core24 + web28 + host43 = 95, all passing across the initial and affected reruns above. No outstanding F027 implementation or selected verification blocker. Custom-host Java adapters require the documented explicit migration; APOLO production policy/dependency acceptance remains separate. No claim of fresh database, browser or production acceptance is made.

Concurrent unrelated F028 documentation/index edits appeared after the initially clean status; they are preserved and excluded from this delivery/review. No roadmap or F028 implementation was undertaken. No files staged, committed or pushed. All task-owned Maven JVMs exited naturally; no temporary host/service was started and no database was accessed. Existing user processes were untouched.
