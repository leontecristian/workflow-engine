# Tasks — F009

- [x] **T01 — Record selected scope.** Document the owner's schema deferral, snapshot/mapping/navigation decisions, requirements and acceptance scenarios; reconcile governing documents. Documentation only, R01–09.
- [x] **T02 — Close concrete contracts.** P01; G01–G04 are resolved by [C01–C07](contracts.md), including packages, API/fixture/evidence examples and additive schema setup.
- [x] **T03 — Implement value/binding/persistence support.** P02/P03, R01–03/06–09; depends on T02 and a user request to implement. Update scalar/JSON callers together; apply C05 additive schema setup without old-data readers.
- [x] **T04 — Implement safe JSON evaluation/evidence.** P02/P03, R04–09; depends on T02/T03 and implementation authorization. Complete strict path errors, bounded reads, routing trace and new-baseline integration.
- [x] **T05 — Deliver host demonstration.** P03, R09; depends on T03/T04. JSON input/output/inspection must preserve exact values and authorization.
- [x] **T06 — Verify and hand over.** P04, AC01–14; record unit, build, database and browser results separately, including gaps and process cleanup. Reconcile spec/plan/tasks before marking IMPLEMENTED.

T02 is complete for specification revision 3. D01–D17 and C01–C07 define the implementation contract. T03–T06 are complete for the authorized implementation. Actual automated verification and the remaining interactive browser/APOLO limitations are recorded in implementation.md; no production acceptance is implied.

Maintenance handoff: read spec.md, contracts.md and the latest implementation record together. In T03, update history readers as well as writers and preserve row-absence initialization semantics (C04/C05). In T04, replace per-variable trace deduplication with ordered path-aware reads. In T05, preserve the existing HTTP value shape and exact JSON text. P04 lists the review-specific regression checks. Do not interpret earlier dated open-work narratives or illustrative factory names as overriding the normative annex.
