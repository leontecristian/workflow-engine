# F031 — Shared runtime command coordination

Revision 5 · READY · 2026-11-19 · Owner: workflow maintainer. Design approved by the owner on 2026-11-19 under [constitution 6.6.1](../../constitution.md).

## Purpose and scope

`WorkflowRuntimeCommandHost` requires hosts to forward six commands and their six receipt lookups. Extend the responsibility split established by [F029](../F029-administration-host-simplification/spec.md) to the existing shared endpoints for completion, claim, release, administrative release, reassignment and cancellation. A host should be able to enable these endpoints using its identity, policy and infrastructure adapters. The proposal makes host audit recording optional for administration writes and consolidates request identity across the instance viewer, runtime reads, runtime commands and definition/catalog administration. It revises the identity portions of [F013](../F013-shared-instance-viewer/spec.md), [F027](../F027-runtime-read-policy-separation/spec.md) and F029.

The [current web contract](../../../workflow-web/README.md) governs routes, input bounds, expected-version tokens, success and failure envelopes, and explicit replay responses. Business starts, start replay, request linking and host endpoints coordinating a larger business transaction stay host-owned. See [CON-01/03/06/07/10](../../constraints.md), [ASM-01/02](../../assumptions.md) and the [design sources](../../design-sources.md).

## Required and optional workflow-web interfaces by endpoint group

| Endpoint group | Required host interfaces | Optional host interfaces |
| --- | --- | --- |
| Instance viewer | `WorkflowRequestIdentity`, `WorkflowViewerAccess` | None |
| Runtime reads | `WorkflowRequestIdentity` | `WorkflowAssignmentDirectory` for assignment labels and reassignment picker search |
| Runtime commands | `WorkflowRequestIdentity` | None |
| Definition and catalog administration | `WorkflowRequestIdentity`, `WorkflowAdministrationAuthorization` | `WorkflowAdministrationAuditRecorder` for recording changes in the mutation transaction |

A host enabling all four groups supplies three distinct required web interfaces. One identity bean serves every enabled group. Viewer checks receive the captured actor. Core supplies the policy contracts for runtime operations and validates a selected reassignment target at execution. The host clock is optional for runtime reads and required for shared commands. The cancellation availability hint uses the captured identity and host clock as specified in R07.

## Approved requirements

The host supplies one bean implementing this contract in `io.github.leontecristian.workflow.web.integration`:

```java
public interface WorkflowRequestIdentity {
    String requireActorRef();
}
```

- **R01a — Shared identity.** The instance viewer, runtime reads, runtime commands and administration can each activate independently and consume the same identity bean. Resolve the stable actor reference from trusted server authentication once per request that needs identity. Pass the captured reference through subsequent checks and recovery; never cache it across requests. An absent or anonymous principal yields `401 UNAUTHENTICATED`. All four groups recognize the same unauthenticated signal and render their existing scoped error envelopes. A null or blank adapter result yields `500 HOST_FAILURE` before policy, core or audit calls.
- **R01b — Separate authorization.** Administration continues to call `WorkflowAdministrationAuthorization.requireAdministrationAccess(actorRef)` before parsing. Runtime reads use their existing read policies. Commands and receipt lookups use the existing operation-specific core policies. Each access recheck evaluates current policy for the captured actor. Identity grants no permission.
- **R01c — Viewer authorization.** The viewer resolves `actorRef` through `WorkflowRequestIdentity` once per request and passes it as the first parameter to every `WorkflowViewerAccess` check listed below. Host implementations authorize the supplied actor. The default event check forwards the same actor to the history check, and the default restricted-summary check forwards it to the exact-task link check. Preserve the existing instance and history grants, per-event checks, summary filtering and distinct activity and exact-task link grants.

  ```java
  void requireInstanceRead(String actorRef, long instanceId);
  void requireHistoryRead(String actorRef, long instanceId);
  void requireHistoryEventRead(String actorRef, WorkflowHistorySummary event);
  boolean canReadHistorySummary(String actorRef, WorkflowHistorySummary event);
  Set<ViewerLinkReference> readableLinks(String actorRef, long instanceId, Set<ViewerLinkReference> candidates);
  ```

