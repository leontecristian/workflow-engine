# Tasks — F008 Restricted expressions and exclusive routing

Applies to [spec revision 1](spec.md) and [plan](plan.md). **DRAFT.** Actual results belong in [implementation.md](implementation.md). F007 is a prerequisite, not an implementation claim.

- [x] **T00 — Prepare draft.** Inspect roadmap/language/F007/POMs and create four documents, fixture and acceptance matrix on the requested branch. Does not establish READY or safe execution.
- [ ] **T01 — Resolve G01–G03 (R01–12; plan 1).** Finalize profile/typed semantics, enforceable limits/dependency choice, DTOs/events/errors, validator identity and golden fixtures. Reconcile actual F007 delivery. **Done when:** exact contracts are specified, spec/plan are READY and any outstanding implementation dependency is explicit.
- [ ] **T02 — Extend definition validation (R01/02/04/10; plan 2/3).** Depends T01 and F007 model. Add gateway fields, mandatory-human graph checks and profile-aware publication. **Done when:** AC03/AC06/AC13 fixtures pass and old source/order/behavior remain compatible.
- [ ] **T03 — Implement restricted evaluation (R03–07/10; plan 2/3).** Depends T01 and F007 typed values. Add AST profile/accessors, exact conversions, budgets and bounded cache. **Done when:** AC05–AC09/AC15 semantic/safety matrix passes without database dependency; no unsafe fallback remains.
- [ ] **T04 — Wire progression (R02/03/07/08; plan 3).** Depends T02/T03 and F007 runtime. Add ordered first-match/default/error handling and shared command budgets. **Done when:** routing/budget/order checks pass without retries or partial commits.
- [ ] **T05 — Add evidence/compatibility (R09/10/12; plan 3).** Depends T04. Implement routing/read evidence, handoff privacy and extended result verification. **Done when:** AC01/AC11/AC12/AC15 codec/replay checks pass; skipped conditions are not false facts.
- [ ] **T06 — Deliver sandbox/console slice (R11; plan 2/4).** Depends T02–T05 and F007 host. Use the complete fixture, real action and existing input/recovery/history surfaces. **Done when:** security/precision/readable evidence work with explicit graph fallback; no frontend evaluator.
- [ ] **T07 — Isolated checks/build (R01–12; plan 4).** Depends T02–T06. Run selected suites and builds after inspecting discovery/dependencies. **Done when:** actual commands/results recorded; database tests excluded unless explicitly authorized.
- [ ] **T08 — Behavioral acceptance (AC01–AC15; plan 4).** Depends T07 and authorized access. Observe SQL rollback/concurrency, replay and browser behavior. **Done when:** scenarios have evidenced outcomes; NOT RUN/BLOCKED remain open, not replaced by mocks.
- [ ] **T09 — Reconcile handover (R12; plan 5).** Depends T07 and current T08 outcomes. Update feature/index/roadmap, check links/diffs and process cleanup. **Done when:** delivered scope and remaining acceptance are clear without reconstructing the chat.

No task implicitly adds facts, JSON, guards or parallelism. Database integration tests, destructive cleanup, commit/push and source relocation require their applicable authorization. Independent contract work may proceed while F007 implementation or runtime checks are pending.
