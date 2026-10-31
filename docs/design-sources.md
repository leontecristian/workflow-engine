# Design sources and decision status

Recorded **2026-10-25** for [constitution v2.2.0](constitution.md).

## Source documents

The active engine architecture, database and workflow definition language designs live in this repository under `docs/architecture/`. Supporting APOLO requirements, reference material and historical discussions remain in the sibling `apolo-docs` repository; links to those documents require the sibling checkout layout. The design relocation preserves source identity, historical commit references and decision precedence.

- [Consolidated design](architecture/workflow-engine-design.md): authoritative detailed architecture and section 22 decision register. The constitution was established from v0.7; v0.8 restores concrete design detail and a complete proposed example without finalizing open contracts.
- [Database design v1.4](architecture/Workflow_Engine_Database_Design_v1.md): persistence design and owner decisions recorded on 24–25 October 2026. Sections 1 and 21 distinguish selected changes from proposed completion rules, physical details and verification gates. The v1.2 baseline was documentation-repository commit `f7e85c9` and v1.3 was `fe73107`; the 2.2.0 amendment incorporates the subsequent owner-selected JSON-variable revision. This records source identity, not a claim that the v1.4 edit is committed or implemented.
- [Workflow definition language design](architecture/workflow-definition-language-design.md): design draft and input for later specification work; exact language contracts remain proposals unless explicitly identified as established decisions.
- [Original-design reconciliation](../../apolo-docs/workflow-engine/architecture/original-design-reconciliation.md): section-by-section account of retained, restored, adapted and deferred original ideas, including the updated five-node workflow example.
- [Approval capability](../../apolo-docs/workflow-engine/requirements/approval-capability.md): capability scope and milestone acceptance expectations.
- [Separate development plan](../../apolo-docs/workflow-engine/development/independent-development-plan.md): specification priorities, two-module delivery and APOLO integration sequence.
- [APOLO technical summary](../../apolo-docs/workflow-engine/reference/apolo-technical-summary.md): platform baseline and effective-build verification obligations.
- [Booking business analysis](../../apolo-docs/workflow-engine/requirements/booking-business-analysis.md): business ownership, confirmed policies and provisional/deferred decisions.
- [Design workspace overview](../../apolo-docs/workflow-engine/README.md): current scope and reading order.

The original 1.0.0 baseline used the working-tree documents available on 24 October 2026. The 2.0.0 amendment incorporated database design v1.2; 2.1.0 added structured submissions from v1.3; 2.2.0 adds JSON execution variables from v1.4. This source record does not approve every proposed contract. Archived discussions explain history and do not override active decisions.

### Scoped precedence for the 2.x amendments

The owner's decisions recorded in database design v1.4 (including retained earlier decisions) supersede older consolidated-design, development-plan and registry examples for these topics:

- Multiple directly assigned tasks per activity are in V1, while the process retains one active position. Detailed SINGLE/ALL completion semantics and fixed-roster rules remain proposals requiring specification.
- A simple retained task catalog replaces task-type business versions, publication lifecycle and outcome tables. Codes are immutable from creation; descriptive metadata and authoring selectability are editable. Published human nodes own outcome/input contracts; activities own the catalog reference and tasks inherit it.
- Hibernate-managed numeric BIGINT/Long JPA versions replace the SQL Server ROWVERSION proposal. The early checked instance write and final managed-version capture are selected approaches whose host behavior still needs O7 verification.
- APOLO stores instance IDs and uses the engine API; host data owns booking membership and historical links. The engine retains optional, immutable, nonunique business_type/business_id correlation, both populated or both null. No host association table is mandated.
- Structured completion results extend scalar-only task inputs. Each accepted result is immutable TASK_COMPLETED evidence with a pinned contract, supported authorized exact-task/activity retrieval, full-payload idempotency, host business validation and operational retention. Same-transaction downstream reads include the final submission. No new task-result table/column, draft form saving or automatic JSON-to-variable merging is selected; exact schema/limits/API shapes remain pending.
- JSON execution variables supplement the six scalar types, superseding the earlier scalar-only variable restriction. Mutable objects/arrays use workflow_variable.json_value with explicit null/type rules, whole-value replacement, declared bindings and bounded read-only path navigation. They do not replace task submissions or APOLO-owned membership. Exact contracts/limits and persistence/expression verification remain pending; indexed JSON search and implicit patch/merge behavior are not selected.