- **R01 — Activation and trusted context.** Replace the forwarding interface with a concrete internal web coordinator. Command activation uses `WorkflowRequestIdentity.requireActorRef()`, host `Clock`, `WorkflowRuntimeOptions`, managed `WorkflowExecutionService` and host transaction infrastructure. Build the user command context once from the authenticated actor, host time at the existing 100-nanosecond precision and correlation from the configured trusted request attribute. Preserve the existing correlation semantics, including null for an absent or non-string value. Hosts own authentication, CSRF and endpoint activation. Either runtime group can be activated independently.
- **R02 — Execution and policy.** Delegate each typed command to its existing core operation. Core retains receipt matching, current business access, fresh-command permission, validation, mutation and durable handoff. Reuse the existing completion, ownership, reassignment and cancellation authorization interfaces, shared assignment provider and applicable validators. Preserve current missing-policy failures and optional operation support. Completion goes directly to core so an authorized duplicate can recover its receipt after the task closes. A preliminary task read or fresh-permission check would break that behavior. The sandbox's post-completion submission-visibility assertion belongs in focused integration verification.
- **R03 — Transaction and core conflict recovery.** The shared HTTP coordinator observes the actual outcome of the command transaction before reporting success or attempting recovery. After rollback for core `STALE_VERSION` or `CONCURRENT_COMMAND`, perform one read-only receipt lookup in a fresh transaction with the original typed command, expected token and trusted actor context. Core rechecks access. A hit returns the stored result as replayed. A miss propagates the original failure. An error from the lookup, including access denial or command-ID conflict, propagates on its own terms. Execute the mutation at most once per request. Other errors, including `COMMAND_ID_CONFLICT`, follow ordinary error mapping. An HTTP status alone never establishes eligibility. Explicit replay always performs lookup only and retains its existing miss response. Business callers managing an outer transaction retain ownership of its completion and recovery.
- **R04 — Sandbox HTTP 409.** A conditional update to the sandbox request can fail during a workflow action. After rollback, the sandbox maps a direct or wrapped `SandboxRequestConflict` to HTTP 409 with the existing `SANDBOX_REQUEST_CONFLICT` code, sanitized message, diagnostics and trusted correlation in the runtime failure envelope. On this path the proposed behavior is an immediate 409, even where the old sandbox coordinator would first attempt a receipt lookup. The client can explicitly request replay. The sandbox preserves the cause across the boundary and owns exception recognition and rendering. Shared web neither inspects the sandbox exception nor reclassifies it as a core conflict. Other runtime failures continue through their existing scoped mapping.
- **R05 — Migration.** Retire `WorkflowRuntimeReadIdentity`, `WorkflowAdministrationIdentity`, `WorkflowRuntimeCommandHost` and `WorkflowCancellationAvailabilityContextHost`. Hosts enabling any of the four groups migrate with the web library and register one `WorkflowRequestIdentity` bean. A viewer-only host supplies the shared identity bean and its authorization-only `WorkflowViewerAccess` adapter with the revised signatures. It can activate the viewer independently. Core-only hosts are outside this migration. Retain existing core contracts, schema, HTTP/UI contracts and sandbox business-start recovery. No completion callback or recovery marker is introduced.

- **R06 — Optional administration audit.** Administration can activate and commit authorized definition and task-type changes without a `WorkflowAdministrationAuditRecorder` bean. Hosts opt in by supplying the existing recorder. For those hosts, a changed mutation calls `record` once with the existing F029 evidence, in the mutation transaction. An unchecked recording failure rolls back the mutation or marks a joined transaction rollback-only. Reads and unchanged results produce no record. This supersedes F029’s mandatory recorder dependency while preserving its identity, authorization and trusted correlation checks as revised above. Runtime execution history and durable event handoff are outside this option.

