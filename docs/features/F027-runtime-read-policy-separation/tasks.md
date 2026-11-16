# F027 tasks

Scope: [specification revision 6](spec.md), [plan](plan.md). Documentation preparation only is authorized.

- [x] T01 — Inspect current adapters, core queries, controller consumers, policy tests and governing requirements; capture route-specific access/paging and the F015 wording conflict.
- [x] T02 — Prepare specification, proposed interfaces/flow, implementation sequence and focused verification plan.
- [ ] T03 — Adopt proposed public integration contracts and reconcile DRAFT/READY status before implementation; confirm any revised behavior in the specification first (P01, R01–R06).
- [ ] T04 — Implement neutral actor-only read-policy composition and concrete authorized read facade; My Tasks combines `canReadTask` with core eligibility without ownership-adapter dependency, including indirect host calls; add focused isolated checks (P01/P02/P03, AC01/02).
- [ ] T05 — Reuse existing operation adapters for availability while preserving completion hint, separate cancellation context, snapshot order and activation behavior (P02, AC03/04).
- [ ] T06 — Migrate web and sandbox wiring, directory/labels and dependent read consumers; apply administrator-wide read visibility, preserve ordinary-user visibility and wire behavior, and keep operation permissions separate (P03, AC01–AC05).
- [ ] T06a — Replace reassignment-to-ownership authorization delegation with the shared host-private business-access helper; preserve business decisions, operation-specific grants and recovery behavior, with focused isolated adapter checks (P03/P05, R04, AC06).
- [ ] T06b — Default directory search to 403 REASSIGNMENT_FORBIDDEN, support label-only adapters, and verify inherited denial plus explicit search overrides with focused web unit checks (P03/P05, R05, AC07).
- [ ] T07 — Update integration/migration documentation and narrow F015 source-precedence reconciliation (P04, R06).
- [ ] T08 — Execute only selected relevant verification and packaging; record results, reuse and gaps; review final diff and finish handover (P05).

T04–T08 require subsequent implementation authorization. T04 precedes consumer migration; targeted test adaptation may proceed alongside the owning implementation when later authorized. No broad parallel work or test matrix is required.