The consolidated design remains the detailed source for unaffected architecture. Older text on the superseded topics must not be used to implement the previous contracts. Dependent specifications must cite this precedence and the database design; unresolved proposals are not finalized by the amendment. Initial schema design changes are not migrations of an applied schema: preserve existing changesets and specify a separate forward migration if needed. APOLO owns migrations for host associations.

The owner's later instructions and completed project changes additionally establish the current local SQL Server connection, Windows authentication setup, Lombok alignment and no-tests development policy. These operational details do not settle the remaining APOLO production integration questions.

## Principle traceability

- **C01 — specification first:** Q19; design sections 20–22; development plan section 3. The repository workflow supplies the document/task/evidence convention for applying this direction.
- **C02 — delivery scope:** Q1, Q4–Q9, F2/F3; design sections 1, 7, 9 and 21; task multiplicity superseded by database design sections 1 and 4.
- **C03 — two modules and boundaries:** F4; design sections 2–4, 8 and 14; technical summary.
- **C04 — eventual APOLO module:** F5/O10; design sections 19.4–19.5; source-integration guidance in the development plan.
- **C05 — migration ownership:** F4/O9; design section 11; approval capability persistence/migration requirements.
- **C06 — command guarantees:** Q11/Q20/O1/O7/O9; design sections 7, 12 and 13; database design sections 9, 14 and 16 for numeric versions, aggregate coordination and host association atomicity.
- **C07 — publication and catalog:** database design sections 8, 10 and 18 supersede task-type versioning/import lifecycle in Q6/Q10/F1/F2/O2 and design sections 9–10; workflow-definition versioning remains.
- **C08 — expressions and strict contracts:** Q9/O6; design sections 5 and 7.3.
- **C09 — business ownership and identity:** Q12/Q17/Q18/O3–O5/O8; design sections 8–9 and 13; booking business analysis; database design sections 9.1 and 16 for host links and optional engine correlation.
- **C10 — compatibility and evidence:** Q2/O7; design sections 4, 14 and 20; technical summary; current owner verification instructions.

The 2.1.0 extension additionally traces C06–C09 to database design sections 8.2, 11.1, 12–16 and 19–21, with CON-13 and ASM-10 covering structured submission contracts and required evidence.

The 2.2.0 extension traces C06/C08/C09 to database design sections 5, 12 and 19–21, with CON-14 and ASM-11 covering JSON execution data, declared access/mutation and required evidence.

## Decisions that remain unresolved

Use the consolidated design's section 22 together with database design sections 1 and 21 and the scoped precedence above. This list preserves status; it does not create a competing set of decisions.

- **O1 — AGREED direction, specification pending:** synchronous service inputs/outputs, explicit service-node actions and bounded automatic execution. Exact bindings, bounds and errors still need contracts.
- **O2 — SELECTED replacement, specification pending:** simple retained task catalog, immutable codes, node-owned contracts and stable-code host capabilities. Catalog administration, any import schema, normalization, technical revision representation and provider interface remain to be specified; task-type business-version lifecycle is superseded.
- **O3 — PROVISIONAL, APOLO:** same-instance adjustment/resubmission with refreshed snapshots and a new activity. Confirm before implementing this business flow.
- **O4/O5 — DEFERRED, APOLO:** alternative resources, excess-allocation calculations, partial confirmation, approver sources and routing. Resolve only the subset needed by the selected integration scenario.
- **O6 — AGREED direction, specification pending:** strict typed workflow/expression contract. Exact encodings, nullability, operator allowlist, limits and error codes remain open specification work.
- **O7 — PENDING VERIFICATION:** APOLO's effective dependencies/compiler configuration, shared persistence/transactions and behavior of the selected numeric JPA version mapping, early instance write and final version capture. Exact wire encoding remains specification work. A successful standalone build does not resolve these.
- **O8 — DEFERRED, APOLO:** material fields, snapshots, country/tenant boundaries and concrete permissions/override rules. High-level authorization and material-change policies are already agreed.
- **O9 — PARTLY AGREED:** single production migration owner is settled; production ordering/settings, durable-handoff adapter, delivery/recovery and retention/redaction details remain APOLO integration work.
- **O10 — PARTLY AGREED:** core's future location inside APOLO is settled; timing, final coordinates, host placement and release/compatibility responsibilities remain open.
- **O11 — OPEN, later milestone:** Angular baseline, graph library and detailed designer interactions.
- **Structured submission contract — SELECTED capability, specification pending:** schema language/features, immutable reference resolution, bounds, exact-value/null/default handling, canonicalization, API/errors and prior-activity bindings. Host review visibility, aggregation and retention/redaction remain explicit integration contracts; a JSON decision document does not determine how reviewed material is preserved.
- **JSON execution data — SELECTED capability, specification pending:** declaration/binding/path syntax, exact encodings and numeric bounds, total instance/read/request/history budgets, null/default/unset semantics, persistence dirty tracking and expression safety. Older scalar contracts remain supported. Storage is not an indexed search contract.

