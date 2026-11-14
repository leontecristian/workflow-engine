# F024 — Start workflow by family

**Status:** IMPLEMENTED. **Revision:** 1 — 2026-11-14. **Owner:** Workflow project owner. **Constitution:** [v6.6.1](../../constitution.md). The selected API/core slice is implemented; scoped verification is recorded in the implementation handover.

## Outcome and scope

Hosts can start using a stable workflow family key or family ID without first choosing a version. Core selects the highest-numbered PUBLISHED version and pins the instance to its exact ID. Explicit version selection remains supported. This is separate from [F023](../F023-create-next-definition-version/spec.md): draft creation/deletion and version allocation do not determine runtime eligibility.

The owner-agreed direction is the three exclusive selectors, server-side highest-published selection, clear failure without a publication, coexistence of published versions, immutable instance pins and receipt-first replay. The contract details below implement that direction within existing host ownership. Scope includes core commands/replay and the sandbox start/replay API. The sandbox UI retains its existing explicit-version choice; a family-selector UI, shared start controller, APOLO integration, latest-version preview endpoint, automatic retirement, new publication policy and instance migration are excluded.

Apply [CON-03/06/07/08/10/12](../../constraints.md) and [ASM-02/03/05](../../assumptions.md): host policy and transactions, authorized replay, lifecycle serialization and executable capability support. Provider behavior needs focused verification; APOLO assumptions remain unverified. Sources: [F005 start/replay](../F005-minimal-single-workflow-execution/spec.md), [F009 current codecs](../F009-json-execution-variables/spec.md), [F015 host boundaries](../F015-runtime-task-instance-extraction/spec.md), [database family/version contract](../../architecture/Workflow_Engine_Database_Design_v1.md), [language identity rules](../../architecture/workflow-definition-language-design.md) and [design precedence](../../design-sources.md).

## Required behavior

### R01 — Exactly one selector

The public start command and sandbox start/replay bodies accept exactly one of `workflowKey`, `definitionId` (family ID), or `definitionVersionId`. No priority or implicit default applies. IDs are positive Java longs; HTTP IDs retain the existing canonical decimal-string encoding. Keys obey the existing case-sensitive `[A-Za-z][A-Za-z0-9._-]{0,99}` contract, without trimming or case folding. Empty/blank keys, null selector fields in HTTP, missing selectors, multiple selectors (even equivalent ones), malformed IDs and unknown HTTP fields fail `INVALID_INPUT` (HTTP 400). Omitted selector fields are absent, not zero.

Retain existing exact-version Java construction and HTTP payloads. Business correlation, command ID, raw start inputs, host intent, trusted actor and successful result shape retain their existing contracts. Prefer `workflowKey` in host integration documentation because database IDs are local; `definitionId` is available to consumers that already hold family identity.

### R02 — Resolve once, then pin

After a successful-receipt miss, resolve the selected family and select the greatest numeric `version` among its PUBLISHED rows. Do not order by row ID, creation/publication time, F023 allocation counter or draft revision. Ignore DRAFT and RETIRED rows. Exact-version selection requires that exact row to be PUBLISHED and never substitutes another version.

Persist the selected exact version ID on the instance; existing result/history identity continues to report that pin. Validate inputs and executable capabilities against that selected version. Failure of its input contract, capabilities or execution fails the command; do not fall back to an older publication. Publishing another version does not retire any existing publication. Retirement excludes a version from future selection but never repins or prevents continued loading of an existing instance's retained version.

An authorized fresh request for an unknown family key/ID or unknown exact version fails `NOT_FOUND` (HTTP 404). A known family with no PUBLISHED version fails the distinct sanitized category `NO_PUBLISHED_VERSION` (HTTP 409). An existing non-PUBLISHED exact version retains `INVALID_STATE` (HTTP 409). No instance, host link, receipt, history or handoff commits on these failures. Host authorization may conceal resources according to its existing policy.

### R03 — Publication/retirement and start have one ordering

Fresh starts acquire the same family write lock used by publication and retirement before choosing a candidate, then lock/refresh the selected version. Hold locks until the surrounding host transaction completes. All paths retain family-before-version ordering. Select using current committed lifecycle state after lock acquisition, including when the persistence context previously loaded stale entities.

