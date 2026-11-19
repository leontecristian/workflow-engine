# Assumptions and verification register

**F026 selection — 2026-11-14:** [Task administration and reassignment](features/F026-task-administration-reassignment/spec.md) is IMPLEMENTED revision 1; the handover records scoped checks. Generic target-change and administration scope is selected; hosts still own task access, destination validity, distinct-person policy and suitability of unchanged frozen inputs. ASM-02/03/04/06/10 integration obligations remain. No directory/membership management, delegation or APOLO policy is inferred.

**F025 selection — 2026-11-14:** [Workflow cancellation](features/F025-workflow-cancellation/spec.md) is IMPLEMENTED revision 1, with scoped evidence in its handover. Generic whole-instance stop, host authorization and shared UI no longer await a scope decision. APOLO withdrawal policy and production transaction/access/delivery verification under ASM-02/03/04/06 remain deferred; partial ALL evidence retention follows ASM-10. This does not select reassignment, compensation or resubmission.

**F022 verification — 2026-11-13:** [Previous-decision comments revision 1](features/F022-previous-decision-comments/implementation.md) records scoped unit, guarded SQL, API and browser evidence for receiving-task authorization, retained entry/completion cutoffs, bounded pagination and corrupt/missing evidence failures. These extend ASM-10 without granting full source-task/history access. APOLO consumption and operational retention remain integration work.

**F021 verification scope — 2026-11-13:** [ALL collective review plan](features/F021-all-collective-review/plan.md) requires fresh ASM-02/03/10/11 evidence for atomic rosters, nonfinal/final receipt states, competing sibling resolution, shared retained input authorization, same-transaction original collections and the enlarged capacity path. Prior SINGLE evidence cannot establish these changes. The [revision 2 handover](features/F021-all-collective-review/implementation.md) records fresh scoped SQL, API, unit and browser evidence for these paths, including maximum-size persistence/returnability and preserving migration 006. This is bounded correctness evidence, not a production load benchmark or heap guarantee. APOLO compatibility remains pending until owner access permits integration work.

**F020 verification scope — 2026-11-10:** [Human-task input bindings](features/F020-human-task-input-bindings/plan.md) relies on ASM-02/10/11. Existing TASK_CREATED history is the versioned snapshot store, with compatible decoding and no migration. The [revision 3 handover](features/F020-human-task-input-bindings/implementation.md) records focused SQL, API, unit and prepared-browser checks of atomic creation, same-command source visibility, immutable reload/replay and receiving-task authorization. Earlier F018/F019 evidence alone did not establish these paths. F021 subsequently added shared ALL snapshot evidence; SINGLE retains the F020 representation.

**Claim/release decisions — 2026-11-09.** [F017](features/F017-task-claim-release/spec.md) is IMPLEMENTED revision 3 with guarded SQL Server and live sandbox acceptance complete. The owner selected per-human-node OPTIONAL (default) or REQUIRED claiming for shared tasks, exclusive claimant completion subject to current eligibility/business access, owner release and host-authorized administrator release, and a separate nullable assignee_ref column preserving the original assignment. Direct USER tasks initialize assignee_ref from assignment_ref and complete without a claim action under either policy. Claim/release actions apply only to GROUP/ROLE tasks. Eligible peers retain claimed-task visibility; current assignees retain discovery/release after membership loss under business access. Own receipt recovery survives membership/admin-privilege loss under business access. Administrative release requires a recorded reason. Constitution 6.2.0 records this additive scope. O01–O06 are closed. Earlier no-claim wording describes the F016 baseline.

**Shared assignment amendment — 2026-11-07:** One SINGLE task has one stable USER, GROUP or ROLE assignment target. Any currently eligible member may complete it without claiming; one completion commits and records the actual actor. Hosts own membership and business authorization. Membership changes do not change the task roster or reopen completed tasks. This was the no-claim F016 baseline; F017 subsequently delivered policy-controlled claiming and release. [F016](features/F016-shared-group-role-task-assignment/spec.md) is IMPLEMENTED revision 2; see its implementation record for scoped verification. Constitution 6.1.0 supersedes earlier direct-user-only assignment and blanket candidate-group deferral for this SINGLE slice. F016 implements the closed assignment, membership, access and preserving-migration contracts; APOLO integration remains separate. Existing user-assignment examples remain valid.

