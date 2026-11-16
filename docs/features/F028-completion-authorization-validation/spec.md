# F028 — Completion authorization and business validation

**Status:** DRAFT, revision 2 — 2026-11-16. **Owner:** Workflow project owner. **Constitution:** 6.6.1. **Authorization:** specification and planning only; no implementation authorized.

## Purpose and scope

Provide an authoritative host authorization boundary and a business-validation hook for fresh task completion, inside the host's completion transaction and before accepting a decision or running downstream effects. Preserve authorized recovery of already successful commands.

In plain language: authorization asks **may this person approve?** Business validation asks **is the business record still valid to approve?** A booking changed after the reviewer opened it must be checked again when the decision is processed. If a check rejects, nothing from that attempted completion commits. A retry of an already successful decision returns its authorized stored result rather than validating the business record as a new approval.

This feature is separate from [F027](../F027-runtime-read-policy-separation/spec.md). It does not depend on F027 delivery or change its read contracts, My Tasks, directory or administrator visibility policy. Read/availability permission is not completion permission. The future semantic-actor/default-resolver proposal is also excluded.

Include core completion and lookup-only replay entry points, neutral host contracts, scoped web error mapping, sandbox policy/fixture wiring and focused transaction/recovery verification. Exclude new booking/budget rules, APOLO production implementation, new outcome guards/DSL fields, schema migrations, automatic retries, compensation, per-outcome UI permissions and broad assignment/ownership changes.

