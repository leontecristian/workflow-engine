# Workflow engine roadmap

**Updated:** 2026-11-13 · **Delivery baseline:** F022 previous-decision comments, IMPLEMENTED revision 1

The agreed direction is **working sandbox → first APOLO booking integration → graphical designer**, with reliability required before production. The sandbox, reusable libraries and ALL collective review are implemented. F022 previous-decision comments is implemented with focused core/shared web/UI and sandbox verification. APOLO integration waits until the owner has access to APOLO.

## Contents

1. [Current position](#1-current-position)
2. [Selected priorities](#2-selected-priorities)
3. [Remaining engine capabilities](#3-remaining-engine-capabilities)
4. [First APOLO booking integration](#4-first-apolo-booking-integration)
5. [Graphical designer](#5-graphical-designer)
6. [Production readiness](#6-production-readiness)
7. [Deferred scope and open decisions](#7-deferred-scope-and-open-decisions)
8. [Reference and historical notes](#8-reference-and-historical-notes)

## 1. Current position

**✅ Sandbox + shared libraries + ALL review → ✅ Previous-decision comments → ⏸ APOLO integration → ⏳ Graphical designer**

### ✅ Implemented — F001–F022

| Features | Capability |
| --- | --- |
| ✅ F001–F006 | **Foundation & sandbox** — database, catalog, definitions, execution, console |
| ✅ F007–F011 | **Execution & routing** — services, JSON data, expressions, outcomes, sequential reviews |
| ✅ F012–F015 | **Shared libraries** — Angular 19, web/UI components, viewer, administration, task UI |
| ✅ F016–F017 | **Assignment & claiming** — user/group/role tasks, claim, release |
| ✅ F018–F020 | **Review data** — structured answers, named outputs, frozen task inputs |
| ✅ F021 | **Collective review** — fixed task roster, contributions/early resolution and original collections |
| ✅ F022 | **Previous decisions** — pinned visibility, receiving-task context and bounded shared reads |

### ✅ Latest delivery

> **F022 previous-decision comments — IMPLEMENTED revision 1.**
>
> See the [specification](features/F022-previous-decision-comments/spec.md) and [plan](features/F022-previous-decision-comments/plan.md). See the [F022 handover](features/F022-previous-decision-comments/implementation.md) for focused verification; APOLO integration remains separate. F021's completed delivery and live walkthrough are recorded in its [handover](features/F021-all-collective-review/implementation.md).

### ⏳ Still to deliver

| Status | Capability | When |
| --- | --- | --- |
| ⬜ Not implemented | **Cancellation & reassignment** | Prioritize what the selected booking scenario needs. |
| ⏸ Waiting for access | **APOLO booking integration** | Resume when the owner has access to APOLO. |
| ⬜ Not implemented | **Graphical designer** | After the first successful APOLO integration. |

**Verification:** ✅ means implemented, not production-ready. Historical acceptance gaps remain; production checks are required before deployment. See the [feature index](features/README.md) for evidence and section 8 for historical notes. The existing read-only graph is not the designer.

## 2. Selected priorities

**Owner decisions — 2026-11-13:** F021 was subsequently implemented and verified as recorded in its [handover](features/F021-all-collective-review/implementation.md). The owner requested specification and planning only for [F022](features/F022-previous-decision-comments/spec.md), including workflow default and human-node overrides. The owner subsequently authorized T02–T06, delivered in the F022 handover. APOLO integration still waits for access.

| Order | Work | Completion condition |
| --- | --- | --- |
| 1 — planning complete | F022 design, specification and plan | Visibility inheritance, stable prior-stage selection, receiving-task access and task-context reads are specified. |
| 2 — delivered | Deliver F022 in the engine, shared libraries and sandbox | The selected feature passes its focused acceptance checks without depending on APOLO access. |
| 3 — waiting for access | Specify and deliver the first APOLO booking integration | Access is available, the business scenario and host compatibility are established, and the integration exit in section 4 is met. Select additional engine operations only as required. |
| 4 | Deliver the graphical designer in workflow-ui | The authoring exit in section 5 is met after successful first APOLO integration. |

Production readiness applies before any deployment, regardless of designer timing. Historical verification debt is reconciled when relevant to a selected dependency or integration/release gate, rather than becoming a blanket prerequisite for new work.

## 3. Remaining engine capabilities

| Capability | Scope and dependencies | Exit condition |
| --- | --- | --- |
| **Cancellation and audited administrative reassignment** — exact slices open | Specify lifecycle rules, authorization, APIs and evidence. Pull forward operations required by the chosen scenario; do not bundle them automatically with ALL. | Selected operations preserve task identity where required, aggregate concurrency, replay, history and atomicity. |
| **External variable-update command** — conditional scope | F009 supports start/service replacements, not a standalone external update command. Specify one only when a selected scenario requires it. | Authorized updates follow the agreed whole-value and aggregate concurrency contracts, with focused verification. |

A shared GROUP/ROLE SINGLE task is one shared obligation; it cannot substitute for ALL contributions. Claim/release already exists in F017. ALL remains agreed V1 scope even if the first integration can use SINGLE.

Assign feature IDs when specifications begin. Follow the [four-document workflow](specification-driven-development.md) and [language design](architecture/workflow-definition-language-design.md), including later feature decisions. Resolve material contracts before dependent code. Extend the console graph only for supported shapes and retain an explicit unavailable state for unsupported shapes.

## 4. First APOLO booking integration

**Status:** waiting for owner access to APOLO. Resume integration work when access is available; the concrete business scenario and actual host compatibility still require specification. ALL design and sandbox delivery proceed independently.

- Select one cross-team booking approval from the [booking analysis](integrations/apolo/booking-business-analysis.md) and [approval requirements](integrations/apolo/approval-capability.md). APOLO decides approver sources, access/no-self-approval rules, protected material, review membership, final business effects and required recovery paths. Do not invent unresolved overbooking or alternative-resource policy.
- Preserve one identified immutable approval basis across all steps of an attempt. Specify protection against material edits, transaction-time membership/material/eligibility checks, durable invalidation and historical retention. Same-instance resubmission remains provisional; a failed completion alone does not persist invalidation.
- Implement APOLO-owned endpoints, screens, assignment/capability/guard adapters and business actions through neutral core contracts. Resolve handlers by stable task code and pinned node contract, retaining support for published definitions. APOLO owns booking-to-instance links; no particular host association table is mandated.
- Verify the effective APOLO build and dependency graph, shared persistence/transaction setup, entity/repository registration and SQL Server flush/concurrency behavior. The [technical summary](integrations/apolo/apolo-technical-summary.md) and reference POM are inputs, not fresh compatibility evidence.
- Supply production-appropriate durable handoff/delivery, recovery and retention. F006's explicit local outbox-to-inbox delivery demonstrates a sandbox mechanism; it is not an APOLO production adapter.

**Exit:** one specified booking scenario works through real APOLO interfaces, with observed authorization, shared-transaction rollback in both directions, concurrency, idempotent retries, approved review-basis handling and durable recovery. Resolve the applicable ASM-01–07 and ASM-10/11 obligations for the demonstrated scope; local sandbox evidence alone cannot close them.

### Source integration and ownership

The eventual move of `workflow-core` into APOLO as a sibling Maven module is agreed; timing, coordinates, release ownership and test-host location remain OPEN (ASM-09). An interim fixed library artifact or combined reactor can support the first integration. At the selected move point, keep one authoritative implementation, review/reparent against APOLO's effective root build and register backend's library dependency.

Preserve Java 17 APIs/bytecode, JDK 21 runtime, ordinary JAR packaging and neutral contracts. Core keeps its migration resources and stable changeset identities; APOLO executes the core master from its existing Liquibase lifecycle. The test host remains outside the production dependency graph. This roadmap does not perform or schedule the source move.

## 5. Graphical designer

**Dependency:** successful first APOLO integration.

Deliver graphical authoring in the Angular 19 workflow-ui library, embedded in APOLO and available through the sandbox shell. Scope includes supported node types, stable task catalog selection, bindings, ordered routes, JSON inspection, backend validation and explicit publication.

Specify round-trip compatibility, unknown-feature handling, authoring permissions and preview semantics under CON-11. Diagram layout must not alter route priority; the frontend must not execute the workflow engine or SpEL.

**Exit:** supported definitions can be authored, validated and published through the shared designer with demonstrated round-trip compatibility. Designer acceptance remains separate from production readiness.

## 6. Production readiness

**Timing:** throughout delivery; mandatory before production, not postponed until after the designer.

Verify applicable published-definition compatibility, migrations/upgrades, authorization, expression safety, shared transactions, concurrency/replay, delivery restart/recovery and retention. Establish APOLO operational monitoring, recovery procedures and release ownership. Final packaging/release arrangements follow the agreed source integration.

At the selected integration/release milestone, reuse applicable evidence and check actual host/environment changes or unresolved gaps. This is not a full release checklist to repeat for every feature or UI change.

**Exit:** each production deployment has the required host-specific reliability and operational evidence, with relevant unresolved acceptance gaps addressed.

## 7. Deferred scope and open decisions

| Item | Position |
| --- | --- |
| Business decision engine, decision tables and FIRST/UNIQUE policies | Optional later work requiring a selected need; not a dependency of workflow routing, first integration or the designer. |
| General delegation, timers/expiration, ANY/QUORUM, inclusive/parallel gateways, subprocesses and active-instance migration | Deferred. ALL is agreed V1 work and is tracked in section 3. |
| APOLO approval branches and recovery | APOLO selects approver sources, material-change policy and recovery paths. Same-instance resubmission remains provisional. |
| Source move and release ownership | Timing, coordinates, test-host placement and release responsibilities remain open; section 4 records the agreed boundary. |
| Future feature IDs, dates and owners | Unassigned until the corresponding specification and scheduling decisions. |

Generic engine work may proceed independently of unrelated APOLO business decisions.

## 8. Reference and historical notes

### Document authority and maintenance

This roadmap replaces the separate development plan formerly held in `apolo-docs`. The [constitution](constitution.md), [constraints](constraints.md) and [design-source precedence](design-sources.md) govern behavior. This document selects no new API, schema, business rule or release date.

Update the current-position and priority sections when delivery changes. Keep detailed tasks and actual verification in the owning feature records and individual statuses in the [feature index](features/README.md). Put dated context here rather than prepending it to the roadmap. Earlier discussions remain in the [development discussion](archive/development-discussion.md).

**Owner delivery clarification — 2026-11-07:** keep slices limited to requested behavior and real dependencies. Follow [focused verification and evidence reuse](specification-driven-development.md#focused-verification-and-evidence-reuse): reuse applicable evidence, check changed boundaries and stop when scoped checks pass. Repeat or broaden checks only for a relevant change, failure or identified gap. Do not add speculative functionality or routine full-system acceptance campaigns.

Follow [AGENTS.md](../AGENTS.md) for verification authorization. Isolated unit tests and guarded integration tests on `localhost:1433/apolo_workflow_test` are authorized; other database targets require task-specific authorization. Documentation-only changes use content, link and whitespace checks, without builds or host startup.

### Priority decision — 2026-11-13

The owner selected ALL collective review as the next feature because APOLO access is unavailable. Start with design discussion, then specification and planning. APOLO integration waits for access; the designer remains after successful first integration. Open ALL contracts remain open until discussed and specified.

### Review baseline — 2026-11-13

Reviewed against feature records through F020 and repository HEAD `cbc64e2`. This reorganization changes no feature status or acceptance outcome. The earlier integration-first proposal was superseded by the owner decision below.

F017–F020 have recorded focused verification. F020 explicitly has no open verification item within its slice; browser rendering checks used prepared NgModule states, while guarded SQL/MVC tests covered real read wiring. APOLO and production acceptance were not run.

F007 is implemented through revision 3; revision 2 T09 closed by owner acceptance, with remaining follow-up coverage recorded in its handover. F008 revision 3 retains T08 browser/live-delivery acceptance gaps; F009 revision 3 retains historical browser/APOLO gaps. F010 records a completed Chrome walkthrough. F011 records unit/SQL/HTTP checks and a historical blocked browser walkthrough. Later evidence must be matched explicitly before closing earlier gaps; historical BLOCKED entries do not establish a current environmental blocker.

Earlier roadmap labels A–D correspond to delivered F007, F008, F018 and F009 respectively. Their specifications and handovers retain the detailed scope and exit evidence. Former E/F are now the remaining capabilities in section 3.

### Historical acceptance reconciliation

This is verification debt owned by the existing features, not a blanket prerequisite for every new slice. Reconcile it when a selected feature depends on an unresolved guarantee or when planning the applicable integration/release gate. First map existing evidence, including F012's completed acceptance, and run only genuinely uncovered relevant checks. Do not create functionality merely to close evidence gaps or repeat already applicable observations.

**Progress — 2026-11-01:** the owner completed all six browser walkthrough points, including two-tab editing, two-account completion, instance graph inspection, exported-command recovery/replay, local event delivery/duplicate suppression and logout clearing. See [the recorded observations](features/F006-test-host-definition-console/implementation.md#owner-browser-acceptance-progress--2026-11-01). The next action is to reconcile these and the earlier API/database evidence against the full feature scenarios, identifying only uncovered checks; the walkthrough does not automatically close every task below.

1. **F006 browser acceptance:** map the existing walkthrough and applicable F012 observations to T08/T09/T12/T13 and AC01–AC30 before selecting any new browser work. Retain only uncovered checks relevant to the scheduled reconciliation. Do not repeat the full administrator/reviewer, command recovery or layout matrix by default; preserve evidence types and revision boundaries.
2. **Revision 5 live acceptance:** reconcile available graph endpoint, pinned-version/history and authorization evidence against the relevant implementation revision. Request a fresh observation only where an identified coverage/environment difference requires it; do not restart a host simply because this historical item remains listed.
3. **F005 evidence reconciliation:** map F006's observed scenarios to F005 AC01–AC14, identify uncovered requirements and complete the remaining authorized checks before closing T11. F006 now supplies the adapters absent at the original F005 handover; that changes the dependency, not the acceptance result.
4. **Earlier-feature reconciliation:** map relevant F006 management/persistence observations to F002–F004, retaining any unmatched race, rollback, hydration or paging gaps. Record new evidence in the owning implementation records without rewriting historical results.

**Exit for the scheduled reconciliation:** relevant scenarios have outcomes or reused evidence with revision/environment references; actual remaining gaps stay explicit. This roadmap update closes no historical task. Independent implementation as well as specification work may proceed unless a stated dependency requires the unresolved evidence. Reconciliation is not a requirement to reproduce every historical run.

### Dated delivery and architecture notes

The entries below retain their original delivery context; the current-position table above is the roadmap summary.

**Human-task inputs — 2026-11-10:** [F020 — Human-task input bindings](features/F020-human-task-input-bindings/spec.md) is implemented. It adds variable/original-submission bindings, immutable creation-time snapshots and task-authorized read-only display. See the [handover](features/F020-human-task-input-bindings/implementation.md) for verification.

**Named task outputs — 2026-11-10:** [F019 — Named task output variables](features/F019-named-task-output-variables/spec.md) is IMPLEMENTED revision 2. An optional human-node output name creates a JSON variable at accepted completion without a separate declaration or service node. Collision rejection, explicit absence, ordinary replacement and variable visibility are settled. See the [handover](features/F019-named-task-output-variables/implementation.md) for verification. F019 also supports explicit service input bindings to the original submission by human node key, independent of the current variable value or triggering completion.

**Implemented slice — 2026-11-10.** [F018 — Structured task submissions](features/F018-structured-task-submissions/spec.md) adds pinned JSON validation, exact replay and authorized result reads. See the [handover](features/F018-structured-task-submissions/implementation.md) for guarded SQL Server and live sandbox acceptance. ALL review, operational commands and APOLO integration remain separate.

**Claim/release decisions — 2026-11-09.** [F017](features/F017-task-claim-release/spec.md) is IMPLEMENTED revision 3 with guarded SQL Server and live sandbox acceptance complete. The owner selected per-human-node OPTIONAL (default) or REQUIRED claiming for shared tasks, exclusive claimant completion subject to current eligibility/business access, owner release and host-authorized administrator release, and a separate nullable assignee_ref column preserving the original assignment. Direct USER tasks initialize assignee_ref from assignment_ref and complete without a claim action under either policy. Claim/release actions apply only to GROUP/ROLE tasks. Eligible peers retain claimed-task visibility; current assignees retain discovery/release after membership loss under business access. Own receipt recovery survives membership/admin-privilege loss under business access. Administrative release requires a recorded reason. Constitution 6.2.0 records this additive scope. O01–O06 are closed. Earlier no-claim wording describes the F016 baseline.

**Shared assignment amendment — 2026-11-07:** One SINGLE task has one stable USER, GROUP or ROLE assignment target. Any currently eligible member may complete it without claiming; one completion commits and records the actual actor. Hosts own membership and business authorization. Membership changes do not change the task roster or reopen completed tasks. F017 subsequently implements claiming/releasing under its own pinned contracts. [F016](features/F016-shared-group-role-task-assignment/spec.md) is IMPLEMENTED revision 2; see its implementation record for scoped verification. Constitution 6.1.0 supersedes earlier direct-user-only assignment and blanket candidate-group deferral for this SINGLE slice. F016 implements the closed assignment, membership, access and preserving-migration contracts; APOLO integration remains separate. Existing user-assignment examples remain valid.

**F015 implementation — 2026-11-07:** [Runtime task, command and recovery extraction](features/F015-runtime-task-instance-extraction/implementation.md) shares remaining selected runtime inspection, complete task interaction and command recovery. Read-only consumers remain independent of command providers. Host business starts, transactions, audit and delivery remain host-owned; APOLO integration and new engine capabilities remain separate.

**F014 implementation — 2026-11-07:** [Definition/catalog administration extraction](features/F014-definition-catalog-administration/implementation.md) is implemented with focused acceptance complete. Shared HTTP/page implementations are consumed by sandbox adapters, preserving host access, CSRF, transaction and audit boundaries. No engine, schema or APOLO change; later authoring/designer and production integration remain separate.

**F013 implementation — 2026-11-07:** [Shared instance viewer extraction](features/F013-shared-instance-viewer/implementation.md) supplies the three-module reactor and Angular package consumed by the sandbox. T03–T06 and the NgModule browser pass are complete. T07 guarded read integration and backend-served production-console acceptance are complete, preserving F012 acceptance. No engine commands, migrations or APOLO implementation changed.


**Frontend alignment — 2026-11-07:** [F012 — Angular 19 frontend alignment](features/F012-angular-19-frontend-alignment/spec.md) is IMPLEMENTED revision 1 with sandbox acceptance complete. Dependency, isolated-test, production-build, Maven and browser gates pass, including updated packaged serving, exact downloads and recovery/read failures. See [actual results and scope](features/F012-angular-19-frontend-alignment/implementation.md). Shared instance-viewer extraction is now implemented under F013 with scoped runtime acceptance complete; APOLO integration remains a later slice; F011 records and historical gaps remain unchanged.

**Architecture adoption — 2026-11-07:** constitution 6.0.0 and main design 0.11 adopt Angular 19 `workflow-ui` and optional host-consumed `workflow-web`. The target has core/web/test-host Maven modules plus the Angular library; F013 now implements the third module and the read-only Angular package. Broader extraction needs a separate feature specification and implementation request and must not alter F011 scope or evidence. The designer remains after first APOLO integration, delivered within workflow-ui rather than as a separate production application.
