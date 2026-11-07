# Tasks — F011

- [x] **T01 — Specify the bounded sequential-stage slice.** Record R01–08 and AC01–13 after inspecting governing designs and existing callers; prepare P01–05 and update roadmap/index navigation. Completion means documentation preparation and its recorded static checks only.
- [x] **T02 — Generalize definition validation/model.** P01; R01/R08; AC01–04/13. Depends on T01 and a subsequent implementation request. Update all schema/parser/capability paths and exact node lookup, with focused isolated checks.
- [x] **T03 — Implement stage entry and progression.** P02; R02–04/R08; AC01/02/05–07/10/11. Depends on T02. Enter only the reached stage, preserve atomic handover and dynamic history sequencing, and stop at human nodes or END.
- [x] **T04 — Reconcile results, reads and recovery.** P03; R03–06/R08; AC04/06–10/13. Depends on T03. Preserve submitted-task receipt identity, exact historical contracts, authorization, final versions and replay after later progression.
- [x] **T05 — Complete sandbox demonstration and console.** P04; R07; AC05/06/08/12. Depends on T02–04. Supply runnable fixtures, current-stage presentation and graph/task links without changing retained command payloads.
- [x] **T06 — Verify and hand over.** P05; AC01–13. Depends on T02–05. Record unit/build, SQL integration and browser outcomes separately, resolve failures, retain explicit gaps, review the final diff and document process cleanup and production limitations.

The subsequent 2026-11-07 owner request authorizes T02–T06, isolated tests and guarded test-database verification. Do not commit or push.

T02–T06 implementation and automated/HTTP handover are complete. AC12 interactive browser rendering/clicking remains BLOCKED by Computer Use URL detection; this is retained explicitly in implementation.md and is not a passed acceptance check.
