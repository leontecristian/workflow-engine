# Tasks — F022 Previous-decision comments

[Specification revision 1](spec.md) and [plan](plan.md) govern delivery. **T02–T06 authorized by the owner implementation request on 2026-11-13.** Actual outcomes belong in [implementation.md](implementation.md).

- [x] T01 — Inspect current contracts, record owner visibility/authorization decisions, prepare the four process documents and reconcile project references. Complete after documentation checks and focused review.
- [x] T02 — Implement pinned root/node policy parsing, schemas and predecessor validation (P01; R01; AC01). Preserve omitted-policy compatibility.
- [x] T03 — Implement bounded previous-decision query/DTOs and integrity handling (P02; R02–R04; AC02/03/05/06). Depends on T02; no new completion storage or command behavior.
- [x] T04 — Add authorized task-context metadata and paginated shared read route (P03; R03/R04; AC04/06). Depends on T03; preserve source/history permissions.
- [x] T05 — Add shared read-only section, adapter and sandbox examples (P04; R05; AC07). Depends on T04; leave designer/APOLO code outside scope.
- [x] T06 — Complete focused unit/integration/browser/package checks, review and handover (P05; AC01–AC07). Checks may accompany T02–T05. Record actual evidence, gaps and process cleanup; no unrelated acceptance reruns.
