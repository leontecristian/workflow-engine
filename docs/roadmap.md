# Workflow engine roadmap

**Frontend alignment — 2026-11-07:** [F012 — Angular 19 frontend alignment](features/F012-angular-19-frontend-alignment/spec.md) is IN PROGRESS against READY revision 1. The sandbox now uses Angular 19.2; T02–T05 pass dependency, isolated-test, production-build and Maven gates. T06 has partial browser evidence, including sequential completion, but required browser acceptance remains open after Computer Use URL verification stopped the session. See [actual results and gaps](features/F012-angular-19-frontend-alignment/implementation.md). Shared instance-viewer extraction remains a later slice; F011 records and historical gaps remain unchanged.

**Architecture adoption — 2026-11-07:** constitution 6.0.0 and main design 0.11 adopt Angular 19 `workflow-ui` and optional host-consumed `workflow-web`. The target has core/web/test-host Maven modules plus the Angular library; the current reactor still has two Maven modules and F012 aligns its existing console. Extraction needs a separate feature specification and implementation request and must not alter F011 scope or evidence. The designer remains after first APOLO integration, delivered within workflow-ui rather than as a separate production application.

**Updated:** 2026-11-07, to record F011 implementation and its remaining interactive browser acceptance gap.

This is the current delivery roadmap. It replaces the separate development plan formerly held in `apolo-docs`. The [feature index](features/README.md) tracks implemented features; each feature's tasks and implementation record own detailed progress and evidence. The [constitution](constitution.md), [constraints](constraints.md) and [design-source precedence](design-sources.md) govern behavior. This roadmap selects no new API, schema, business rule or release date.

The agreed delivery direction is **working sandbox → first APOLO booking integration → graphical designer**, with reliability required before any production deployment. The future feature breakdown below is **PROPOSED**; the underlying V1 capabilities already selected by the constitution remain agreed. Assign feature IDs when their specifications begin, rather than treating roadmap entries as READY features.

## 1. Where we are

| Delivered scope | Current position | Evidence and remaining work |
| --- | --- | --- |
| F001–F003: schema, task catalog and persistence | Implemented within their feature scopes | Local migration/build/discovery evidence exists. Some catalog mutation, concurrency, hydration and paging scenarios remain unverified in the original records; later evidence must be matched explicitly. |
| F004: definition management | Implemented for the minimal SINGLE language subset | Draft import/edit/validation, publication and retirement exist. F006 adds management API/database observations; these do not automatically close every F004 scenario. |
| F005: minimal execution | Core implemented | Exact-version `START → HUMAN_TASK (SINGLE) → END`, assignment, completion, history, receipt replay and durable recorder integration exist. Original T11 acceptance remains open. |
| F006: development console | Revision 5 implemented; six-point browser walkthrough complete; full acceptance incomplete | Owner confirmed all six walkthrough points on 2026-11-01, including graph, command recovery, local dispatch/redelivery and logout clearing. Full scenario/evidence reconciliation and uncovered technical checks remain; see the handover. |

See the [feature index](features/README.md) and [F006 handover](features/F006-test-host-definition-console/implementation.md) for revision-specific results. IMPLEMENTED means code exists, not that all acceptance or production gates passed. Historical BLOCKED entries describe the environment at the time; they do not establish that the same blocker still exists today.

F007–F009 implement service actions, explicit scalar/JSON bindings and restricted expression routing. F010 adds 1–16 outcomes with exact routes within one SINGLE human stage. F011 implements multiple sequential/conditional SINGLE stages; local unit/SQL/HTTP checks are recorded, with interactive browser acceptance blocked. Structured results, collective review and cancellation/reassignment remain future work. The console's read-only graph is not the graphical designer.

## 2. Immediate priority: accept the existing baseline

Complete this work under the existing features rather than creating a new capability merely to close evidence gaps.

