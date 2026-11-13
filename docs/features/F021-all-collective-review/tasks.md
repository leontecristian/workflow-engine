# Tasks — F021 ALL collective review

[Specification revision 2](spec.md) and [plan](plan.md) govern delivery. **No implementation is authorized yet.** Actual results belong in [implementation.md](implementation.md).

- [x] T01 — Record owner decisions, inspect affected paths and prepare the four planning records; update roadmap/index and scoped design precedence. Revision 2 traces actual copies and reduces surrounding capacities while retaining the 8 MiB collection; documentation checks and focused review are recorded in the handover.
- [ ] T02 — Extend pinned ALL definition and bounded host assignment contracts (P01; R01/R02; AC01/02). Depends on implementation authorization. Preserve SINGLE host providers and publication behavior.
- [ ] T03 — Align capacity profiles and add the preserving history-constraint migration (P02; C01/C02; AC09/10). Depends on T02's model/codec decisions. Do not execute against an unauthorized database.
- [ ] T04 — Implement atomic roster entry, one shared immutable snapshot and exact-task creation lookup (P03; R02/C03; AC02/03/08/10). Depends on T02/T03.
- [ ] T05 — Implement contribution/resolution, sibling cancellation and compatible receipts (P04; R03/R06; AC04–06/10). Depends on T04; preserve same-transaction guarantees and replay identity.
- [ ] T06 — Implement original collection bindings and resolution-only named output (P05; R04/R05; AC07–09). Depends on T03–T05; keep original submissions authoritative and enlarged reads returnable.
- [ ] T07 — Adapt shared web/UI and sandbox fixture (P06; R06; AC08/11). Depends on T04–T06 public/read contracts. Keep APOLO and designer outside scope.
- [ ] T08 — Run focused verification, independent review and complete handover (P07; AC01–AC11). Checks can accompany preceding tasks; finish with actual results/gaps and process cleanup. No broad historical acceptance rerun.
