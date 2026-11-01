# Workflow engine roadmap

**Updated:** 2026-11-01, after F006 revision 5 and documentation consolidation.

This is the current delivery roadmap. It replaces the separate development plan formerly held in `apolo-docs`. The [feature index](features/README.md) tracks implemented features; each feature's tasks and implementation record own detailed progress and evidence. The [constitution](constitution.md), [constraints](constraints.md) and [design-source precedence](design-sources.md) govern behavior. This roadmap selects no new API, schema, business rule or release date.

The agreed delivery direction is **working sandbox → first APOLO booking integration → graphical designer**, with reliability required before any production deployment. The future feature breakdown below is **PROPOSED**; the underlying V1 capabilities already selected by the constitution remain agreed. Assign feature IDs when their specifications begin, rather than treating roadmap entries as READY features.

## 1. Where we are

| Delivered scope | Current position | Evidence and remaining work |
| --- | --- | --- |
| F001–F003: schema, task catalog and persistence | Implemented within their feature scopes | Local migration/build/discovery evidence exists. Some catalog mutation, concurrency, hydration and paging scenarios remain unverified in the original records; later evidence must be matched explicitly. |
| F004: definition management | Implemented for the minimal SINGLE language subset | Draft import/edit/validation, publication and retirement exist. F006 adds management API/database observations; these do not automatically close every F004 scenario. |
| F005: minimal execution | Core implemented | Exact-version `START → HUMAN_TASK (SINGLE) → END`, assignment, completion, history, receipt replay and durable recorder integration exist. Original T11 acceptance remains open. |
| F006: development console | Revision 5 implemented; acceptance incomplete | Management/execution pages, real sandbox adapters, local audit/outbox/inbox, instance monitor and read-only graph exist. Browser acceptance and revision 5 live checks remain outstanding. |

See the [feature index](features/README.md) and [F006 handover](features/F006-test-host-definition-console/implementation.md) for revision-specific results. IMPLEMENTED means code exists, not that all acceptance or production gates passed. Historical BLOCKED entries describe the environment at the time; they do not establish that the same blocker still exists today.

The broader language is not implemented by the three-node sandbox. In particular, service actions, expression routing, general input/variable bindings, structured results, collective review and cancellation/reassignment commands still need their own specified runtime work. The console's read-only graph is not the graphical designer.

## 2. Immediate priority: accept the existing baseline

Complete this work under the existing features rather than creating a new capability merely to close evidence gaps.

1. **F006 browser acceptance:** finish T08/T09/T12/T13 against the intended packaged revision, covering administrator/reviewer navigation, two-tab conflicts and unsaved text, command recovery, instance monitoring, graph refresh/failure behavior, keyboard use and responsive layout. Cover the remaining portions of AC01–AC30 as applicable; preserve API-only and isolated-test results as such.
2. **Revision 5 live acceptance:** verify the graph endpoint, pinned-version/history display and authorization using the real host. Confirm the running artifact actually includes revision 5 before relying on it.
3. **F005 evidence reconciliation:** map F006's observed scenarios to F005 AC01–AC14, identify uncovered requirements and complete the remaining authorized checks before closing T11. F006 now supplies the adapters absent at the original F005 handover; that changes the dependency, not the acceptance result.
4. **Earlier-feature reconciliation:** map relevant F006 management/persistence observations to F002–F004, retaining any unmatched race, rollback, hydration or paging gaps. Record new evidence in the owning implementation records without rewriting historical results.

**Exit:** applicable scenarios have explicit outcomes and revision/environment references; required gaps are resolved before claiming baseline acceptance. This roadmap update itself closes no task. Independent specification work can continue while environment-dependent acceptance is pending.

## 3. Next engine increments

The proposed sequence grows from the accepted minimal workflow to a useful branching sandbox, then richer reviews. Each increment should include its core contract, real test-host adapter/UI support where needed, and its own acceptance evidence. Extend the F006 graph only for shapes it can represent correctly; retain an explicit unavailable state for unsupported shapes.