SQL Server `ROWVERSION` is superseded by the selected numeric JPA mapping. Detailed completion policies, unselected physical limits/encodings, interface names, decimal JSON string encodings and the illustrative automatic-step limit of 50 MUST NOT be treated as finalized contracts merely because they appear in a design. [F001](features/F001-initial-database-schema/spec.md) selects the initial nine-table physical contract; its [evidence](features/F001-initial-database-schema/evidence.md) records implementation and local schema verification. Runtime completion algorithms and APIs remain separate specification work.

## Current implementation evidence

At constitution creation, the project has a two-module scaffold, explicit core configuration and host entity/repository registration. It targets Java 17, builds with JDK 21 and currently uses Spring Boot 3.4.5. Core has Lombok 1.18.36 configured, aligned with the supplied APOLO backend POM. These versions describe the current build; they are not permanent constitutional version pins.

The local host uses SQL Server at `localhost:1433`, database `apolo_workflow_dev`, and Windows native authentication with a matching JDBC authentication DLL. A prior startup check observed NTLM-authenticated JDBC sessions and successful startup. Liquibase created its tracking tables; the workflow master was empty. That temporary host process was stopped afterward. These are historical observations, not a live status check.

On 2026-10-25, F001 added the initial core schema changesets. JDK 21 packaging with tests skipped, first host migration/startup, complete SQL Server catalog inspection, tracking checks and a same-artifact second startup passed locally. The two temporary host processes were stopped. See the feature evidence for versions, exact identities/checksums and scope.

On 2026-10-25, [F002](features/F002-task-type-catalog/evidence.md) added catalog Java contracts, JPA mapping, bounded reads and host-transactional versioned mutations. Packaging, discovery, JPA validate startup and unchanged catalog/Liquibase metadata were observed locally; CRUD, races and audit rollback were not executed. Concrete host authorization/audit adapters remain deferred.

F002 revision 2 relocated those types to the agreed layer-first packages without changing behavior. Fresh build/startup/read-only verification passed and is recorded separately from revision 1, with the temporary host stopped and migration metadata unchanged.

F002 revision 3 uses a concrete TaskTypeCatalogService directly, with no mirrored interface/impl pair. Fresh build, CGLIB transaction-proxy metadata, discovery/startup and read-only bookkeeping checks passed; behavioral scenarios remain unexecuted and earlier evidence is retained.

Workflow execution services, runtime idempotency and durable handoff remain unimplemented. Runtime feature guarantees remain to be specified, implemented and evidenced. The earlier 2.x reconciliations themselves changed documentation only; their historical no-execution statements do not describe the later F001/F002 verification.

## Maintaining this record

When a source decision changes, update the relevant constitution principle and affected feature specifications, and record the amendment reason/version. When evidence changes, update its date and scope without rewriting historical observations as if they were current checks. Resolve document conflicts explicitly; retain the distinction between agreed direction, proposed contracts and verified implementation.
