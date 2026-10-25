# Assumptions and verification register

**Version:** 2.2.0

**Recorded:** 2026-10-25

**Applies to:** constitution v2.2.0 and planning for APOLO integration

An assumption is an expectation used for planning whose supporting evidence is incomplete. It must not be presented as a confirmed contract or an implemented guarantee. [Constraints](constraints.md) remain requirements even when the feasibility or mechanism for satisfying them needs verification.

The items below derive from the [consolidated design](../../apolo-docs/workflow-engine/architecture/workflow-engine-design.md), particularly O3/O7–O11, the [development plan](../../apolo-docs/workflow-engine/development/independent-development-plan.md), the [technical summary](../../apolo-docs/workflow-engine/reference/apolo-technical-summary.md) and the scoped superseding decisions in [database design v1.4](../../apolo-docs/workflow-engine/architecture/Workflow_Engine_Database_Design_v1.md). See [design sources](design-sources.md) for precedence. Owners identify responsible roles, not newly assigned individuals or delivery dates.

No item is marked verified merely because it is plausible. Follow the [current no-tests policy](specification-driven-development.md#verification-and-current-no-tests-policy); required evidence listed here is a future verification obligation, not authorization to execute tests now.

## ASM-01 — A compatible shared dependency baseline can be established

**Status:** PENDING VERIFICATION — O7.

**Owner:** workflow maintainer with APOLO build owner.

**Resolve before:** adopting APOLO's parent or accepting host integration.

The separate core is expected to work with the dependency versions selected by APOLO once the effective builds are aligned. Reference POMs and the standalone build provide starting evidence only. Java 17 compilation and JDK 21 runtime are confirmed constraints; APOLO's exact compiler/plugin settings and effective framework versions still require verification.

**Evidence needed:** effective parent/dependency/plugin configuration for both builds; reconciliation of Spring, Hibernate/JPA, JSON, JDBC and Liquibase versions; Java API/bytecode target and packaging confirmation; compatibility evidence appropriate to the integration scope.

**If false:** align the build and affected code before integration. Do not add backend as a dependency or treat an incompatible graph as production-ready.

## ASM-02 — Workflow and domain persistence can share the required transaction

**Status:** PENDING VERIFICATION — O7.

**Owner:** APOLO persistence owner with workflow maintainer.

**Resolve before:** accepting the first APOLO workflow integration; mandatory before production.

The architecture expects workflow JPA repositories and APOLO domain persistence to participate in the same effective transaction and persistence arrangement. Using the same server/database does not prove this.

**Evidence needed:** datasource/entity-manager/transaction-manager configuration, entity/repository registration retaining APOLO's packages, propagation and flush/commit behavior; evidence of rollback in both directions, including failures at flush or commit. Verify that start can allocate an instance ID and establish required APOLO links before actions consume them, and that rollback/replay preserves the shared transaction and idempotency boundary.

**If false:** correct the integration before enabling business commands. Separate commits or eventual updates to required business state do not satisfy the agreed atomicity constraint.

## ASM-03 — SQL Server concurrency can satisfy the command contract

**Status:** PENDING VERIFICATION; numeric JPA mapping SELECTED — O7, database design sections 5, 9 and 16.

**Owner:** workflow persistence maintainer with APOLO database owner.

**Resolve before:** accepting the persistence integration and enabling concurrent commands.

The selected mapping is Hibernate-managed BIGINT lock_version represented by Java Long with JPA @Version; the earlier SQL Server ROWVERSION proposal is superseded. Its ability to provide the required instance-level coordination in the actual host remains unverified. For existing instances, the selected approach reserves the first history sequence and flushes the checked parent update before business effects, including commands that otherwise mutate only children.

**Evidence needed:** provider initialization/version increments, early checked write and flush timing, isolation/locking behavior, consistent lock ordering, duplicate-command handling and coordination with concurrent domain edits. Verify activity/task/variable-only commands, stale sibling task completions, rollback and capture of the actual final managed version after multiple flushes. Wire encoding must preserve full BIGINT precision. Do not assume one increment or immediate OPTIMISTIC_FORCE_INCREMENT behavior.

**If false:** resolve the integration failure and record any required change to the selected mapping through the design/constitution amendment process. Do not silently remove concurrency protection.

## ASM-04 — The host can provide a durable transactional event handoff

**Status:** DEFERRED integration mechanism — O9; durability requirement AGREED.

**Owner:** APOLO infrastructure owner with workflow maintainer.

**Resolve before:** production integration relying on workflow events.

The planned neutral event-recorder contract is expected to connect to host infrastructure capable of storing a handoff in the command transaction and delivering after commit. The existence and suitability of an APOLO outbox/publication registry have not been established.

**Evidence needed:** selected adapter/storage mechanism, transaction participation, event identity/versioning, delivery/recovery ownership, restart durability and idempotent consumption behavior.

**If false:** provide a suitable adapter and durable storage arrangement before deployment. An in-memory event or after-commit callback cannot stand in for the required guarantee.

## ASM-05 — Host capabilities can accurately represent executable support

**Status:** contract direction AGREED; concrete provider/support pending — O2.

**Owner:** workflow contract maintainer with APOLO backend/frontend owners.

**Resolve before:** publishing workflows against that host and deploying changes affecting active tasks.

The design expects the deployed host to supply supported stable task codes and registered extensions, validate support for pinned workflow-node contracts, and retain compatible screens/handlers for published workflows, including future starts. Catalog metadata and selectability do not establish executable support. A concrete capability interface and deployment compatibility process are still needed; task-type business versions are no longer part of the model.

**Evidence needed:** capability-provider contract, correspondence with deployed handlers/screens, publication validation and an inventory of task codes/node contracts required by retained published workflow versions and active tasks. Verify that disabling catalog selectability preserves execution of published definitions.

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

**VERIFIED LOCALLY — 2026-10-25, F001:** JDK 21 packaging, Windows-authenticated database access, initial migration and two packaged-host startups succeeded. Complete schema/tracking inspection and unchanged restart metadata are recorded in [F001 evidence](features/F001-initial-database-schema/evidence.md). Both temporary processes were stopped. This revalidates local availability for that run only, not production compatibility or runtime transaction behavior.

Planning assumes the instance, Windows permissions, x64 JDK and matching DLL remain available. That historical check does not prove current availability, schema compatibility, production credentials or transaction behavior. Development-only certificate trust must not be treated as production TLS policy.

**Evidence needed:** the current task's relevant configuration and, when authorized, a bounded startup/connection observation. Use the configured development database and stop temporary processes afterward.

**If false:** repair the local environment/configuration and record the cause. Do not change production policy or conclude that the workflow architecture is invalid from a workstation failure.

## ASM-09 — The source move can use APOLO's build and release process

**Status:** destination AGREED; execution details OPEN — O10/F5.

**Owner:** project owner with APOLO build/release owner.

**Resolve before:** moving or reparenting core.

The plan expects APOLO's root build and release process to accommodate core as a sibling library module after inherited dependencies/plugins are reviewed. The destination is a decision, not an assumption; its exact integration mechanics remain unresolved.

**Evidence needed:** move timing, final coordinates, root module registration, backend dependency, inherited build behavior, migration-path preservation, maintenance ownership and test-host placement. Retain a single authoritative core implementation.

**If false:** resolve the build/release obstacle and revise the move plan. Do not silently turn the current separate checkout into a permanent independent-product requirement.

## ASM-10 — Completion history can support operational result retrieval

**Status:** storage direction SELECTED; contract and integration PENDING VERIFICATION — introduced in database design v1.3 and retained in v1.4, sections 11.1, 13.1, 16 and 19.

**Owner:** workflow persistence/contract maintainer with APOLO integration and retention owners.

**Resolve before:** implementing structured completion and accepting downstream use of task submissions.

The selected storage is the existing TASK_COMPLETED.details_json record, with exactly one authoritative completion per completed task and supported exact-task/activity APIs. It is expected to support bounded result reads, including the final submission inside the same transaction, without another table or JSON column.

**Evidence needed:** pinned schema/reference resolution, bounded validation, canonical request handling preserving exact values, completion uniqueness and lifecycle consistency, query/index behavior, authorized sibling-result access, same-transaction visibility and rollback. Define retention/archival/redaction safeguards for operational dependencies and retained evidence, and codec compatibility for older valid no-result completions. Verify published workflows retain compatible host handlers and readers.

**If false:** revise the storage/access or retention mechanism explicitly while preserving immutable submissions, transaction guarantees and host ownership; do not silently drop evidence, create independently editable copies or expose arbitrary history as the API.

## ASM-11 — JSON variables preserve persistence and expression guarantees

**Status:** capability SELECTED; contracts and integration PENDING VERIFICATION — database design v1.4, section 12.

**Owner:** workflow persistence/expression maintainers with APOLO integration owner.

**Resolve before:** accepting JSON variable writes, enabling JSON-aware definitions or executing path-based routing.

The selected model adds JSON objects/arrays in workflow_variable.json_value while preserving scalar storage and immutable task results. Whole-value updates, declared bindings and restricted read-only navigation must preserve existing concurrency, atomicity, authorization and exact-value guarantees.

**Evidence needed:** SQL CHECK and null behavior, explicit JPA replacement/dirty tracking, stale-write conflicts, transaction-local visibility, rollback, bounded schema validation, exact numeric round trips and full-payload fingerprints. Specify and verify detached projections and the expression allowlist for object/index access, required/missing/null/type errors and traversal limits. Reconcile per-instance/value budgets with command/read/history envelopes; verify immutable write/routing evidence and backward-compatible scalar codecs. No host data or mutable provider object may escape into expression evaluation.

**If false:** revise the mapping/access mechanism and record contract changes before enabling affected definitions; do not silently accept lossy values, unsafe navigation, lost updates or missing evidence.

## Open decisions that must not be filled with assumptions

- **O1/O6:** exact node/action bindings, JSON encodings, nullability, precision, date/time semantics, expression allowlist, execution bounds and errors. The illustrative limit of 50 and decimal-as-string examples are not final selections.
- **O2:** simple catalog administration, any import normalization, technical revision-token representation and API/provider schemas. The former task-type publication/import lifecycle is superseded; a checksum convention is not established merely by mentioning checksums.
- **Task multiplicity:** multiple tasks per activity is agreed; proposed SINGLE/ALL semantics, fixed roster, bounds and exact node JSON require specification before dependent implementation. Do not promote them to confirmed APOLO business policy.
- **Host associations:** APOLO ownership, instance-ID queries and optional engine correlation are selected. APOLO must specify its column/table shape, historical membership storage, authorization and start-time integration without making core depend on booking tables.
- **Structured submissions:** result contract language/features and immutable reference resolution, payload/depth/collection/string limits, number and omitted/null/default rules, versioned canonicalization, public API/error shapes, bounded retrieval and prior-activity bindings remain specification gates. Define host business validation/aggregation and reviewer visibility without treating JSON as proof of membership or reviewed material. Draft/partial task saving is excluded.
- **JSON variables:** exact declaration/schema, start/service/update bindings, authorized read projections, expression path syntax, full-value encoding/canonicalization, unset behavior and limits remain specification work. Whole-value replacement, SQL NULL for variable null and object/array roots are the selected initial shape. JSON-path search/indexing, patch/merge APIs and arbitrary Java-object serialization are not implied.
- **O4:** alternative-resource acceptance/reapproval behavior. No default replacement behavior has been selected.
- **O9:** deployment-specific migration ordering, delivery/recovery and retention/redaction arrangements beyond the agreed ownership boundary.
- **O11:** Angular version, graph library and detailed designer interactions. These do not block the first engine slice.
- Physical table names, schema naming, interface names and API paths presented as examples remain proposed until the relevant specification fixes them.

## Maintaining assumptions

Feature specifications and plans should cite the ASM identifiers they depend on. At the stated decision point, record the evidence, date, relevant environment/revision and resulting status: VERIFIED for the demonstrated scope, REJECTED or SUPERSEDED. A local verification must not be generalized to APOLO production without evidence.

If an assumption fails, update affected specifications/tasks and explain the impact. Changes to confirmed principles still follow the [constitution's amendment rules](constitution.md#governance-and-amendments). Keep required but unperformed checks visible under the current no-tests instruction.
