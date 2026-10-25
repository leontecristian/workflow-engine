# Design sources and decision status

Recorded **2026-10-25** for [constitution v2.0.0](constitution.md).

## Source documents

The architecture sources currently live in the sibling documentation repository at `C:\dev\apolo-docs\workflow-engine`. The relative links below work with the current sibling checkout layout. If the documentation or core moves, update links and preserve source identity and decision history.

- [Consolidated design](../../apolo-docs/workflow-engine/architecture/workflow-engine-design.md): authoritative detailed architecture and section 22 decision register. The constitution was established from v0.7; v0.8 restores concrete design detail and a complete proposed example without finalizing open contracts.
- [Database design v1.2](../../apolo-docs/workflow-engine/architecture/Workflow_Engine_Database_Design_v1.md): persistence design and owner decisions recorded on 24–25 October 2026. Sections 1 and 21 distinguish selected changes from proposed completion rules, physical details and verification gates. The source revision reviewed for this amendment is documentation-repository commit `f7e85c9`.
- [Original-design reconciliation](../../apolo-docs/workflow-engine/architecture/original-design-reconciliation.md): section-by-section account of retained, restored, adapted and deferred original ideas, including the updated five-node workflow example.
- [Approval capability](../../apolo-docs/workflow-engine/requirements/approval-capability.md): capability scope and milestone acceptance expectations.
- [Separate development plan](../../apolo-docs/workflow-engine/development/independent-development-plan.md): specification priorities, two-module delivery and APOLO integration sequence.
- [APOLO technical summary](../../apolo-docs/workflow-engine/reference/apolo-technical-summary.md): platform baseline and effective-build verification obligations.
- [Booking business analysis](../../apolo-docs/workflow-engine/requirements/booking-business-analysis.md): business ownership, confirmed policies and provisional/deferred decisions.
- [Design workspace overview](../../apolo-docs/workflow-engine/README.md): current scope and reading order.

The original 1.0.0 baseline used the working-tree documents available on 24 October 2026. The 2.0.0 amendment incorporates database design v1.2 and the owner's subsequent decisions. This source record does not approve every proposed contract. Archived discussions explain history and do not override active decisions.

### Scoped precedence for the 2.0.0 amendment

The owner's decisions recorded in database design v1.2 supersede older consolidated-design, development-plan and registry examples for these topics:

- Multiple directly assigned tasks per activity are in V1, while the process retains one active position. Detailed SINGLE/ALL completion semantics and fixed-roster rules remain proposals requiring specification.
- A simple retained task catalog replaces task-type business versions, publication lifecycle and outcome tables. Codes are immutable from creation; descriptive metadata and authoring selectability are editable. Published human nodes own outcome/input contracts; activities own the catalog reference and tasks inherit it.
- Hibernate-managed numeric BIGINT/Long JPA versions replace the SQL Server ROWVERSION proposal. The early checked instance write and final managed-version capture are selected approaches whose host behavior still needs O7 verification.
- APOLO stores instance IDs and uses the engine API; host data owns booking membership and historical links. The engine retains optional, immutable, nonunique business_type/business_id correlation, both populated or both null. No host association table is mandated.

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

SQL Server `ROWVERSION` is superseded by the selected numeric JPA mapping. Detailed completion policies, unselected physical limits/encodings, interface names, decimal JSON string encodings and the illustrative automatic-step limit of 50 MUST NOT be treated as finalized contracts merely because they appear in a design. The proposed nine-table persistence model still needs its initial-schema feature specification and verification evidence.

## Current implementation evidence

At constitution creation, the project has a two-module scaffold, explicit core configuration and host entity/repository registration. It targets Java 17, builds with JDK 21 and currently uses Spring Boot 3.4.5. Core has Lombok 1.18.36 configured, aligned with the supplied APOLO backend POM. These versions describe the current build; they are not permanent constitutional version pins.

The local host uses SQL Server at `localhost:1433`, database `apolo_workflow_dev`, and Windows native authentication with a matching JDBC authentication DLL. A prior startup check observed NTLM-authenticated JDBC sessions and successful startup. Liquibase created its tracking tables; the workflow master was empty. That temporary host process was stopped afterward. These are historical observations, not a live status check.

Workflow entities, physical schema changesets, execution services, catalog behavior, idempotency and durable handoff are not implemented by this scaffold. Runtime feature guarantees remain to be specified, implemented and evidenced. The 2.0.0 reconciliation changes documentation only; no build, host startup, database access or automated tests were performed for it.

## Maintaining this record

When a source decision changes, update the relevant constitution principle and affected feature specifications, and record the amendment reason/version. When evidence changes, update its date and scope without rewriting historical observations as if they were current checks. Resolve document conflicts explicitly; retain the distinction between agreed direction, proposed contracts and verified implementation.
