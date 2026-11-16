# F028 tasks

[Specification revision 2](spec.md), [plan](plan.md). Planning only; implementation is not authorized.

- [x] T01 — Inspect completion/replay, host orchestration, submission validator and governing host transaction/approval rules.
- [x] T02 — Prepare separate specification, implementation approach, focused verification and handover; leave F027 unchanged.
- [x] T03a — Close D01: owner requires both adapters for completion hosts, with no implicit fallback and fresh validation excluded from replay (2026-11-16; R06).
- [ ] T03 — Finalize public context/error contracts and mark READY when applicable, applying the agreed D01 registration rule (P01, R05/R06).
- [ ] T04 — Implement core access-before-replay and fresh authorization/general validation ordering, preserving result-specific validation and existing receipt semantics (P01/P02, AC01–AC04/AC06).
- [ ] T05 — Migrate sandbox/direct callers and scoped web errors; preserve authoritative replay access and transaction coordination (P03/P04, AC03/AC06).
- [ ] T06 — Add/select focused unit and guarded SQL checks, including rollback and business-edit race; record actual evidence separately from reused results (P05, AC01–AC06).
- [ ] T07 — Reconcile integration/source-precedence documentation after adoption, review final diff and complete handover with remaining APOLO gaps (P04/P05).

T04–T07 require a subsequent implementation request. No F027 work, production access or broader release matrix is implied.
