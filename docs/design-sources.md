# Design sources and decision status

Recorded **2026-10-24** for [constitution v1.0.0](constitution.md).

## Source documents

The architecture sources currently live in the sibling documentation repository at `C:\dev\apolo-docs\workflow-engine`. The relative links below work with the current sibling checkout layout. If the documentation or core moves, update links and preserve source identity and decision history.

- [Consolidated design v0.7](../../apolo-docs/workflow-engine/architecture/workflow-engine-design.md): authoritative detailed architecture and section 22 decision register.
- [Approval capability](../../apolo-docs/workflow-engine/requirements/approval-capability.md): capability scope and milestone acceptance expectations.
- [Separate development plan](../../apolo-docs/workflow-engine/development/independent-development-plan.md): specification priorities, two-module delivery and APOLO integration sequence.
- [APOLO technical summary](../../apolo-docs/workflow-engine/reference/apolo-technical-summary.md): platform baseline and effective-build verification obligations.
- [Booking business analysis](../../apolo-docs/workflow-engine/requirements/booking-business-analysis.md): business ownership, confirmed policies and provisional/deferred decisions.
- [Design workspace overview](../../apolo-docs/workflow-engine/README.md): current scope and reading order.

This baseline uses the current working-tree versions of those documents, including their pending edits. It is a dated source reference, not a claim that they were committed or that every proposed contract was approved. Archived discussions explain history and do not override active decisions.

The owner's later instructions and completed project changes additionally establish the current local SQL Server connection, Windows authentication setup, Lombok alignment and no-tests development policy. These operational details do not settle the remaining APOLO production integration questions.

## Principle traceability

- **C01 — specification first:** Q19; design sections 20–22; development plan section 3. The repository workflow supplies the document/task/evidence convention for applying this direction.
- **C02 — delivery scope:** Q1, Q4–Q9, F2/F3; design sections 1, 7, 9 and 21.
- **C03 — two modules and boundaries:** F4; design sections 2–4, 8 and 14; technical summary.
- **C04 — eventual APOLO module:** F5/O10; design sections 19.4–19.5; source-integration guidance in the development plan.
- **C05 — migration ownership:** F4/O9; design section 11; approval capability persistence/migration requirements.
- **C06 — command guarantees:** Q11/Q20/O1/O7/O9; design sections 7, 12 and 13.
- **C07 — versioning and registry:** Q6/Q10/F1/F2/O2; design sections 9–10.
- **C08 — expressions and strict contracts:** Q9/O6; design sections 5 and 7.3.
- **C09 — business ownership and identity:** Q12/Q17/Q18/O3–O5/O8; design sections 8–9 and 13; booking business analysis.
- **C10 — compatibility and evidence:** Q2/O7; design sections 4, 14 and 20; technical summary; current owner verification instructions.

## Decisions that remain unresolved

Use the consolidated design's section 22 as the detailed register. This list preserves status; it does not create a competing set of decisions.

- **O1 — AGREED direction, specification pending:** synchronous service inputs/outputs, explicit service-node actions and bounded automatic execution. Exact bindings, bounds and errors still need contracts.
- **O2 — AGREED direction, specification pending:** registry lifecycle/import behavior and deployed-host capabilities. API/schema, normalization, revision representation and provider interface remain to be specified.
- **O3 — PROVISIONAL, APOLO:** same-instance adjustment/resubmission with refreshed snapshots and a new activity. Confirm before implementing this business flow.
- **O4/O5 — DEFERRED, APOLO:** alternative resources, excess-allocation calculations, partial confirmation, approver sources and routing. Resolve only the subset needed by the selected integration scenario.
- **O6 — AGREED direction, specification pending:** strict typed workflow/expression contract. Exact encodings, nullability, operator allowlist, limits and error codes remain open specification work.
- **O7 — PENDING VERIFICATION:** APOLO's effective dependencies/compiler configuration, shared persistence/transactions, flush/locking and concurrency-token mapping. A successful standalone build does not resolve these.
- **O8 — DEFERRED, APOLO:** material fields, snapshots, country/tenant boundaries and concrete permissions/override rules. High-level authorization and material-change policies are already agreed.
- **O9 — PARTLY AGREED:** single production migration owner is settled; production ordering/settings, durable-handoff adapter, delivery/recovery and retention/redaction details remain APOLO integration work.
- **O10 — PARTLY AGREED:** core's future location inside APOLO is settled; timing, final coordinates, host placement and release/compatibility responsibilities remain open.
- **O11 — OPEN, later milestone:** Angular baseline, graph library and detailed designer interactions.

SQL Server `ROWVERSION`, example table/interface names, decimal JSON string encodings and the illustrative automatic-step limit of 50 MUST NOT be treated as finalized contracts merely because they appear in the design.

## Current implementation evidence

At constitution creation, the project has a two-module scaffold, explicit core configuration and host entity/repository registration. It targets Java 17, builds with JDK 21 and currently uses Spring Boot 3.4.5. Core has Lombok 1.18.36 configured, aligned with the supplied APOLO backend POM. These versions describe the current build; they are not permanent constitutional version pins.

The local host uses SQL Server at `localhost:1433`, database `apolo_workflow_dev`, and Windows native authentication with a matching JDBC authentication DLL. A prior startup check observed NTLM-authenticated JDBC sessions and successful startup. Liquibase created its tracking tables; the workflow master was empty. That temporary host process was stopped afterward. These are historical observations, not a live status check.

Workflow entities, physical schema changesets, execution services, registry behavior, idempotency and durable handoff are not implemented by this scaffold. Runtime feature guarantees remain to be specified, implemented and evidenced. No tests were added or run for this constitution.

## Maintaining this record

When a source decision changes, update the relevant constitution principle and affected feature specifications, and record the amendment reason/version. When evidence changes, update its date and scope without rewriting historical observations as if they were current checks. Resolve document conflicts explicitly; retain the distinction between agreed direction, proposed contracts and verified implementation.
