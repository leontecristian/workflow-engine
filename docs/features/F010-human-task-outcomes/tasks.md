# Tasks — F010

- [x] **T01 — Specify selected scope.** R01–08, AC01–12; inspect current implementation, record concrete contracts/example and reconcile roadmap/source links. Documentation only.
- [x] **T02 — Implement definition/model support.** P01, AC01–03/12; authorized by the owner on 2026-11-06. Remove single-outcome graph assumptions across all schema/parser paths, retain single-human scope and validation context safety.
- [x] **T03 — Implement selected-outcome execution.** P02, AC04–10/12; depends on T02. Update traversal/callers/evidence while preserving transaction, replay and concurrency contracts.
- [x] **T04 — Complete console and sandbox.** P03, AC11; depends on T02/T03. Explicit outcome actions, comments, frozen recovery payloads and accurate graphs; include runnable fixtures.
- [x] **T05 — Verify and hand over.** P04, AC01–12; depends on T02–T04. Isolated/build/database/HTTP checks and handover are complete. The subsequent Chrome walkthrough covers explicit actions, blank-comment blocking, approve/reject routes, exact submissions, replay/conflict and selected graphs/history. See implementation.md for dated results and remaining browser/APOLO limits; the earlier browser-control blocker is resolved.

The owner authorized T02–T05 on 2026-11-06. T05 has the explicit remaining gap above; no runtime scope is deferred. Do not commit or push.
