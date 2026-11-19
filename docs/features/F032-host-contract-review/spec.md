# F032 — Host contract review

Revision 4 · IMPLEMENTED · 2026-11-19 · Owner: workflow maintainer. [Constitution](../../constitution.md), C03/C07/C09. [Constraints](../../constraints.md), CON-03/08/10. [Assumptions](../../assumptions.md), ASM-05/06.

## Selected scope

All 15 workflow-core host contracts were reviewed with the owner on 2026-11-19. The approved design selects registration and discovery for items 1–3 and consolidates command authorization for items 8–11. Hosts can implement distinct business rules for each permission through one service.

| Item | Contract | Agreed decision |
| --- | --- | --- |
| 1 | `DefinitionCapabilityProvider` | Replace the provider with one `TaskTypeCapability` bean per supported task type and its outcomes. |
| 2 | `SingleTaskAssignmentResolver` | Discover SINGLE resolvers from their beans. Keep the SINGLE interface and result contract. |
| 3 | `AllTaskAssignmentResolver` | Discover ALL resolvers from their beans. Keep the separate ALL interface and roster contract. |
| 4 | `WorkflowSharedAssignmentProvider` | Retain both target-existence and current-membership checks. |
| 5 | `WorkflowServiceAction` | Retain the action descriptor and transactional execution methods. |
| 6 | `WorkflowTaskCompletionValidator` | Retain the required general check for fresh decisions. |
| 7 | `WorkflowTaskSubmissionValidator` | Retain the separate optional structured-answer check. |
| 8 | `WorkflowTaskCompletionAuthorization` | Move current completion access and fresh completion permission to the shared service. |
| 9 | `WorkflowTaskOwnershipAuthorization` | Move ownership access and administrative release permission to the shared service. |
| 10 | `WorkflowTaskReassignmentAuthorization` | Move actor decisions to the shared service. Keep destination validation in its own host contract. |
| 11 | `WorkflowCancellationAuthorization` | Move instance cancellation access and fresh cancellation permission to the shared service. |
| 12 | `WorkflowRuntimeReadAuthorization` | Retain the existing visibility and advisory availability methods. |
| 13 | `WorkflowEventBatchWriter` | Retain the ordered, same-transaction event handoff. |
| 14 | `StartLinkAction` | Retain the per-start callback for linking a newly allocated instance. |
| 15 | `WorkflowCancellationEffect` | Retain the separate optional per-cancellation business effect. |

## Requirements

- R01 — Discover SINGLE and ALL resolvers from beans implementing the existing `SingleTaskAssignmentResolver` and `AllTaskAssignmentResolver` interfaces. Each bean supplies its identifier, target kinds and resolution logic. Index identifiers separately per policy. A SINGLE-only deployment needs no ALL bean. Keep both resolver signatures and result contracts for this slice.
- R02 — Replace `DefinitionCapabilityProvider` with one existing `TaskTypeCapability` bean per supported task-type code, declaring its supported outcome keys. The logical code identifies the capability independently of the Spring bean name. Discover deployed registrations for the lifetime of the application context. A host may provide none. Preserve selectable catalog entries as a separate publication requirement.
- R03 — During validation and publication, use resolver metadata without executing assignment logic. Require the referenced resolver for the node's policy, deployed support for its target kinds, a selectable task type and support for every declared outcome. Preserve existing diagnostics for unavailable capabilities (`HOST_CAPABILITY_UNAVAILABLE`) and missing or unselectable catalog entries (`TASK_TYPE_UNAVAILABLE`). Duplicate logical registrations within a policy or duplicate task-type codes, and malformed descriptors, are host configuration errors. Never silently choose one or merge outcomes. Validate resolver and task-type metadata when the affected registry is consumed. Empty registries are valid at wiring. Duplicate or malformed metadata in a consumed registry raises `HOST_CONFIGURATION`; unrelated reads do not trigger that validation. Equivalent registrations must yield the same validation provenance as before.
- R04 — On a fresh start, check the pinned definition’s service bindings, all human task types and declared outcomes, and the presence of every referenced resolver under the correct policy, including later stages. On fresh completion, recheck service bindings and all human task types and outcomes; resolver presence is checked when entering a stage. Preserve receipt lookup before these fresh checks. Missing required capabilities at these runtime checks fail with `HOST_CONFIGURATION`. At activity entry, invoke the registered resolver for the pinned policy and identifier without consulting a provider snapshot. Missing required runtime resolver support fails with `HOST_CONFIGURATION`. Preserve existing assignment cardinality, target validation and shared membership checks. Migrate the sandbox's sample registrations and document the host Java migration. Published documents and stored evidence require no migration.

### Command authorization

**R05 — Deployment support.** A host opts in with one `WorkflowAuthorization` bean exposing `Set<WorkflowOperation> supportedOperations()` and `boolean isAllowed(WorkflowAuthorizationRequest request)`. Core captures the supported set once per application context. An absent service or an empty set is valid and enables zero covered operations. When wiring core authorization, multiple services or a null, unreadable or invalid declaration (including a null entry or START_WORKFLOW) fail wiring with `HOST_CONFIGURATION`, before any request. Business starts remain host-owned.

**R06 — Permissions and requests.** Define separate `WorkflowResource` and `WorkflowScope` enums and a `WorkflowPermission(resource, scope)` value. The following rows define the valid pairs and dispatch. Check support by exact operation before asking policy, including on explicit receipt lookup. All actor decisions receive core-constructed typed requests with `permission()`, trusted `actorRef()` and `instanceId()` derived from the verified context.

