# Tasks — F020

[Specification revision 3](spec.md) and [plan](plan.md) govern this feature. Actual verification is in the [handover](implementation.md).

- [x] T01 — Record agreed scope, snapshot timing and visibility; inspect existing contracts and prepare the four planning records. Revision 3 selects inputs as incoming bindings and resultContract as the answer, removes human inputContract from new authoring, and preserves published legacy reads and start input contracts.
- [x] T02 — Implement incoming contract and binding validation (P01; R01; AC01). Focused definition tests passed for valid and rejected bindings, source-derived types, strict new authoring, legacy runtime loading and unchanged start inputContract.
- [x] T03 — Implement atomic snapshots and compatible creation-evidence readers (P02; R02/R04; AC02/03/05). Focused SQL and isolated checks passed for persistence, exact values, failed-entry rollback, replay and legacy/strict decoding.
- [x] T04 — Expose authorized snapshots and read-only task display (P03; R03/R04; AC04/05). Web, SQL host-wiring and browser fixture checks passed.
- [x] T05 — Run focused verification, review and reconcile handover (P04; AC01–AC05). Recorded passing unit, SQL, build and browser checks, independent review and process cleanup in the handover.
