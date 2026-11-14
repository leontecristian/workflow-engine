# Tasks — F023 Create next definition version

- [x] T01 — Analyze existing authoring contracts and code, capture automatic allocation selection, and prepare revision 1 specification/plan.
- [x] T01b — Analyze draft deletion and permanent numbering; reconcile revision 2 contracts and migration/import implications.
- [x] T01c — Apply owner-selected family counter and strict import floor; reconcile revision 3 and superseded ledger implementation work.
- [x] T02a — P00: replaced unapplied ledger files with family-counter migration/backfill and entity mapping; counter migration applied only to the guarded test database.
- [x] T02 — P01: implemented core copying/deletion and shared family coordination; focused isolated tests cover changed commands. SQL verification remains T05.
- [x] T03 — P02: wired required host adapter, HTTP contract and audit; updated implementations and focused tests.
- [x] T04 — P03: implemented shared UI create/delete dialogs, interaction tests, adapters and consumer documentation; focused browser evidence is recorded in implementation.md.
- [x] T05 — P04: scoped builds/unit/UI/browser evidence retained; repaired test-only RECOVERY_PENDING state without a reset, completed fixture cleanup and obtained passing evidence for all 15 focused SQL cases. Corrected test-only reset typing, publication fixture and audit exception expectations; reran only affected cases. Final test-database inspection found no F023 fixtures/schemas or held Liquibase lock.
- [x] T06 — Reconciled delivered revision 3 scope, AC01–09 evidence, consumer/deployment documentation and final diff. Focused independent production review found no actionable defects; SQL follow-up and test-only corrections are documented. No required focused SQL gate remains open.