| Supported operation | Resource | Access scope before receipt lookup | Additional scope for fresh command |
| --- | --- | --- | --- |
| COMPLETE_TASK | TASK | ACCESS_COMPLETION | COMPLETE |
| CLAIM_TASK | TASK | ACCESS_OWNERSHIP | Core checks eligibility and claim state. |
| RELEASE_TASK | TASK | ACCESS_OWNERSHIP | Core checks current claimant. |
| ADMIN_RELEASE_TASK | TASK | ACCESS_OWNERSHIP | ADMIN_RELEASE |
| REASSIGN_TASK | TASK | ACCESS_REASSIGNMENT | REASSIGN |
| CANCEL_WORKFLOW | INSTANCE | ACCESS_CANCELLATION | CANCEL |

Use Java 17 sealed request variants. Task access, ADMIN_RELEASE and REASSIGN carry `TaskEligibilityContext`. COMPLETE requires `TaskCompletionContext`, including the selected outcome and comment. Both instance scopes carry `WorkflowCancellationAuthorizationContext`. Construction must reject invalid resource/scope/context combinations. Hosts may map multiple scopes to one rule while retaining distinct completion, ownership, reassignment and cancellation access decisions.

**R07 — Call timing and failures.** Unsupported direct commands and replay lookups fail with HOST_CONFIGURATION without calling policy. Supported commands retain the existing access-before-receipt order, fresh checks and rechecks against refreshed state. Denied business access conceals the resource with NOT_FOUND. Denied fresh permission retains the operation-specific forbidden error. Lookup failures propagate as AUTHORIZATION_LOOKUP_FAILED. A matching original-actor receipt skips fresh permission, validators and destination checks. Lookup-only misses also skip fresh checks. Preserve existing availability semantics where read paths currently consult operation adapters: missing operation support suppresses its hint, and a denied reassignment availability prevents picker access. For a partial host omitting COMPLETE_TASK, the read-policy completion hint can still be true for an otherwise eligible task. An attempted completion returns HOST_CONFIGURATION before consulting command policy. See [runtime read policy](../F027-runtime-read-policy-separation/spec.md) and [completion](../F028-completion-authorization-validation/spec.md).

**R08 — Reassignment destination.** A host supporting REASSIGN_TASK supplies a separate target validator with `boolean isValidTarget(TaskEligibilityContext task, TaskAssignmentTarget target)`. Core asks it about the exact proposed destination on a fresh authorized reassignment. Missing or ambiguous required target validation is HOST_CONFIGURATION when needed. Preserve current invalid-target handling and shared-target existence checks. Availability, picker authorization and receipt recovery require no destination validation.

## Acceptance scenarios

- A01 — With only a SINGLE reviewer resolver and a `SANDBOX_REVIEW` bean supporting COMPLETE, APPROVE and REJECT, a selectable review definition using supported outcomes validates and its SINGLE stage resolves. Referencing an ALL resolver before registration produces an unavailable-capability diagnostic.
- A02 — Adding an ALL resolver enables its definition and fixed roster using the same task-type bean. Matching identifiers in different policies resolve independently. Supported shared targets still require the existing shared-assignment provider.
- A03 — An empty registration set allows a host to load core. A definition referencing an absent task capability, wrong resolver policy or unsupported outcome cannot publish. Registering a task capability alone does not create or select a catalog entry.
- A04 — Duplicate or invalid resolver or task-type metadata fails as a configuration error when consumed. Validation never calls resolve. Equivalent registrations preserve provenance, and a pinned stage whose resolver is missing fails explicitly.
- A05 — A completion-only host declares `Set.of(COMPLETE_TASK)`. Completion dispatches TASK/ACCESS_COMPLETION then TASK/COMPLETE on an otherwise valid fresh decision. Current access can pass while fresh DECIDE permission fails. A matching replay asks only for current access. Cancellation availability is false and cancellation commands/lookups fail as unsupported without consulting the host policy.
- A06 — A host supporting CLAIM_TASK and RELEASE_TASK can omit ADMIN_RELEASE_TASK despite their common access scope. Check each operation independently. Invalid permission/context pairs cannot reach the host. Lookup failures remain distinguishable from denial.
- A07 — Supported reassignment checks actor permission before offering targets and validates the selected destination on execution. An unsuitable target fails even for an authorized actor. Replay skips target validation. Supported cancellation uses INSTANCE access and fresh scopes in the established order.
- A08 — The migrated sandbox preserves booking VIEW/DECIDE for completion, the linked-request rule for ownership/reassignment/cancellation access, administrator-only fresh privileges, and the existing reassignment destination set.
- A09 — Remove a later stage’s resolver after publication. A fresh start fails before linking or entering the first stage. Remove a declared task outcome after a task opens. An otherwise valid fresh completion fails before accepting the decision. Authorized matching receipt recovery skips fresh capability checks.
- A10 — An absent authorization service and an empty supported set both wire successfully. An ambiguous service or invalid declaration fails at wiring. Missing or ambiguous reassignment target validation fails only when fresh execution needs it. With COMPLETE_TASK omitted, an eligible task can still have a positive read hint while its command returns HOST_CONFIGURATION without a policy call.