**F009 test-baseline exception — owner decision, 2026-11-06:** [F009 revision 2, D17](features/F009-json-execution-variables/spec.md) removes backward-compatibility obligations for pre-F009 Java APIs, formats and existing test data. Core/test-host callers may change together; old readers, compatibility overloads, dual limit profiles and old-data conversion are unnecessary. The decision retained D15 capacities at that time and permitted fresh test-schema/rebaseline or migration setup instead of mandatory preservation of the pre-F009 test baseline. F021 later superseded those capacities. New executions still require history/evidence, pinned immutable definitions, concurrency, transactions and authorized idempotent replay. This scoped exception supersedes contrary older wording below; it grants no production compatibility waiver. The original amendment authorized documentation only; implementation followed under separate authorization. This historical exception is not continuing authorization for data resets.

**Version:** 6.6.1

**Recorded:** 2026-11-14 (reconciled through F022; no new scope or fresh runtime verification)

**Applies to:** constitution v6.6.1 and planning for APOLO integration

An assumption is an expectation used for planning whose supporting evidence is incomplete. It must not be presented as a confirmed contract or an implemented guarantee. [Constraints](constraints.md) remain requirements even when the feasibility or mechanism for satisfying them needs verification.

The items below derive from the [consolidated design](architecture/workflow-engine-design.md), particularly O3/O7–O11, the development plan now consolidated into the [roadmap](roadmap.md), the [technical summary](integrations/apolo/apolo-technical-summary.md) and the scoped superseding decisions in [database design v1.5](architecture/Workflow_Engine_Database_Design_v1.md). See [design sources](design-sources.md) for precedence. Owners identify responsible roles, not newly assigned individuals or delivery dates.

