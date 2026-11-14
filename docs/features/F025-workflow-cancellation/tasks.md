# Tasks — F025 workflow cancellation

Targets [spec revision 1](spec.md) and [plan](plan.md). Implementation and focused verification authorized on 2026-11-14.

- [x] T01 — Record owner decisions, current baseline, specification, plan and focused verification. Documentation review recorded in implementation.md.
- [x] T02 — Implement P01 core command, authorization, cancellation receipt and aggregate changes; satisfy R01–R05 with focused isolated checks. Coordinate shared F024 files first.
- [x] T03 — Implement P02 shared web routes and sandbox transaction/access adapters; depends on T02 contract availability. Verify request/recovery/activation behavior.
- [x] T04 — Implement P03 shared UI and affected read/graph rendering; depends on T03 contracts. Preserve view-only consumers and frozen command recovery.
- [x] T05 — Execute P04 selected SQL, packaging and browser checks, resolve relevant failures, record outcomes/gaps and process cleanup. Depends on T02–T04; no unrelated historical acceptance gates.
- [x] T06 — Reconcile implementation record, library contracts, feature status and final diff; hand over without claiming APOLO readiness. Depends on T05.
