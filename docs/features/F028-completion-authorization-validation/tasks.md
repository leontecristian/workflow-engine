# F028 tasks

[Specification revision 3](spec.md), [plan](plan.md). Owner authorized revision 3 implementation and focused verification on 2026-11-16.

- [x] T01 — Inspect completion/replay, host orchestration, submission validator and governing host transaction/approval rules.
- [x] T02 — Prepare separate specification, implementation approach, focused verification and handover; leave F027 unchanged.
- [x] T03a — Close D01: owner requires both adapters for completion hosts, with no implicit fallback and fresh validation excluded from replay (2026-11-16; R06).
- [x] T03 — Finalize public context/error contracts and mark READY when applicable, applying the agreed D01 registration rule (P01, R05/R06).
- [x] T04 — Implement core access-before-replay and fresh authorization/general validation ordering, preserving result-specific validation and existing receipt semantics (P01/P02, AC01–AC04/AC06).
- [x] T05 — Migrate sandbox/direct callers and scoped web errors; preserve authoritative replay access and transaction coordination (P03/P04, AC03/AC06).
- [x] T05a — Implement the planned local Resource.BOOKING / Scope.VIEW / Scope.DECIDE fixture with contextual roles and focused host checks; no per-object ACL, Keycloak runtime dependency or production permission rules (P03/P05, R01, AC01/AC03/AC06).
- [x] T06 — Add/select focused unit and guarded SQL checks, including rollback and business-edit race; record actual evidence separately from reused results (P05, AC01–AC06).
- [x] T07 — Reconcile integration/source-precedence documentation after adoption, review final diff and complete handover with remaining APOLO gaps (P04/P05).

T04–T07 are authorized. Preserve F027 read behavior; no production access or broader release matrix is implied.