**Progress — 2026-11-01:** the owner completed all six browser walkthrough points, including two-tab editing, two-account completion, instance graph inspection, exported-command recovery/replay, local event delivery/duplicate suppression and logout clearing. See [the recorded observations](features/F006-test-host-definition-console/implementation.md#owner-browser-acceptance-progress--2026-11-01). The next action is to reconcile these and the earlier API/database evidence against the full feature scenarios, identifying only uncovered checks; the walkthrough does not automatically close every task below.

1. **F006 browser acceptance:** finish T08/T09/T12/T13 against the intended packaged revision, covering administrator/reviewer navigation, two-tab conflicts and unsaved text, command recovery, instance monitoring, graph refresh/failure behavior, keyboard use and responsive layout. Cover the remaining portions of AC01–AC30 as applicable; preserve API-only and isolated-test results as such.
2. **Revision 5 live acceptance:** verify the graph endpoint, pinned-version/history display and authorization using the real host. Confirm the running artifact actually includes revision 5 before relying on it.
3. **F005 evidence reconciliation:** map F006's observed scenarios to F005 AC01–AC14, identify uncovered requirements and complete the remaining authorized checks before closing T11. F006 now supplies the adapters absent at the original F005 handover; that changes the dependency, not the acceptance result.
4. **Earlier-feature reconciliation:** map relevant F006 management/persistence observations to F002–F004, retaining any unmatched race, rollback, hydration or paging gaps. Record new evidence in the owning implementation records without rewriting historical results.

**Exit:** applicable scenarios have explicit outcomes and revision/environment references; required gaps are resolved before claiming baseline acceptance. This roadmap update itself closes no task. Independent specification work can continue while environment-dependent acceptance is pending.

## 3. Next engine increments

**Latest implemented increment:** [F010 — Human-task outcomes and outcome-based routing](features/F010-human-task-outcomes/spec.md), revision 1, delivers explicit Approve/Reject-style choices and exact routes within one SINGLE human stage. Local unit/SQL/HTTP checks and the completed Chrome walkthrough are recorded in its handover, with their coverage limits.

**Latest delivered slice:** [F011 — Multiple sequential human stages](features/F011-sequential-human-stages/spec.md), IMPLEMENTED revision 1. The subsequent 2026-11-07 request authorized successive/conditional SINGLE stages in an acyclic graph, one active stage, atomic entry, exact historical contracts and stable receipts. Its [handover](features/F011-sequential-human-stages/implementation.md) records local evidence and the blocked browser walkthrough. Earlier acceptance reconciliation and the proposed increments below remain independent work; no next feature is selected here.

The proposed sequence grows from the accepted minimal workflow to a useful branching sandbox, then richer reviews. Each increment should include its core contract, real test-host adapter/UI support where needed, and its own acceptance evidence. Extend the F006 graph only for shapes it can represent correctly; retain an explicit unavailable state for unsupported shapes.

| Proposed increment | Dependency and scope | Exit condition |
| --- | --- | --- |
| **A — [F007: Typed bindings and service tasks](features/F007-typed-bindings-service-tasks/spec.md)**; IMPLEMENTED through revision 3; revision 2 T09 closed by owner acceptance | Extends F004/F005. The selected bounded slice covers scalar start inputs/variables, direct initialization/output bindings and registered synchronous SERVICE_TASK actions around one SINGLE human task. Expressions, direct fact/submission bindings and nonempty guards remain outside this slice. Core, sample host migration/action and console support are implemented; revision 3 batches variable-write evidence per mapping operation. Isolated tests/builds pass; revision 3 has scoped SQL transaction checks. T09 is closed by owner acceptance after manual API/SQL checks and the owner browser walkthrough/replay confirmation; the handover retains unverified follow-up coverage. | A real sandbox workflow performs a declared service action and pauses at a human task or reaches END. Invalid inputs, unavailable capabilities and action/output failures roll back the command; successful replay repeats no business effect. Existing minimal definitions remain supported. |
| **B — [F008: Restricted expressions and exclusive routing](features/F008-restricted-expressions-routing/spec.md)**; IMPLEMENTED revision 3; T08 acceptance open | Build on A's typed data and bounded progression. Complete expression semantics, read-only access restrictions, budgets and publication validation; implement EXCLUSIVE_GATEWAY using first-true transition order, a separate default and strict evaluated-error failure. | A sandbox exercises true/false/default/no-match/error and short-circuit paths, with pinned order and routing evidence. Unsafe expressions are rejected and failures roll back. A service action, routing and human completion work together end to end. |
| **C — Structured task submissions** | Extend the F005 submission baseline and pinned human-node contracts under CON-13 / ASM-10. Specify schema profile/bounds, result presence, full-payload fingerprints, authorized exact-task/activity reads and downstream same-transaction access. | Valid structured results are stored once in immutable completion evidence; invalid/unauthorized results fail. Replay and rollback preserve exact submissions; a downstream action can read the final submission before commit. |
| **D — [F009: JSON execution variables](features/F009-json-execution-variables/spec.md)**; IMPLEMENTED revision 3; browser acceptance NOT RUN | Extend A/B under CON-14 / ASM-11. Specify schema-free bounded snapshots, whole-value start/service mappings, detached projections, safe expression navigation and write evidence. Field schemas and a standalone external variable-update command are deferred in F009. C is needed only for a scenario that consumes submissions; JSON variables do not depend inherently on task results. | A declared JSON value can be initialized, replaced and read safely under aggregate concurrency, with exact fingerprints and history. No implicit merge or submission-to-variable copying occurs. |
| **E — ALL collective review** | Extend assignment, human-node validation and completion beyond SINGLE. Build on C if the selected review uses structured submissions; JSON variables are not inherently required. Keep one active process position and the fixed task roster. | All contributions or a permitted early outcome resolve the activity correctly. Open siblings cancel on early resolution, earlier submissions survive, competing commands have one committed result and downstream failures roll back the current command. |
| **F — Remaining runtime operations** | Specify cancellation and audited administrative reassignment, plus any required authorized variable-update command not delivered in D. Exact APIs, lifecycle rules and evidence are still feature work. Pull forward only operations required by the chosen scenario. | Authorized operations preserve task identity where required, instance concurrency, replay, history and atomicity; unsupported operations remain explicit. This does not introduce claiming or general delegation. |

A/B deliver the next end-to-end sandbox milestone. C–F complete additional agreed capabilities in bounded slices; their exact ordering should follow dependencies and the selected booking scenario. The first APOLO integration need not wait for an unrelated capability, but the broader agreed V1 scope remains tracked rather than silently dropped.

For each slice, use the [four-document workflow](specification-driven-development.md) and the current [language design](architecture/workflow-definition-language-design.md). Carry forward selected language decisions; resolve remaining API, codec, limits and compatibility details before dependent code. Do not revive task-type business versions, the former DECISION node name or a separate expressionPolicy field.

## 4. First APOLO booking integration

**Status:** planned; concrete business scenario and host compatibility still require specification. Business discovery and read-only build analysis can proceed alongside engine work. Runtime integration depends on the engine capabilities actually needed by that scenario.

- Select one cross-team booking approval from the [booking analysis](integrations/apolo/booking-business-analysis.md) and [approval requirements](integrations/apolo/approval-capability.md). APOLO decides approver sources, access/no-self-approval rules, protected material, review membership, final business effects and required recovery paths. Do not invent unresolved overbooking or alternative-resource policy.
- Preserve one identified immutable approval basis across all steps of an attempt. Specify protection against material edits, transaction-time membership/material/eligibility checks, durable invalidation and historical retention. Same-instance resubmission remains provisional; a failed completion alone does not persist invalidation.
- Implement APOLO-owned endpoints, screens, assignment/capability/guard adapters and business actions through neutral core contracts. Resolve handlers by stable task code and pinned node contract, retaining support for published definitions. APOLO owns booking-to-instance links; no particular host association table is mandated.
- Verify the effective APOLO build and dependency graph, shared persistence/transaction setup, entity/repository registration and SQL Server flush/concurrency behavior. The [technical summary](integrations/apolo/apolo-technical-summary.md) and reference POM are inputs, not fresh compatibility evidence.
- Supply production-appropriate durable handoff/delivery, recovery and retention. F006's explicit local outbox-to-inbox delivery demonstrates a sandbox mechanism; it is not an APOLO production adapter.

**Exit:** one specified booking scenario works through real APOLO interfaces, with observed authorization, shared-transaction rollback in both directions, concurrency, idempotent retries, approved review-basis handling and durable recovery. Resolve the applicable ASM-01–07 and ASM-10/11 obligations for the demonstrated scope; local sandbox evidence alone cannot close them.

### Source integration and ownership

The eventual move of `workflow-core` into APOLO as a sibling Maven module is agreed; timing, coordinates, release ownership and test-host location remain OPEN (ASM-09). An interim fixed library artifact or combined reactor can support the first integration. At the selected move point, keep one authoritative implementation, review/reparent against APOLO's effective root build and register backend's library dependency.

Preserve Java 17 APIs/bytecode, JDK 21 runtime, ordinary JAR packaging and neutral contracts. Core keeps its migration resources and stable changeset identities; APOLO executes the core master from its existing Liquibase lifecycle. The test host remains outside the production dependency graph. This roadmap does not perform or schedule the source move.

## 5. Graphical designer and release

**Designer — after successful first APOLO integration:** deliver the graphical authoring feature in the Angular 19 workflow-ui library, embedded in APOLO and available through the sandbox development shell for supported node types, stable task catalog selection, bindings, ordered routes, JSON inspection, backend validation and explicit publication. Define round-trip compatibility, unknown-feature handling, authoring permissions and preview semantics under CON-11. Diagram layout must not alter route priority; the frontend must not execute the workflow engine or SpEL. The F006 inspector does not close this milestone.

**Release hardening — throughout delivery, mandatory before production:** verify compatible published-definition support, migrations/upgrades, authorization, expression safety, shared transactions, concurrency/replay, delivery restart/recovery and retention. Establish operational monitoring, recovery procedures and release ownership for APOLO. Final packaging/release arrangements follow the agreed source integration; production guarantees cannot be postponed until after the designer.

**Exit:** designer acceptance is demonstrated separately from production readiness, and every production deployment has the required host-specific reliability and operational evidence.

## 6. Deferred scope and open scheduling decisions

- The optional business decision engine, decision tables, FIRST/UNIQUE policies and their storage/APIs/editor require a separately selected need. They do not block workflow routing, APOLO integration or the designer.
- Claim/release, candidate groups, general delegation, timers/expiration, ANY/QUORUM, inclusive/parallel gateways, subprocesses and active-instance migration remain deferred. ALL is agreed V1 work, not part of this deferred list.
- F007 is implemented through revision 3, including batched variable-write evidence; revision 2 T09 is closed by owner acceptance, and remaining unverified coverage is documented in its handover. F008 revision 3 implements owner-corrected native restricted SpEL and exclusive routing, with guarded SQL/unit/build evidence; T08 browser and delivery acceptance remains open. F009 revision 3 is implemented, with unit, SQL Server and frontend evidence; interactive browser and APOLO acceptance remain unverified. Other later feature boundaries/IDs, delivery dates and assigned owners remain unset.
- APOLO decides business branches, source-move timing and deployment/release details at their stated decision points. Generic engine work may continue independently of unrelated business decisions.

## 7. Keeping the roadmap current

Update this document when a milestone or priority changes, and update the [feature index](features/README.md) when a feature is specified or delivered. Keep detailed tasks and actual verification in the owning feature documents. Do not copy historical status paragraphs into navigation pages.

Use the owner clarification in [AGENTS.md](../AGENTS.md): isolated unit tests and integration tests on `localhost:1433/apolo_workflow_test` are authorized. Other database targets and runtime checks outside that harness require task-specific authorization. Documentation-only work uses content, link and whitespace checks, with no build or host startup.

Historical context is retained in the [development discussion](archive/development-discussion.md). This consolidation preserves earlier evidence and source provenance; it supersedes the old development plan's obsolete delivery assumptions, not the governing architecture.
