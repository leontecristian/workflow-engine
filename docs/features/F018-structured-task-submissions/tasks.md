# Tasks — F018

Specification revision 3 governs implementation. See the [handover](implementation.md) for fresh verification and open gates.

- [x] T01 — Inspect current contracts/source and prepare the four records, recording owner UI/validation scope choices and open decisions (P01).
- [x] T02 — Close O01–O05 and C01–C06, including version-specific NetworkNT source/API feasibility, exact codec/wire/evidence decisions and the amount/note example; reconcile READY revision 3 (P01; R01–R07). Provider behavior is not claimed tested; runtime verification belongs to T03–T06.
- [x] T03 — NetworkNT and Jackson upgrades, loader migration, pinned schemas and exact codec implemented. AC01–02 and focused dependency/codec checks passed.
- [x] T04 — Completion validation, evidence, receipt compatibility, reads and service context implemented. Isolated checks and guarded SQL Server acceptance passed.
- [x] T05 — Shared web/UI and sandbox example implemented. Authorization, frontend unit checks, mock recovery after a dropped response and live sandbox browser acceptance passed.
- [x] T06 — Focused review, production package and dependency checks, advisory scan, guarded SQL Server and live browser verification, and fixture/process cleanup completed.