If publication/retirement commits before start obtains that family lock, start observes its result. If start obtains the lock first, it may commit its selected version before the lifecycle command proceeds. A rolled-back publication is not eligible. Retirement of the highest publication allows a later start to select the next-highest remaining publication, or fail R02 if none remains. Lock waits/deadlocks fail as sanitized concurrency conflicts; never silently retry business execution or choose a different version after partial effects.

### R04 — Replay preserves the original selection

Fingerprint the original selector kind and value, alongside the existing actor, operation, business correlation (`businessType`/`businessId`), host intent and raw inputs. Attempt metadata (`occurredAt` and `correlationId`) remains excluded, so recovery can use a new request correlation. Never fingerprint a family request by looking up its current selected version. Key and family-ID forms are distinct commands even when they denote the same family; changing form/value under an existing command ID fails `COMMAND_ID_CONFLICT`.

An identical authorized retry returns the original stored successful result before family lookup, publication selection, current input-contract/capability validation, link creation or business execution. This holds after newer publication, retirement of the original pin, and changed current eligibility. Existing exact-version receipt fingerprints and readers remain supported. Lookup-only replay performs no resolution or mutation; a valid unmatched request returns the existing not-found receipt response even when its family has no publication.

Simultaneous duplicate requests retain one committed receipt/instance/effect set. The losing transaction rolls back; the host may perform existing lookup-only recovery in a fresh transaction. A request without a committed success may be explicitly retried and resolve a newer publication; failed attempts do not reserve a pin.

### R05 — Authorization and atomicity stay host-owned

Selectors confer no permission. Hosts authorize trusted identity/business access before both start and replay; core receipt identity continues to bind actor and operation. Preserve the sandbox administrator policy, CSRF boundary, immutable saved request-data contribution and host intent. Do not resolve a family into an exact-version command in host orchestration before receipt lookup. No new mandatory core authorization hook or engine permission model is introduced; existing supported guard/capability behavior is unchanged.

Resolution, pin creation, required request linking before dependent actions, synchronous effects, history, receipt and durable handoff participate in one host transaction. Core retains MANDATORY propagation. Failed or unauthorized requests commit none of these. A successful replay must still pass current host access checks, but does not rerun fresh-start business validation or actions. External delivery remains after commit.

## Acceptance scenarios

- **AC01 (R01):** Each selector is accepted independently; missing/multiple/null/blank/invalid selectors fail consistently at core and transport boundaries. Existing exact-version callers/payloads remain usable and large IDs remain exact.
- **AC02 (R02):** A family with PUBLISHED versions 2 and 7, DRAFT 9 and RETIRED 10 starts version 7 by key and ID, regardless of row-ID/publication-time ordering. Exact selection of 2 still starts 2. Counter gaps/deleted drafts are irrelevant. Unknown selectors, no publication and nonpublished exact IDs yield their specified distinct failures.
- **AC03 (R02/R05):** Incompatible inputs or unavailable capabilities on the latest publication fail without fallback or persisted effects. New publication leaves older publications intact; an instance pinned to an older subsequently retired version continues on its pin.
- **AC04 (R03):** Controlled two-transaction start/publication and start/retirement races exercise both lock orders, lifecycle rollback and a preloaded stale persistence context. Selection matches committed lock ordering; a concurrency failure leaves no partial start.
- **AC05 (R04/R05):** Replay after newer publication and original-version retirement returns the original result without resolution/actions/links. Changed selector kind/value, input, host intent or actor cannot reuse the receipt. Lookup-only misses do not resolve. Pre-F024 exact-version receipts still replay.
- **AC06 (R04/R05):** Overlapping identical family starts commit only one instance/link/effect/receipt set; losing work rolls back and lookup-only recovery returns that result. A post-link or durable-recorder failure rolls back the family start atomically.
- **AC07 (R05):** Unauthorized start/replay is denied before definition information/results are exposed. Sandbox API passes all selector forms unchanged through its transaction and recovery paths; its current explicit-version UI and frozen payload remain compatible.

## Readiness

No material product decision blocks this selected API/core slice. The requirements above are implemented; actual checks and remaining boundaries are recorded in [implementation](implementation.md). Lifecycle lock support is present in F023, committed as `c147835` during this planning session; its evidence does not establish F024 family selection. [Plan](plan.md) defines fresh checks and that dependency. Future UI selector controls remain deferred, not an acceptance gate.
