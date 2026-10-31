# Implementation — F005 Minimal SINGLE workflow execution

## 1. Implementation Summary

**NOT IMPLEMENTED — documentation prepared 2026-10-31.** [Specification revision 1](spec.md) defines the first executable `START → HUMAN_TASK (SINGLE) → END` slice. All [implementation tasks](tasks.md) are pending. Current code provides F003 runtime mappings/reads and F004 definition management, not these execution commands.

Preparation baseline: HEAD `34929ae` (`feat(definition): implement F004 management and minimal SINGLE validation`), with the previously prepared F006 documents/navigation uncommitted. Those changes are preserved. No implementation, build, host startup, database write or automated test was performed during F005 preparation.

## 2. Components and Files Changed

Added F005's four process documents and reconciled feature/project navigation and design-source sequencing. F006 remains a separate planned management console. No application source, migration, dependency or runtime configuration changed.

## 3. Technical Decisions and Deviations

Specified exact-version start, one fixed direct assignment, sole-outcome completion, early instance claim, immutable history/submission, receipt-first replay and durable host event recording. Optional start-link callback runs after ID allocation and before assignment, with the host association intent included in request identity. Runtime ignores authoring selectability and retains retired pinned definitions. Existing F001 tables are sufficient; no migration is planned.

Host authorization, actual assignment implementations and durable recorder storage remain integration responsibilities. F006's finite validation fixture is explicitly insufficient for runtime support. No code exists for these choices yet, and no implementation deviation has occurred.

## 4. Verification

- **PASS — preparation source review:** inspected F001 lifecycle/JSON/receipt constraints, F003 runtime entities/repositories, F004 typed minimal model and capability/validation separation, and current host boundaries. Selected numeric JPA concurrency supersedes the older ROWVERSION design sketch.
- **PASS — documentation checks:** all 113 relative file-link destinations across F005/F006 and the four navigation documents exist. Both feature folders contain exactly four process files. Reviewed 12 requirements, 12 tasks and 14 acceptance scenarios for traceability; trailing-whitespace scans and `git diff --check` passed. No separate evidence file was added.
- **NOT RUN — build and AC01–AC14:** documentation-only work. No runtime command, transaction, race, replay, resolver, event recording or read scenario was executed. No automated tests were created/run.

No temporary process was started. Changes are unstaged/uncommitted; nothing was pushed.

## 5. Known Limitations

F005 has no implementation yet. Real host adapters and an authorized verification environment are required for behavioral evidence. F006's definition management console does not expose F005 commands. Packaging/static inspection will not establish rollback, concurrency, durable delivery/recovery or production authorization. Minimal graph acceptance cannot demonstrate a 50/51-step execution boundary.

## 6. Remaining Work

Implement T01–T10, establish T11's actual manual acceptance with suitable real adapters, and complete T12 handover. Record implementation revisions, callback/transaction/lock evidence, commands, outcomes, retained data and unresolved gaps here. Update the specification and reconcile plan/tasks before implementing any discovered contract change.
