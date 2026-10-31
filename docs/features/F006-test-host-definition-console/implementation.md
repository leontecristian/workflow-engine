# Implementation — F006 Test-host definition console

## 1. Implementation Summary

**NOT IMPLEMENTED — documentation prepared 2026-10-31.** [Specification revision 1](spec.md) is READY for a subsequent implementation task. All implementation tasks in [tasks.md](tasks.md) are unchecked. The requested outcome is a host-owned Angular/HTTP console for manual definition/catalog verification; F005 runtime execution remains separate.

Preparation baseline: clean working tree at `34929ae` (`feat(definition): implement F004 management and minimal SINGLE validation`). Inspected the committed F004 contracts/source, test-host configuration and migration ownership. No endpoint, frontend, migration, credential, fixture or core read method has been implemented in this preparation.

## 2. Components and Files Changed

Added the four F006 process documents and updated project/feature navigation and design-source sequencing. No application/configuration/build file changed. The planned core family listing, host audit schema, transport adapters and Angular application are described in [plan.md](plan.md), not claimed as existing code.

## 3. Technical Decisions and Deviations

F006 preserves the reserved F005 execution identifier. The console uses authenticated local access, raw document text, string-encoded Long values, host transactions and durable host audit. Capability declarations are an explicitly labelled finite validation test double, not proof of executable workflow support. Manual verification is selected; the no-automated-tests instruction remains in force. Toolchain versions will be pinned during implementation from official compatibility sources.

## 4. Verification

- **PASS — preparation source review:** confirmed two modules, existing F002/F004 public DTO/service boundaries, absence of a family-list service, raw-string document API and host-owned Liquibase master. Identified durable audit and trusted context as required host responsibilities.
- **PASS — documentation checks:** all 90 relative file-link destinations across the four feature records and four navigation documents exist; the feature contains exactly four process files. Reviewed 10 requirements, 10 implementation tasks and 12 acceptance scenarios for traceability. Trailing-whitespace scans and `git diff --check` passed. No separate evidence/verification file was added.
- **NOT RUN — builds:** documentation-only preparation; no Maven/npm build or dependency installation.
- **NOT RUN — AC01–AC12 and null-name provider-error reproduction:** implementation does not yet exist. No host startup, database access/write, manual browser scenario or automated test occurred. Existing F004 evidence remains historical and is not evidence for these new endpoints/pages.

No temporary process was started. Changes are unstaged/uncommitted; nothing was pushed.

## 5. Known Limitations

The UI/API/audit migration/fixture provider and family listing are planned only. Manual console verification will cover selected F002/F004 behaviors; it cannot establish production authentication, true concurrent races, F005 execution or real host capability compatibility. The null-constraint-name regression needs separate controlled observation if feasible.

## 6. Remaining Work

Implement T01–T08, perform and record T09 manual acceptance, and complete T10 handover. Preserve the spec's authority when resolving discoveries. Record the actual frontend/backend toolchain, migrations, retained development records, verification outcomes and any unfinished scenarios here as implementation proceeds.
