# Tasks — F015

Specification revision 2, IMPLEMENTED. The owner implementation request authorizes T02–T06 and focused checks.

- [x] **T01 — Prepare/revise the four documents and inventory.** Expand from reads to complete task UI, completion mapping and shared recovery. Define host policy/transaction/start boundaries and focused acceptance. Static documentation review only.
- [x] **T02 — Reconcile baseline and establish READY contracts.** P01; R01–08. After implementation is requested, confirm shared APIs/evidence, read/command adapter signatures, session scope and start-envelope compatibility; assign file ownership. Resolve routine alignment and reconcile behavioral drift before dependent edits.
- [x] **T03 — Extract HTTP reads and completion/replay mapping.** P02; R01–03/R06–07; AC01–03/07. Depends on T02. Add independent activation and host adapters; preserve paging, exact payload/token/errors, receipt ordering and host transaction proxies. Retain business-start/replay and service effects in host.
- [x] **T04 — Extract complete runtime UI, buffer and recovery.** P03; R03–06/R08; AC03–06/08–09. Depends on T02. Move full task form and generic validation, reads, frozen envelopes and recovery. Replace task/commands pages with wrappers and wire host request forms into shared recovery through start-specific adapters. Preserve export compatibility, uncertainty and session/selection isolation.
- [x] **T05 — Integrate and verify changed boundaries.** P04; AC01–09. Depends on T03/T04. Focused isolated tests, guarded completion/read/replay/rollback integration, builds and short browser checks; reuse applicable earlier evidence. Centralize runs and avoid duplicate builds or broad engine campaigns.
- [x] **T06 — Final review and handover.** P04 step 5; R06. Depends on T05. Review actual diff, record verification/gaps and remaining host scope, complete owned cleanup. No automatic commit/push or APOLO compatibility claim.