| Proposed increment | Dependency and scope | Exit condition |
| --- | --- | --- |
| **A — Typed execution bindings and service tasks**; recommended next specification, candidate F007 | Build on F004/F005. Specify the required scalar start inputs/variables, declared bindings, facts, registered host capabilities/guards and synchronous SERVICE_TASK execution. Include restricted expression support where bindings need it, with explicit validation and execution limits. Split this into smaller features if the contract work warrants it. | A real sandbox workflow performs a declared service action and pauses at a human task or reaches END. Invalid inputs, unavailable capabilities and action/output failures roll back the command; successful replay repeats no business effect. Existing minimal definitions remain supported. |
| **B — Restricted expressions and exclusive routing** | Build on A's typed data and bounded progression. Complete expression semantics, read-only access restrictions, budgets and publication validation; implement EXCLUSIVE_GATEWAY using first-true transition order, a separate default and strict evaluated-error failure. | A sandbox exercises true/false/default/no-match/error and short-circuit paths, with pinned order and routing evidence. Unsafe expressions are rejected and failures roll back. A service action, routing and human completion work together end to end. |
| **C — Structured task submissions** | Extend the F005 submission baseline and pinned human-node contracts under CON-13 / ASM-10. Specify schema profile/bounds, result presence, full-payload fingerprints, authorized exact-task/activity reads and downstream same-transaction access. | Valid structured results are stored once in immutable completion evidence; invalid/unauthorized results fail. Replay and rollback preserve exact submissions; a downstream action can read the final submission before commit. |
| **D — JSON execution variables** | Extend A/B under CON-14 / ASM-11. Specify bounded object/array values, whole-value initialization/replacement, permitted writers, detached projections, expression navigation and write evidence. C is needed only for a scenario that consumes submissions; JSON variables do not depend inherently on task results. | A declared JSON value can be initialized, replaced and read safely under aggregate concurrency, with exact fingerprints and history. No implicit merge or submission-to-variable copying occurs. |
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

**Designer — after successful first APOLO integration:** deliver the standalone Angular authoring application for supported node types, stable task catalog selection, bindings, ordered routes, JSON inspection, backend validation and explicit publication. Define round-trip compatibility, unknown-feature handling, authoring permissions and preview semantics under CON-11. Diagram layout must not alter route priority; the frontend must not execute the workflow engine or SpEL. The F006 inspector does not close this milestone.

**Release hardening — throughout delivery, mandatory before production:** verify compatible published-definition support, migrations/upgrades, authorization, expression safety, shared transactions, concurrency/replay, delivery restart/recovery and retention. Establish operational monitoring, recovery procedures and release ownership for APOLO. Final packaging/release arrangements follow the agreed source integration; production guarantees cannot be postponed until after the designer.

**Exit:** designer acceptance is demonstrated separately from production readiness, and every production deployment has the required host-specific reliability and operational evidence.

## 6. Deferred scope and open scheduling decisions

- The optional business decision engine, decision tables, FIRST/UNIQUE policies and their storage/APIs/editor require a separately selected need. They do not block workflow routing, APOLO integration or the designer.
- Claim/release, candidate groups, general delegation, timers/expiration, ANY/QUORUM, inclusive/parallel gateways, subprocesses and active-instance migration remain deferred. ALL is agreed V1 work, not part of this deferred list.
- Exact future feature boundaries/IDs, delivery dates and assigned owners are not set here. Prioritize A as the next specification and refine its dependencies before implementation.
- APOLO decides business branches, source-move timing and deployment/release details at their stated decision points. Generic engine work may continue independently of unrelated business decisions.

## 7. Keeping the roadmap current

Update this document when a milestone or priority changes, and update the [feature index](features/README.md) when a feature is specified or delivered. Keep detailed tasks and actual verification in the owning feature documents. Do not copy historical status paragraphs into navigation pages.

Use the owner clarification in [AGENTS.md](../AGENTS.md): isolated unit tests are allowed; database integration tests require explicit authorization. Runtime/database checks must be within the task's authorized scope. Documentation-only work uses content, link and whitespace checks, with no build or host startup.

Historical context is retained in the [development discussion](archive/development-discussion.md). This consolidation preserves earlier evidence and source provenance; it supersedes the old development plan's obsolete delivery assumptions, not the governing architecture.
