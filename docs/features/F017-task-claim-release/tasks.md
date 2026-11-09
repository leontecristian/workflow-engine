# Tasks — F017

The owner authorized implementation of revision 3 through focused verification and handover. The [implementation record](implementation.md) records the completed checks.

- [x] T01 — Record the requirements, decisions and focused plan.
- [x] T02 — Close O01–O06 under P01.
- [x] T03 — Implement policy and preserving storage under P02. All three parser paths passed isolated checks. Migration 005, its representative backfill and Hibernate schema validation passed on the guarded database.
- [x] T04 — Implement core claim/release, exclusive completion, authorization and replay under P03. Focused isolated and guarded checks passed, including races, recorder rollback and persisted compatibility.
- [x] T05 — Implement shared web/UI and sandbox adapters under P04. Scoped Java and frontend checks passed. The live sandbox browser verified reviewer, peer and administrator interactions.
- [x] T06 — Complete independent review, focused corrections, packaging, guarded verification, live browser verification and cleanup. See the implementation record for outcomes and the deployment boundary.