Durable administration audit recording provided by `workflow-web` is a [deferred roadmap item](../../roadmap.md#7-deferred-scope-and-open-decisions). R06 covers only the optional host recorder.

### R07 — Cancellation availability on runtime reads

For the cancellation-availability GET, first resolve the actor through `WorkflowRequestIdentity` and authorize the instance read. A denied read returns its existing error. After a granted read, return `{allowed: false, lockVersion}` with the instance lock version if the instance is inactive, the host supplies no `Clock`, or cancellation authorization is absent. These paths skip the clock read and cancellation policy evaluation. Runtime reads can activate alone without a clock; ordinary reads never build a cancellation context.

For an active instance with both dependencies, shared web builds a `WorkflowCommandContext` using `ActorKind.USER`, the actor already authorized for this read, one host clock reading truncated to 100-nanosecond precision, and null correlation. Pass it to the existing core availability check. Core evaluates current business access and cancellation permission, retaining its existing failure handling. Return the result with the instance lock version. The hint is advisory. Actual cancellation requires its own authorization and execution checks.

**Migration effect.** Under F027 a host could omit the context factory to suppress the hint. Under F031 a host that already supplies a clock and cancellation policy will receive the policy result for an active, readable instance, even if it previously omitted the factory. This applies with command endpoints disabled as well. The clock remains required for shared command activation.

### Assignment directory — confirmed host contract

`WorkflowAssignmentDirectory` stays optional in `workflow-web`. A host can supply `assignmentLabel` for display labels and `reassignmentTargets` for the reassignment picker's bounded search. Without a directory, labels fall back to the stored assignment reference. With either no directory or a labels-only adapter, picker search defaults to `403 REASSIGNMENT_FORBIDDEN`. See the [F027 directory contract](../F027-runtime-read-policy-separation/spec.md).

Picker results are advisory. When the reassignment command executes, core validates the selected target for the exact task through the host's `WorkflowTaskReassignmentAuthorization.isValidTarget` policy, even if the picker returned that target moments earlier. See the [F026 reassignment contract](../F026-task-administration-reassignment/spec.md).

## Acceptance scenarios

1. **A01 (R01–R02, R05).** A command-only host using the proposed identity contract and existing command dependencies can execute the six operations it supports and request their corresponding replays. A read-only host requires no command coordinator. A disabled or unauthorized operation retains its established failure. A completion retry after closure reaches core's receipt check with the authenticated actor.
2. **A02 (R03).** A core conflict rolls back before the fresh lookup starts. A matching receipt returns the recorded result. A miss returns the original command error. Denied access or a mismatched command ID during lookup yields the corresponding error. Each path executes the mutation once. Explicit replay of an unknown command executes it zero times.
3. **A03 (R04).** On a shared command route, a sandbox status update that loses its conditional write rolls back and returns the sandbox 409, including when the action failure wraps the cause. There is no automatic receipt lookup. A subsequent explicit replay gets the ordinary authorized hit or miss. An unrelated core error still uses the shared runtime mapper.
4. **A04 (R01a–R01b, R05).** Each group can activate alone with the identity bean and its own dependencies. With all four enabled, one bean serves successive requests from different users and resolves the correct actor for each. A reviewer can complete an authorized task and receive the existing 403 for administration. A runtime recheck uses the same actor and reevaluates current access.
5. **A05 (R01a).** Each group returns 401 for an unauthenticated caller and 500 for a null or blank actor returned by the adapter. Each failure preserves its scoped envelope and reaches neither policy, core nor audit.
6. **A06 (R01a, R01c, R05).** On history, exact-event and graph requests, every applicable viewer check receives the actor captured for that request, including calls made by default methods. An actor granted instance and history access but denied an exact task sees restricted ownership summaries filtered out, receives the existing concealed 404 for protected exact evidence and sees only granted links in the graph.

7. **A07 (R06).** With the recorder omitted, the administration group starts and an authorized change commits. With a recorder present, the same change produces the expected audit record, a no-op produces none, and recorder failure prevents commit. Authorization applies in both configurations.

8. **A08 (R05, R07).** A read-only host can start without a clock and receives a false hint for a readable instance. An inactive instance or absent policy also gives a false hint. A denied instance read retains its error. With an active readable instance, a fixed clock and configured policy, the policy receives the captured actor, expected time and null correlation. Its grant or denial determines the hint without executing a command.

Implementation awaits separate authorization.
