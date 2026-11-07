# Tasks — F016

**Revision:** 2 — 2026-11-07. [Specification](spec.md), [plan](plan.md).

- [x] **T01 — Record selected design and draft:** update constitutional/design precedence and roadmap; create the four process documents. Shared current-membership behavior selected; claiming follows separately. Documentation only.
- [x] **T02 — Close contracts (P01):** O01–O05 are settled in revision 2, including owner-selected revocation/recovery behavior and diagnostic-only UI. Exact interfaces, storage/evidence versions, query/access semantics and plans are reconciled; specification is READY. Implementation subsequently authorized by the owner on 2026-11-07.
- [x] **T03 — Implement core/storage (P02):** after an implementation request and T02, implement R01–07/R09 in the named packages and ordered migration. Preserve direct USER behavior and existing evidence.
- [x] **T04 — Integrate host/web/UI (P03):** after relevant T02/T03 contracts, implement authorized discovery and shared task interaction, R05/R07/R08, with a focused sandbox fixture.
- [x] **T05 — Verify and hand over (P04):** complete AC01–07 with fresh/reused evidence scoped to actual changes; review final diff, compatibility and process cleanup. Record results, gaps and commit status. No claiming implementation.

T03–T05 are complete. The selected isolated, guarded integration, queued-delivery compatibility, browser and independent review results are recorded in implementation.md. SQL Server unavailability was resolved; successful and interrupted-run fixtures are cleaned and owned processes exited. No commit or push.

- [x] **T06 — Owner-started Chrome follow-up:** fix the reproduced host-start route collision, verify the affected MVC suite, restart only the existing IntelliJ backend under owner authorization, and finish the browser observation with the original command. Record retained browser fixtures and server state.

T06 result: route collision reproduced/fixed,10 affected unit tests PASS; current-bundle Chrome shared-task/access checks PASS. The port4200 dev bundle is stale and was not restarted; backend41748 remains running under owner authorization. See implementation.md for retained examples and the exact scope of browser evidence.

- [x] **T07 — Restarted frontend/cache follow-up:** remove the confirmed pre-F016 Angular dependency cache under approved frontend interruption, restart the existing frontend run, inspect retained task41 on4200 and record process state. No new workflow or backend process is required.

T07 PASS: after explicit owner approval, stopped only the existing frontend run, cleared its resolved generated cache, and reran the same IntelliJ configuration. Chrome4200 now shows GROUP sandbox.finance, retained completing actor, disabled completed-task actions and current eligible-inbox wording. Both existing servers remain running; no workflow command or backend restart was needed.