Apply [CON-01/02/03/06/07/10/12/13](../../constraints.md), [ASM-01/02/03/06/10](../../assumptions.md), [constitution C03/C06/C09/C10](../../constitution.md), [design sources](../../design-sources.md), [F017](../F017-task-claim-release/spec.md), [F018](../F018-structured-task-submissions/spec.md) and [F021](../F021-all-collective-review/spec.md). The [booking baseline requirement](../../integrations/apolo/booking-business-analysis.md#approval-baseline-and-protected-material) remains host-owned. Budget rules and exact APOLO access mappings are not inferred.

## Current evidence

At baseline `50e356395fae2822ea38493ee255c6e8ef9da7c3`, `WorkflowExecutionService.completeTask` resolves receipt replay before fresh state/version/eligibility checks. Its completion receipt helper verifies actor/fingerprint and target/result consistency but has no core completion business-access adapter. The sandbox web command adapter supplies its own recovery access policy; direct-core consumers retain host authorization obligations.

`WorkflowTaskSubmissionValidator` is optional, runs for OPTIONAL/REQUIRED result contracts (including omitted OPTIONAL results), and is skipped for FORBIDDEN. It is not a universal approval permission or business-validation hook. This feature preserves that contract rather than silently broadening it.

## Requirements

### R01 — Separate current access and fresh permission

Introduce a host completion-authorization contract with separate `canAccess` and `canComplete` decisions. `canAccess` checks current business access to the exact target task/instance for both mutation requests and receipt lookup; missing/denied exact targets are concealed as NOT_FOUND. It must not require OPEN/ACTIVE state, current assignment/membership, current approval privilege, an unchanged approval baseline or general instance-read permission. Those would prevent recovery of a valid previous completion.

`canComplete` checks authority for the selected outcome on a fresh completion only. Core still owns workflow state, assignment/current membership, claimant exclusivity, version, outcome/comment and result validation. No administrator bypass follows from being able to read everything. Host policies may share internal business-access helpers but do not depend on ownership, reassignment or cancellation authorization adapters.

### R02 — Authorized replay before fresh checks

Validate command shape and trusted context, resolve and verify exact task/instance association, and require current business access before exposing any receipt result or lookup miss. Then use the existing receipt actor, operation, fingerprint/version and target-consistency logic.

A matching successful receipt returns the original stored result without fresh completion permission, business validation, membership lookup, current-state/version eligibility, submission validation, downstream effects or new history. This holds after closure, later workflow advancement, reassignment, membership/privilege loss or changed business material, provided current business access and original receipt identity remain valid. Another actor cannot retrieve that success merely by having administrator/read access. Retain existing receipt mismatch/conflict behavior and fingerprint formats.

Both `completeTask` and `findReplay(CompleteWorkflowTask, ...)` use the same current-access boundary. Lookup-only replay never executes completion or fresh hooks on a miss. Do not add UI/exact-read prechecks that impose narrower visibility than this operation's access policy. After stale/concurrent failure, complete rollback before authorized lookup in a fresh transaction; never rerun mutation automatically.

### R03 — Fresh completion checks before acceptance

On a receipt miss, preserve the early aggregate version-checked write and refreshed task/activity checks. Re-evaluate current business access on the refreshed task before fresh permission and validation. Preserve existing assignment, ownership, declared outcome/comment and structural result checks. The new fresh permission decision and business validator run before `task.complete`, accepted submission/history, named output writes, sibling cancellation, progression, service effects, receipts and durable recorder calls.

Business validation runs for every fresh completion that reaches that boundary, including FORBIDDEN results, OPTIONAL omission, REQUIRED results, nonfinal ALL contributions and resolving outcomes. A rejected or failed check aborts the transaction, including any provisional aggregate version/history reservation. Earlier committed contributions remain intact. The new checks are not skipped because the task has no JSON result.

Retain the existing F018 submission validator and its result-mode behavior. Keep a deterministic sequence: structural result checks, fresh authorization, new general business validation, then the existing optional result-specific validator before acceptance. Host rules must not be duplicated in both validators. Successful replay runs neither validator.

### R04 — Host transaction and business-data consistency

Invoke host checks in the same host-owned transaction as completion; no separate validation transaction, cached UI decision, or post-commit check. Validation is read-only with respect to business effects: no notifications, external delivery, approval mutation or irreversible action inside the hook. Required effects remain in the established transactional orchestration/service paths.

Being inside a transaction alone does not protect business data from concurrent edits. The host must use its supported locking/version checks/protected-write guard to keep the validated approval basis valid through acceptance and commit. Core aggregate locking does not lock APOLO booking/budget records. Define consistent host/engine lock ordering during integration; contention fails through established conflict behavior without automatic retries.

The hook supports, but does not by itself implement, APOLO's preserved booking basis, protected-material/membership checks, no-self-approval and finalization requirements. Sequential steps must not recapture the baseline. Business finalization checks remain in the host's finalization path; this feature adds no generic finalization callback. Budget scope/amount rules and protected fields remain separate business specifications. Generic sandbox evidence must not be reported as APOLO acceptance.

### R05 — Neutral inputs and safe failure behavior

Callbacks receive workflow-owned detached context: trusted actor/command context, verified task/instance linkage, and, for fresh evaluation, pinned node/type/version and requested outcome/comment. The business validator additionally receives the existing immutable result value, nullable under the existing result contract. Do not pass entities, arbitrary application objects, every variable, or client-supplied identity. Hosts resolve authoritative business associations by instance ID; correlation fields are not proof of access.

Proposed errors for this feature: access denial/missing target retains NOT_FOUND (404); fresh permission denial uses COMPLETION_FORBIDDEN (403); explicit business rejection uses BUSINESS_VALIDATION_REJECTED (422); unexpected authorization failures retain AUTHORIZATION_LOOKUP_FAILED (503); unexpected general validation failures use BUSINESS_VALIDATION_FAILED (503); missing/ambiguous required adapters use HOST_CONFIGURATION (500). Existing structural, submission-validation, receipt and concurrency errors remain. Public responses use stable generic messages without arbitrary host exception text, business material or baseline contents. No new free-text diagnostic contract is proposed.

### R06 — Explicit activation and migration proposal

**AGREED deployment rule — owner decision, 2026-11-16:** hosts using completion must provide exactly one `WorkflowTaskCompletionAuthorization` and exactly one `WorkflowTaskCompletionValidator`. There is no optional activation mode or implicit allow/no-op fallback. A host with no additional business validation still registers an explicit validator that returns normally, owning that decision. Enforce authorization adapter presence for completion mutation and lookup entry points; enforce validator presence before fresh completion can be accepted. The requirement to provide both adapters does not make fresh validation part of replay: authorized receipt hits and lookup-only misses do not resolve or invoke the fresh validator. Core import, read-only consumers and other commands must not acquire mandatory bean dependencies just by including the library.

This is an intentional completion integration change, not a reinterpretation of published definition JSON. Migrate host policy/configuration together with core/web; retain receipt schemas/fingerprints and published definitions. Older receipts remain readable under current business access and original-actor rules. A host adopting this version must explicitly provide both adapters for existing supported workflows. Missing policy fails closed rather than silently weakening checks. D01 is closed by the owner decision below.

Web completion payloads/success responses and command recovery routes remain unchanged. UI `canCompleteTask` remains advisory; a displayed action may later be denied. It must not invoke the transaction-sensitive business validator during a GET, and this feature adds no F027 dependency. Definition-declared authorization guards are not implemented or expanded by this feature; reconcile older documentation that describes a guard contract with actual supported behavior during implementation documentation work.

## Acceptance scenarios

- **AC01 — Fresh authorization:** an otherwise eligible actor denied completion authority cannot commit a decision; current business-access denial conceals both fresh requests and receipt lookup. Read/admin grants alone cannot override operation policy.
- **AC02 — All result modes:** the general validator runs before acceptance for FORBIDDEN, omitted OPTIONAL and REQUIRED results; explicit rejection and unexpected failure roll back. Existing F018 mode semantics are retained.
- **AC03 — Authorized replay:** original actor with current business access recovers the original success after permission/membership loss, business-material change or terminal state. Fresh hooks and effects are not invoked. Access loss, different actor and changed payload remain denied/conflicted under their existing boundaries. Lookup miss performs no mutation.
- **AC04 — Atomicity:** validator rejection leaves task/activity/instance version, newly attempted history, outputs, receipts and handoff unchanged after rollback; prior accepted ALL answers remain. A successful nonfinal ALL contribution and resolving completion each pass the hook once for that fresh attempt.
- **AC05 — Business edit race:** a focused test-owned host fixture demonstrates that a material edit cannot commit between successful basis validation and approval acceptance under the selected host locking/version arrangement. This establishes the fixture boundary only, not APOLO production protection.
- **AC06 — Integration:** completion hosts provide both adapters; missing/ambiguous authorization fails closed for mutation and lookup, and missing/ambiguous validation fails closed for fresh acceptance. Direct-core and shared-web completion/recovery use the same policy. Missing fresh validator cannot block an authorized receipt hit; lookup-only misses do not invoke it. Read-only hosts require neither adapter. Scoped HTTP error projection, lookup-only recovery and transaction cleanup remain correct.

## Decisions and exclusions

**AGREED scope:** specify authoritative fresh authorization and transaction-time business validation separately from F027; preserve authorized replay and current core/host ownership. No implementation authorized.

**D01 — CLOSED / AGREED, 2026-11-16:** the owner requires both completion authorization and general business-validation adapters for hosts using completion, including existing published workflows when adopting this boundary. No opt-in compatibility alternative or permissive fallback is selected. Preserve R06's distinction between required host registration and fresh-only validator invocation; successful authorized replay remains independent of fresh validation. This closes the deployment-policy decision and does not authorize implementation.

**PROPOSED engineering contracts:** names/signatures and new error categories in this revision. **DEFERRED:** exact APOLO permissions, baseline storage/guards and budget rules, semantic actor/default resolver design, new outcome guard syntax and UI permission redesign. No APOLO business rule is invented to close those gaps.
