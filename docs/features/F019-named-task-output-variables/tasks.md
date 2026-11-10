# Tasks — F019

Specification revision 2 governs the implementation. See the [handover](implementation.md) for verification and open gates.

Owner settled the two-source design after research: recommend submission bindings for original answers and variable bindings for mutable working copies. Implement both through the same service-input mechanism; a submission source requires no variable copy.

- [x] T01 — Record owner decisions, inspect existing definition/completion/variable/expression paths, prepare the four records and reconcile source precedence.
- [x] T02 — Add pinned output-name validation and derived producer descriptors (P01; R01–02; AC01).
- [x] T03 — Add completion creation, write provenance, post-creation replacement and materialized-only queries (P02; R02–03/R05–06; AC02–03/AC05–06).
- [x] T04 — Add bounded existence evaluation, compatible evidence rendering and the sample binding reviewResult into the downstream service input; add submission-by-nodeKey as the alternative input source while preserving completedActivityId (P03; R03–07; AC03–07).
- [x] T05 — Complete focused unit/web/SQL/view/build checks and review, reconcile actual evidence, and clean up processes started for verification (P04). Scoped checks passed. Independent review found one resolver integrity issue, which was corrected and verified with targeted reruns.
