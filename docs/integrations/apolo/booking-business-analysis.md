# Workflow management — APOLO business analysis

> **Updated 2026-10-25.** This document records APOLO-owned business requirements and their decision status. It does not define generic engine behavior. Read the [consolidated design](../../architecture/workflow-engine-design.md) with the subsequent selected changes in [database design v1.4](../../architecture/Workflow_Engine_Database_Design_v1.md); older engine examples do not override those decisions.

## Agreed delivery and ownership

First validate a small synthetic workflow in the standalone sandbox, then integrate one cross-team booking approval in APOLO. Planning-like sandbox examples are test fixtures, not a decision to migrate the existing planning workflow first.

APOLO owns booking data, capacity calculations, approver mappings, business validation, screens, authorization policies and business outcomes. Its booking-specific endpoint/service owns orchestration and invokes the workflow library inside the host transaction.

The engine owns reusable execution, task lifecycle, versioned workflow definitions, expression-based routing, registry contracts and history. A standalone business decision engine is optional and deferred beyond the first stage. The workflow engine must not encode overbooking formulas, country routing or booking-specific alternatives.

## Confirmed integration policies

- **AGREED — approval basis and pending edits:** one identified immutable baseline covers the entire approval attempt, including sequential steps. APOLO blocks protected material changes while pending and verifies membership, material and eligibility at each decision and finalization. Detected material changes prevent further approval of that attempt and require fresh review; harmless edits follow host policy. See [Approval baseline and protected material](#approval-baseline-and-protected-material).
- **AGREED — authorization:** no self-approval; country/business access checks; audited reassignment and administrator overrides. Concrete permissions and whether countries are separate tenants remain APOLO integration questions.
- **AGREED — engine task scope:** multiple directly assigned tasks within one activity are supported by the selected engine direction. Detailed collective completion and APOLO approval policies require specification; claiming, delegation and expiration remain deferred.
- **AGREED — infrastructure boundary:** APOLO owns transaction configuration, notification/delivery infrastructure, recovery and retention. Atomic updates, command idempotency and durable handoff remain required production guarantees.

### Approval baseline and protected material

**AGREED requirement:** an approval applies to an immutable, identified set of business material captured for an approval attempt. All approval steps in that attempt use the same basis. APOLO prevents changes to protected material while approval is pending and verifies current membership, material and eligibility within each decision transaction and finalization transaction. A detected material change prevents further approval of that attempt, preserves previous decision evidence and requires fresh review.

An approval attempt is a host business concept spanning all required approval steps; it is not synonymous with one engine activity. Moving from manager review to planning review must not recapture current bookings or replace the basis. APOLO defines the protected fields, membership, relevant related data, comparison rules, eligibility conditions and attempt identity. Technical booking row versions support concurrency but are not a substitute for the captured material: permitted status or harmless-field updates may change a row version without changing the basis.

**Selected implementation direction:** combine application edit restrictions, an APOLO-owned database guard for protected writes and mandatory verification against the preserved basis. The guard must cover changes to membership and relevant protected records through the supported write paths, including jobs/imports and direct database writes within the host's enforcement boundary. Its implementation, database permissions, covered operations and controlled invalidation/finalization paths belong to the APOLO feature specification and migrations, not workflow-core. An unrestricted client-controlled bypass flag is not an authorization mechanism. No particular trigger, table or locking implementation is selected here.

**Generic storage option:** APOLO may initialize a JSON workflow variable named approvalBaseline at submission through the engine's normal variable-writing contract. For example, instance 9001 may contain this abbreviated value:

```json
{
  "attemptRef": "APPROVAL-9001-1",
  "schemaVersion": 1,
  "bookings": [
    { "bookingId": 101, "allocatedHours": "16.00" },
    { "bookingId": 102, "allocatedHours": "24.00" },
    { "bookingId": 103, "allocatedHours": "8.00" }
  ]
}
```

The name, fields and attempt reference above are illustrative APOLO conventions, not reserved engine identifiers. APOLO captures all relevant material, such as dates, resource, project, hours and required related information, within the submission transaction while protecting the data being captured. Engine storage supplies generic schema/bounds validation and persistence; APOLO owns the baseline's meaning, business completeness and comparison. Host booking associations remain authoritative for current membership; the preserved baseline records the submitted membership against which APOLO checks them.

APOLO makes this variable write-once for the attempt through declared writer permissions and host validation on every exposed write path, including generic variable commands and service bindings. Ordinary JSON-variable mutability is not permission to replace the basis. Subsequent approval activities use the same identified baseline, for example through the existing opaque review_revision_ref where appropriate. Display the captured material and distinguish any current-data warning from what was submitted. Checks at screen-open time are advisory; they do not replace transaction-time verification.

Within each new decision transaction, follow successful-receipt resolution and the early instance claim, then verify exact current membership (including additions/removals), protected values, attempt validity and current eligibility before accepting the decision. Finalization verifies again before applying business effects. APOLO's protection/concurrency protocol must keep the checked membership and related material stable through commit, including against other instances and non-workflow writers; checking rows without protecting the check-to-update interval is insufficient. Preserve the agreed host lock order. An authorized identical receipt replay returns the earlier command result without performing a new approval or rerunning current-state validation.

On mismatch, APOLO blocks the new decision and reports an appropriate authorized explanation, such as submitted 16 hours versus current 24 hours. The engine does not interpret the booking difference or automatically cancel the workflow. APOLO specifies whether the recovery is cancellation, resubmission or a modeled correction path. Fresh review uses a new attempt identity and new basis; earlier approvals must not be counted toward the new attempt. A mismatch detected for an attempt must not be forgotten merely because values are subsequently restored.

Rejecting an approval rolls back that command; an invalidation marker written inside that failed transaction would roll back too. The APOLO feature must specify durable disqualification/recovery and coordination that prevents further acceptance for the invalid attempt. If an explicit invalidation command is used, it is a distinct authorized, idempotent operation, not a successful approval or an automatic engine response to any validation error. Do not claim that a failed completion alone persisted a cancellation or invalidation.

Preserve the full old basis, its identity and its links to decisions for the required audit/recovery period. If a new attempt reuses the instance and variable key, retain the old value in immutable evidence before replacement and keep an unambiguous attempt-to-evidence reference; the current variable row alone cannot preserve earlier attempts. Engine variable-write history may supply that evidence within its limits, or APOLO may use another immutable host record. The exact mechanism, retention and same-instance resubmission remain feature decisions. No booking-snapshot entity, write-once variable primitive or business-specific mismatch outcome is added to the engine.

**Required APOLO feature decisions:** protected fields and related records, baseline schema/identity, guard coverage and controlled write paths, concurrency protocol, comparison/eligibility rules, durable mismatch handling, correction/resubmission route and historical-basis retention. This policy does not choose these implementation details or claim that protection is already implemented.

## Overbooking — DEFERRED, APOLO-owned

Original source notes state:

- Cross-team booking routes to the resource Team Lead and becomes confirmed after approval.
- The Team Lead owns resource capacity.
- Approval should cover only allocation exceeding 100%.
- Approvers may approve, reject or request adjustment.
- Relevant EMs and EPs are notified.

These statements are retained as source requirements, not newly confirmed routing or calculation specifications. The source timing reference to Q1 FY2027 is not a committed implementation schedule.

**Unresolved:** capacity interval and aggregation rules, treatment of pending requests, splitting within-capacity versus excess allocation, partial confirmation, rejection effects and user-interface implications. These are APOLO concerns and are not being resolved in the current engine design.

Source issue: [APOLO-5188 — Workflow engine](https://robucapp32.ro.kworld.kpmg.com/issue/APOLO-5188/Workflow-engine). The linked issue has not been independently checked during this documentation update.

## Approver selection — DEFERRED, APOLO-owned

The source notes mention Team Lead, Country Planning Officer and APOLO Country Coordinator. The authoritative organization source, role precedence, delegation/fallback and behavior when an approver is unavailable remain undecided.

The library accepts neutral assignment contracts. APOLO supplies the resolver and business policy before integrating a scenario that needs it.

## Clarification and adjustment — PROVISIONAL

The tentative preference is to return the same workflow instance to the requester and begin a new approval attempt on resubmission, with a new identified baseline and review activity while preserving the previous basis and history. Progressing to the next approval step within an existing attempt does not refresh the baseline.

This is not a confirmed adjustment flow. Still decide whether clarification can leave a review open, which edits invalidate earlier decisions, who receives the resubmission and how prior approvals are treated. Material changes requiring reapproval is agreed; same-instance handling is provisional.

## Cross-border alternatives — DEFERRED, APOLO-owned

The source scenario allows an approver to propose an alternative resource. No behavior has been selected.

Open questions: must the requester accept the alternative, is a separate booking created, and must the alternative resource's team/country approve it? Do not silently implement direct replacement or automatic approval.

## Request information — source requirements for APOLO validation

The original business notes identify:

- Resource, dates and allocation — requested capacity.
- Engagement and chargeable project code — official code, locking and recharging.
- Mandate rate — commercial conditions.
- On-site/off-site arrangement — delivery model.
- Travel expense coverage — cost responsibility.

APOLO must confirm required fields and validation timing for its selected scenario. These are not generic workflow variables or engine-owned forms by default.

## Illustrative cross-border flow — not a finalized process

1. Requester submits the required business information.
2. APOLO determines the home-country approver.
3. Approver reviews availability, priorities, commercial terms, delivery needs and conflicts.
4. Approver decides or requests clarification.
5. APOLO confirms allocation and notifies stakeholders after the required approvals.

**Unresolved:** notification recipients/timing, clarification and alternative branches, routing and cancellation behavior. Keep this scenario outside the first integration unless its business requirements are explicitly agreed.

## Decision ownership and timing

Overbooking, routing and alternative-resource decisions do not block generic library development. APOLO must resolve whichever rules the first cross-team integration actually exercises before that integration is accepted. Technical open questions are tracked separately in the [architecture register](../../architecture/workflow-engine-design.md#222-unresolved-questions-and-ownership).
