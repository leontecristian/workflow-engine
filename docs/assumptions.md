# Assumptions and verification register

**Version:** 1.0.0

**Recorded:** 2026-10-24

**Applies to:** constitution v1.0.0 and planning for APOLO integration

An assumption is an expectation used for planning whose supporting evidence is incomplete. It must not be presented as a confirmed contract or an implemented guarantee. [Constraints](constraints.md) remain requirements even when the feasibility or mechanism for satisfying them needs verification.

The items below derive from the [consolidated design](../../apolo-docs/workflow-engine/architecture/workflow-engine-design.md), particularly O3/O7–O11, the [development plan](../../apolo-docs/workflow-engine/development/independent-development-plan.md) and the [technical summary](../../apolo-docs/workflow-engine/reference/apolo-technical-summary.md). Owners identify responsible roles, not newly assigned individuals or delivery dates.

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

**Evidence needed:** datasource/entity-manager/transaction-manager configuration, entity/repository registration retaining APOLO's packages, propagation and flush/commit behavior; evidence of rollback in both directions, including failures at flush or commit.

**If false:** correct the integration before enabling business commands. Separate commits or eventual updates to required business state do not satisfy the agreed atomicity constraint.

## ASM-03 — SQL Server concurrency can satisfy the command contract

**Status:** PENDING VERIFICATION; mapping PROPOSED — O7, design section 12.3.

**Owner:** workflow persistence maintainer with APOLO database owner.

**Resolve before:** finalizing the concurrency mapping and enabling concurrent commands.

SQL Server with JPA is expected to support the required optimistic concurrency and instance-level serialization. `ROWVERSION` is one proposed mapping; it has not been selected as the final token contract.

**Evidence needed:** chosen token mapping, generated-value refresh, isolation/locking behavior, consistent lock ordering, duplicate-command handling and coordination with concurrent domain edits. Task/variable updates and command receipts must be covered by the same policy.

**If false:** select and specify a supported alternative mapping/locking approach that preserves the guarantee. Do not silently remove concurrency protection.

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

The design expects the deployed host to supply supported task-type versions, outcomes and registered extensions and to retain compatible screens/handlers for active tasks. A concrete capability interface and deployment compatibility process are still needed.

**Evidence needed:** capability-provider contract, correspondence with deployed handlers/screens, publication validation and an inventory of versions required by active tasks.

**If false:** reject unsupported publication or deployment and resolve the missing support. Imported registry metadata does not create an executable capability or authorize generic completion.

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

## Open decisions that must not be filled with assumptions

- **O1/O6:** exact node/action bindings, JSON encodings, nullability, precision, date/time semantics, expression allowlist, execution bounds and errors. The illustrative limit of 50 and decimal-as-string examples are not final selections.
- **O2:** import normalization, revision-token representation and API/provider schemas. A checksum convention is not established merely by mentioning checksums.
- **O4:** alternative-resource acceptance/reapproval behavior. No default replacement behavior has been selected.
- **O9:** deployment-specific migration ordering, delivery/recovery and retention/redaction arrangements beyond the agreed ownership boundary.
- **O11:** Angular version, graph library and detailed designer interactions. These do not block the first engine slice.
- Physical table names, schema naming, interface names and API paths presented as examples remain proposed until the relevant specification fixes them.

## Maintaining assumptions

Feature specifications and plans should cite the ASM identifiers they depend on. At the stated decision point, record the evidence, date, relevant environment/revision and resulting status: VERIFIED for the demonstrated scope, REJECTED or SUPERSEDED. A local verification must not be generalized to APOLO production without evidence.

If an assumption fails, update affected specifications/tasks and explain the impact. Changes to confirmed principles still follow the [constitution's amendment rules](constitution.md#governance-and-amendments). Keep required but unperformed checks visible under the current no-tests instruction.