No item is marked verified merely because it is plausible. Follow the [current verification policy](specification-driven-development.md#verification-and-current-no-tests-policy): isolated unit tests and guarded database integration tests on `localhost:1433/apolo_workflow_test` are authorized; other targets require task-specific authorization. Required evidence is still distinct from a passing test result.

## ASM-01 — A compatible shared dependency baseline can be established

**UI/web adoption and delivery — 2026-11-07:** adoption selected workflow-web and Angular 19 workflow-ui without proving APOLO compatibility. [F012](features/F012-angular-19-frontend-alignment/implementation.md) aligned the console with Angular 19.2; [F013](features/F013-shared-instance-viewer/implementation.md), F014 and F015 subsequently delivered the three-module reactor and shared viewer, administration and runtime libraries. Their scoped evidence includes local packaging, NgModule consumption and sandbox integration. Verify APOLO’s effective framework/Material/compiler/tool versions, routing/theme/localization, HTTP/auth integration and shared transaction participation before accepting that host. Adoption and later extraction do not rewrite F011 evidence.

**Status:** APOLO compatibility PENDING VERIFICATION — O7; scoped standalone/library consumption evidence recorded.

**Owner:** workflow maintainer with APOLO build owner.

**Resolve before:** adopting APOLO's parent or accepting host integration.

The separate core is expected to work with the dependency versions selected by APOLO once the effective builds are aligned. Reference POMs and the standalone build provide starting evidence only. Java 17 compilation and JDK 21 runtime are confirmed constraints; APOLO's exact compiler/plugin settings and effective framework versions still require verification.

**Evidence needed:** effective parent/dependency/plugin configuration for both builds; reconciliation of Spring, Hibernate/JPA, JSON/schema validation, JDBC and Liquibase versions, including F018’s Jackson/NetworkNT baseline; Java API/bytecode target and packaging confirmation; compatibility evidence appropriate to the integration scope.

**If false:** align the build and affected code before integration. Do not add backend as a dependency or treat an incompatible graph as production-ready.

## ASM-02 — Workflow and domain persistence can share the required transaction

**Status:** locally verified for recorded scenarios; APOLO integration PENDING VERIFICATION — O7.

**Owner:** APOLO persistence owner with workflow maintainer.

**Resolve before:** accepting the first APOLO workflow integration; mandatory before production.

The architecture expects workflow JPA repositories and APOLO domain persistence to participate in the same effective transaction and persistence arrangement. Using the same server/database does not prove this.

**Local evidence:** [F009 revision 3](features/F009-json-execution-variables/implementation.md) records guarded rollback of values, participating host effects, task/instance state, history, receipts and outbox. [F018 revision 3](features/F018-structured-task-submissions/implementation.md), [F019 revision 2](features/F019-named-task-output-variables/implementation.md), F020 revision 3 and F021 revision 2 add same-transaction submission/binding/snapshot checks and rollback preserving prior committed contributions. These establish the exercised sandbox boundaries, not APOLO transaction participation or every flush/outer-commit failure path.

**Evidence needed:** datasource/entity-manager/transaction-manager configuration, entity/repository registration retaining APOLO's packages, propagation and flush/commit behavior; evidence of rollback in both directions, including failures at flush or commit. Verify that start can allocate an instance ID and establish required APOLO links before actions consume them, and that rollback/replay preserves the shared transaction and idempotency boundary.

**If false:** correct the integration before enabling business commands. Separate commits or eventual updates to required business state do not satisfy the agreed atomicity constraint.

## ASM-03 — SQL Server concurrency can satisfy the command contract

**Status:** numeric JPA mapping implemented and locally exercised; APOLO concurrency integration PENDING VERIFICATION — O7, database design sections 5, 9 and 16.

**Owner:** workflow persistence maintainer with APOLO database owner.

**Resolve before:** accepting the persistence integration and enabling concurrent commands.

The selected mapping is Hibernate-managed BIGINT lock_version represented by Java Long with JPA @Version; the earlier SQL Server ROWVERSION proposal is superseded. Required coordination has scoped local evidence; APOLO’s effective provider, isolation and concurrent domain-edit behavior remain unverified. For existing instances, the selected approach reserves the first history sequence and flushes the checked parent update before business effects, including commands that otherwise mutate only children.

**Local evidence:** F009 revision 3 records actual final-version capture, overlapping completion/replay and rollback checks; [F017 revision 3](features/F017-task-claim-release/implementation.md) covers ownership commands, and F021 revision 2 covers competing siblings and nonfinal/final receipts. Retain their exact scenarios and limits rather than treating them as proof of every host/provider combination.

**Evidence needed for APOLO integration:** provider initialization/version increments, early checked write and flush timing, isolation/locking behavior, consistent lock ordering, duplicate-command handling and coordination with concurrent domain edits. Verify activity/task/variable-only commands, stale sibling task completions, rollback and capture of the actual final managed version after multiple flushes. Wire encoding must preserve full BIGINT precision. Do not assume one increment or immediate OPTIMISTIC_FORCE_INCREMENT behavior.

**If false:** resolve the integration failure and record any required change to the selected mapping through the design/constitution amendment process. Do not silently remove concurrency protection.

## ASM-04 — The host can provide a durable transactional event handoff

**Status:** core contract and sandbox handoff implemented; APOLO integration mechanism DEFERRED — O9; durability requirement AGREED.

**Owner:** APOLO infrastructure owner with workflow maintainer.

**Resolve before:** production integration relying on workflow events.

The required `WorkflowEventBatchWriter` SPI writes the ordered event batch durably in the existing host transaction. Delivery after commit remains host-owned. [F006’s evidence](features/F006-test-host-definition-console/implementation.md) records sandbox outbox survival across restarts and idempotent inbox delivery. Distributed exactly-once delivery and production recovery remain unverified. APOLO's outbox or publication registry remains to be evaluated.

**Evidence needed:** selected adapter/storage mechanism, transaction participation, event identity/versioning, delivery/recovery ownership, restart durability and idempotent consumption behavior.

**If false:** provide a suitable adapter and durable storage arrangement before deployment. An in-memory event or after-commit callback cannot stand in for the required guarantee.

## ASM-05 — Host capabilities can accurately represent executable support

**Status:** core capability contracts and sandbox providers implemented; APOLO provider/deployment support PENDING VERIFICATION — O2.

**Owner:** workflow contract maintainer with APOLO backend/frontend owners.

**Resolve before:** publishing workflows against that host and deploying changes affecting active tasks.

The design expects the deployed host to supply supported stable task codes and registered extensions, validate support for pinned workflow-node contracts, and retain compatible screens/handlers for published workflows, including future starts. Catalog metadata and selectability do not establish executable support. DefinitionCapabilityProvider and its deployment descriptors already define task-type and SINGLE/ALL assignment support. APOLO still needs its concrete provider, deployed handler/screen mapping and compatibility process; task-type business versions are not part of the model.

**Evidence needed:** APOLO implementation of the existing capability contracts, correspondence with deployed handlers/screens, publication validation and an inventory of task codes/node contracts required by retained published workflow versions and active tasks. Verify that disabling catalog selectability preserves execution of published definitions.

**If false:** reject unsupported publication or deployment and resolve the missing support. Catalog metadata does not create an executable capability or authorize generic completion.

## ASM-06 — APOLO will supply the rules required by the first business slice

**Status:** DEFERRED — APOLO, O5/O8.

**Owner:** APOLO business/product owner with integration maintainer.

**Resolve before:** implementing and accepting the selected booking scenario.

Planning assumes a sufficiently narrow cross-team approval scenario can be agreed, with business validation, approver sources, material fields, required snapshots and concrete access rules supplied by APOLO. These details are not yet available as finalized contracts.

**Evidence needed:** explicit scenario specification and decisions for every business branch it exercises, including no-self-approval enforcement and material-change invalidation.

**If false:** continue independent generic/sandbox work and keep the affected business branch unimplemented. Do not invent a default approver, capacity formula or authorization mapping.

## ASM-07 — Adjustment may reuse the same workflow instance

**Status:** PROVISIONAL — O3.

**Owner:** APOLO business/product owner.

**Resolve before:** implementing adjustment/resubmission behavior.

The tentative preference is to retain the instance, refresh snapshots and create a new review activity on resubmission while preserving history. This is a proposed business flow, not confirmed generic engine behavior.

**Evidence needed:** a recorded decision defining clarification versus invalidation, treatment of earlier approvals, allowed edits and who receives the resubmission.

**If false:** specify the chosen replacement flow and its lifecycle/audit consequences. The confirmed requirement that material changes invalidate review still applies.

## ASM-08 — The local environment remains available for development

**Status:** OBSERVED LOCALLY; revalidation depends on the task.

**Owner:** developer maintaining the workstation.

**Resolve before:** a requested database/runtime verification when the environment may have changed.

Prior startup on 2026-10-24 confirmed JDBC access to `apolo_workflow_dev` at `localhost:1433` with Windows NTLM authentication and the native DLL. Liquibase created tracking tables; no workflow changesets existed. The temporary host was subsequently stopped.

**VERIFIED LOCALLY — 2026-10-25, F001:** JDK 21 packaging, Windows-authenticated database access, initial migration and two packaged-host startups succeeded. Complete schema/tracking inspection and unchanged restart metadata are recorded in [F001 evidence](features/F001-initial-database-schema/implementation.md#4-verification). Both temporary processes were stopped. This revalidates local availability for that run only, not production compatibility or runtime transaction behavior.

Later guarded runs, including F021/F022 on 2026-11-13, record access to `localhost:1433/apolo_workflow_test`, migrations and cleanup for their own fixtures. Those observations establish availability only for the recorded runs, not a live status check.

Planning assumes the instance, Windows permissions, x64 JDK and matching DLL remain available. That historical check does not prove current availability, schema compatibility, production credentials or transaction behavior. Development-only certificate trust must not be treated as production TLS policy.

**Evidence needed:** the current task's relevant configuration and, when authorized, a bounded startup/connection observation. Use the guarded `apolo_workflow_test` target for authorized integration tests; `apolo_workflow_dev` and other targets require task-specific authorization. Stop temporary processes afterward unless asked to leave them running. Documentation review alone requires no startup or connection check.

**If false:** repair the local environment/configuration and record the cause. Do not change production policy or conclude that the workflow architecture is invalid from a workstation failure.

## ASM-09 — The source move can use APOLO's build and release process

**Status:** destination AGREED; execution details OPEN — O10/F5.

**Owner:** project owner with APOLO build/release owner.

**Resolve before:** moving or reparenting core.

The plan expects APOLO's root build and release process to accommodate core as a sibling library module after inherited dependencies/plugins are reviewed. The destination is a decision, not an assumption; its exact integration mechanics remain unresolved.

**Evidence needed:** move timing, final coordinates, root module registration, backend dependency, inherited build behavior, migration-path preservation, maintenance ownership and test-host placement. The adopted web/UI integration also needs agreed source/distribution placement and host consumption wiring; it does not select a separate production workflow server or frontend. Retain one authoritative implementation of each library.

**If false:** resolve the build/release obstacle and revise the move plan. Do not silently turn the current separate checkout into a permanent independent-product requirement.

## ASM-10 — Completion history can support operational result retrieval

**F019 revision 2 verification — 2026-11-10:** explicit service submission inputs resolve a unique completed activity by current instance and pinned node key. Its [handover](features/F019-named-task-output-variables/implementation.md) records focused unit, SQL and browser checks of earlier-node selection, exact original values versus mutable copies, same-transaction visibility and rollback on unavailable sources. F018 readers supplied the foundation; the new resolver required its own scoped evidence.

**Status:** contracts and implementation delivered with scoped local evidence through F022; APOLO integration and operational retention PENDING VERIFICATION. Storage was selected in database design v1.3 and is reconciled in v1.5, sections 11.1, 13.1, 16 and 19.

**Owner:** workflow persistence/contract maintainer with APOLO integration and retention owners.

**Resolve before:** accepting APOLO submission/context integration or changing retention/readers on which operational use depends.

The selected storage is the existing TASK_COMPLETED.details_json record, with exactly one authoritative completion per completed task and supported exact-task/activity APIs. Implemented bounded reads include final-submission visibility in the same transaction, without another result table or JSON column.

**Local evidence:** F018 revision 3 records schema/business rejection, exact submission reads, scale-sensitive replay and downstream rollback. F019 revision 2 covers original-result bindings and mutable copies; F020 revision 3 covers immutable receiving-task snapshots. F021 revision 2 covers shared ALL snapshots, resolved original collections and rollback preserving prior contributions. F022 revision 1 covers receiving-task-authorized previous decisions, retained entry/completion cutoffs, bounded queries/pages and integrity failures. These records distinguish isolated, SQL, API and browser observations; none establishes APOLO production retention policy.

**Remaining evidence:** validate APOLO’s business/access policy, transaction participation, deployment compatibility and operational query/load behavior against the delivered contracts. Reuse applicable local evidence for embedded schema validation, supported local references, exact canonicalization, uniqueness, bounded reads and rollback; external/versioned schema resolution is not implemented. Define retention/archival/redaction safeguards for submissions, input snapshots and retained entry/completion facts while operational reads depend on them, including completed-task reads. Preserve supported older no-result evidence and published-definition handlers/readers. Missing evidence must not masquerade as an empty decision or context.

**If false:** revise the storage/access or retention mechanism explicitly while preserving immutable submissions, transaction guarantees and host ownership; do not silently drop evidence, create independently editable copies or expose arbitrary history as the API.

## ASM-11 — JSON variables preserve persistence and expression guarantees

**F019 verification scope — 2026-11-10:** [named outputs](features/F019-named-task-output-variables/spec.md) select an additional pinned producer with no pre-completion row. The [revision 2 handover](features/F019-named-task-output-variables/implementation.md) records focused checks for output insertion/replacement, original versus mutable values, same-transaction visibility, absence/existence, collisions and rollback/replay. F009/F018 evidence alone did not establish the new paths. No new table or migration was needed for F019; F021 later extended output creation to resolved ALL collections.

**Status:** JSON contracts implemented with scoped automated and browser evidence through F021; APOLO integration and production capacity/load acceptance PENDING VERIFICATION — database design v1.5, section 12. F009’s [handover](features/F009-json-execution-variables/implementation.md) includes later Chrome follow-ups; its original browser NOT RUN entry is historical. Download verification remains inconclusive, and the follow-up did not rerun every invalid-index, replay, concurrency or capacity scenario in Chrome.

**Owner:** workflow persistence/expression maintainers with APOLO integration owner.

**Resolve before:** accepting APOLO JSON-variable/routing integration or a relevant provider, codec, expression or capacity change.

The selected model adds JSON objects/arrays in workflow_variable.json_value while preserving scalar storage and immutable task results. Whole-value updates, declared bindings and restricted read-only navigation must preserve existing concurrency, atomicity, authorization and exact-value guarantees.

**Local evidence:** F009 revision 3 records SQL null/replacement behavior, exact numeric round trips, full-value replay identity, stale/overlapping commands, rollback and immutable write/routing evidence, plus bounded detached navigation and scalar regressions. F019 adds named outputs and existence evidence. F021 revision 2 exercises 8 MiB values/collections, 10 MiB maps, 24 MiB history records, maximum-size persistence/returnability, shared snapshots and preserving migration 006; these supersede F009 D15’s earlier capacities.

**Remaining evidence:** validate APOLO’s provider/security/transaction arrangement, production memory/load and transport budgets, operational retention and coordinated deployment against the current [capacity contract](features/F021-all-collective-review/spec.md#c01--capacity-contract). Reuse applicable feature evidence; a capacity correctness test is not a load benchmark or heap guarantee. F009 specifies codecs, explicit replacement and detached allowlisted object/index access, including missing-property projection to null, uninitialized errors and bounded null/type handling; field schemas for ordinary execution JSON remain deferred. Preserve current scalar behavior and supported evidence readers. The historical D17 exception does not waive later compatibility requirements. No host data or mutable provider object may escape into expression evaluation.

**If false:** revise the mapping/access mechanism and record contract changes before enabling affected definitions; do not silently accept lossy values, unsafe navigation, lost updates or missing evidence.

## Open decisions that must not be filled with assumptions

The following separates settled delivery contracts from remaining decisions; historical planning gaps do not reopen the implemented slices.

- **O1/O6:** F007–F009 specify and implement supported node/action bindings, exact scalar/JSON encodings, initialization/read rules, restricted navigation and errors. The 50-automatic-step counting rules are settled; F021 owns current coordinated capacities. Future unsupported bindings and APOLO integration/load evidence remain separate work.
- **Ordered routing:** first-match transition-array order, short-circuit behavior, default/error handling and publication diagnostics are specified and implemented. Designer preservation and optional unreachable-route warnings remain later authoring work; complete static overlap detection is not assumed.
- **O2:** catalog administration, technical revision tokens and capability-provider contracts are specified and implemented. Additional import/normalization contracts and APOLO deployment compatibility remain separate work. Task-type publication/business-version lifecycles remain superseded.
- **Task multiplicity:** SINGLE, USER/GROUP/ROLE targets, F017 claims and F021 fixed 1–100 ALL rosters have contracts and scoped runtime evidence. Preserve early-resolution sibling cancellation, prior submissions, concurrency and atomic rollback. General reassignment/cancellation, loops, mutable rosters and advanced policies remain deferred; APOLO assignment and distinct-person rules are host decisions.
- **Submission contracts:** F018 defines node-wide FORBIDDEN/OPTIONAL/REQUIRED result contracts and embedded schemas. F020 incoming bindings replace human simple-input contracts in new authoring; legacy published empty arrays and explicit start input contracts remain supported. Nonempty scalar submission contracts remain unsupported. Further authoring/form extensions require separate specification.
- **Host associations:** APOLO ownership, instance-ID queries and optional engine correlation are selected. APOLO must specify its column/table shape, historical membership storage, authorization and start-time integration without making core depend on booking tables.
- **Structured submissions:** F018–F022 settle supported result schemas, limits, exact-value/canonicalization rules, APIs/errors, original-submission bindings, retained inputs and previous-decision context. APOLO business validation/aggregation, operational retention and integration policy remain to be specified without treating JSON as proof of membership or reviewed material. External schema references, draft/partial saving and ordinary accepted-result edits remain outside delivered scope.
- **JSON variables:** F009 implements schema-free bounded objects/arrays, declared start/service bindings, exact codecs, read-only expression navigation and whole-value replacement; F019/F021 add pinned human outputs and current capacities. Per-field execution-data schemas remain deferred under the 2026-11-06 owner decision. Standalone variable-update commands, arbitrary API path projections, JSON-path search/indexing, patch/merge APIs and arbitrary Java-object serialization are not implied.
- **O4:** alternative-resource acceptance/reapproval behavior. No default replacement behavior has been selected.
- **O9:** deployment-specific migration ordering, delivery/recovery and retention/redaction arrangements beyond the agreed ownership boundary.
- **O11:** Angular 19.2 alignment and shared-library extraction are delivered. Detailed designer interactions and any further designer graph-library selection remain future work; they do not reopen the implemented read-only viewer or block the first APOLO slice.
- Physical table names, schema naming, interface names and API paths fixed by delivered specifications are contracts. Remaining illustrative examples are proposals until specified.

## Maintaining assumptions

Feature specifications and plans should cite the ASM identifiers they depend on. At the stated decision point, record the evidence, date, relevant environment/revision and resulting status: VERIFIED for the demonstrated scope, REJECTED or SUPERSEDED. A local verification must not be generalized to APOLO production without evidence.

Apply the [focused verification and evidence-reuse rules](specification-driven-development.md#focused-verification-and-evidence-reuse): cite the source/revision and applicability of prior evidence, label it REUSED rather than a fresh PASS, and select fresh checks only for relevant changes, concrete risks or an explicitly selected integration gate. Preserve historical failures and NOT RUN outcomes alongside later scoped evidence. They neither disappear nor automatically become the next feature’s acceptance checklist. Stop when the selected scope and relevant checks pass.

If an assumption fails, update affected specifications/tasks and explain the impact. Changes to confirmed principles still follow the [constitution's amendment rules](constitution.md#governance-and-amendments). Keep required but unperformed checks visible under the current verification policy.
