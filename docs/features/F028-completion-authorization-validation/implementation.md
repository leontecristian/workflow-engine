# F028 preparation and handover

**2026-11-16:** DRAFT revision 2, documentation preparation only. Runtime code is not implemented and no implementation authorization is claimed. See [specification](spec.md), [plan](plan.md) and [tasks](tasks.md).

## Actual work

Inspected current completion/replay and host transaction paths, F018 validation modes, exact completion command/context types and booking baseline requirements at `50e356395fae2822ea38493ee255c6e8ef9da7c3`. Prepared four process documents and feature-index navigation. F027 staged files and pre-existing roadmap changes were preserved; no application, migration or test file was changed.

The design distinguishes current business access for recovery, fresh approval permission, and business-record validation before acceptance. It retains successful receipt recovery before fresh checks and does not use the optional result validator as a universal permission hook. APOLO-specific rules, business locking and finalization remain integration responsibilities.

## Checks and limitations

Revision 2 records the owner's D01 decision: both adapters are required for completion hosts, without opt-in or implicit allow/no-op fallback. Required registration is distinct from invocation: authorization protects completion and recovery; the general validator is resolved/invoked only for fresh acceptance, never successful receipt replay or lookup-only misses. Specification, plan, tasks and feature index were aligned. Focused lead content/link/whitespace checks were repeated; the independent review below covered revision 1.

- Source inspection: completed for planning; not runtime PASS evidence.
- Focused independent evidence/draft review: completed; no actionable issues found in replay ordering, transaction boundaries, result-mode coverage or separation from F027.
- Documentation structure/link/whitespace checks: PASS (four process documents; relative file links resolve; no trailing whitespace); `git diff --check` passed. Final index diff inspected; F027 has no unstaged changes from this task and its existing staged content was preserved.
- Unit/build/integration/browser checks: NOT RUN (planning-only request).
- Processes started: none. Database access: none.
- D01 is CLOSED / AGREED by the owner. Public signatures/error names remain PROPOSED; feature remains DRAFT pending their finalization, not because adapter requirements are undecided.
- Changes are uncommitted; no files staged by this task.

Next work is finalizing the remaining engineering contracts and eventual implementation authorization, not reopening D01 or executing the planned checks now. Existing receipt/transaction evidence is referenced for reuse only where applicable; no APOLO production guarantee follows from this document.
