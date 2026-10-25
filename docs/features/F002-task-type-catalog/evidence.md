# F002 evidence

Specification: [revision 1](spec.md). Status: **documentation prepared; implementation NOT RUN**.

## Baseline

- `workflow-engine`: `4a7bf64c632b6e07216a5b285265f5ab5a0387ec` (`feat(db): implement F001 initial workflow schema`), clean working tree before F002 documentation.
- Source design: v1.4 at `apolo-docs` commit `c32ffb07cc9f4f2f3d1d7bf087455c1af13acbc3`; constitution v2.2.0.
- Observed scaffold: core component configuration; host imports core and scans its entities/repositories. Core already declares Spring transaction, Spring Data JPA and Jakarta Persistence dependencies. No catalog Java implementation exists at this baseline.
- No build, application startup, database access or automated tests performed for this documentation task.

## Documentation preparation — 2026-10-25

PASS: reviewed repository ownership/scanning and the F001 catalog mapping against database-design and constitution boundaries. Checked all relative file links across the five F002 documents (no missing targets), checked new-document trailing whitespace and ran `git diff --check` (no whitespace errors). Reviewed the feature-index and development-workflow updates. These are documentation checks, not implementation or behavioral acceptance evidence.

## Implementation results — fill during implementation

Record date, code revision/working-tree state, effective dependency/tool versions, exact commands and result, actual host/database configuration without credentials, generated artifacts/log location and owned-process cleanup. Preserve applicable F001 identities/checksums.

| Check | Requirements / scenarios | Result | Reason/evidence |
| --- | --- | --- | --- |
| API, validation, persistence and forbidden-operation review | R01–R07; AC01–AC10 | NOT RUN | No implementation yet. |
| Unique-insert and optimistic-update race-path review | R02/R04/R07; AC03–AC06 | NOT RUN | No implementation yet; static review will not prove runtime behavior. |
| Host transaction/audit integration contract review | R05; AC09/10 | NOT RUN | No implementation yet; production host adapters deferred. |
| JDK 21 package, tests skipped | R08; AC11 | NOT RUN | Documentation-only task. |
| Entity scanning, JPA validation and completed startup | R01/R08; AC11 | NOT RUN | Deferred to implementation. |
| Existing catalog and changelog unchanged by startup | R08; AC11 | NOT RUN | Deferred to implementation. |
| Temporary process cleanup | R08; AC11 | NOT RUN | No process started. |
| Functional create/read/update/list, boundaries and no-op execution | AC01/02/04/06/07/08 | NOT RUN | Outside current execution scope; no mutation fixtures authorized. |
| Duplicate/stale races and transaction/audit rollback execution | AC03/05/09 | NOT RUN | Requires appropriate behavioral verification and host integration. |

Record static review and executed behavior as separate results. Do not replace NOT RUN behavioral rows with PASS merely because the build, wiring or schema validation succeeds. IMPLEMENTED can describe code completion while these gaps remain explicit; it does not establish production readiness.

## Remaining integration gates

Concrete host authorization and durable transactional management audit; actual provider concurrency/rollback evidence; production dependency alignment; future publication capability checks and execution with nonselectable retained types. No automated tests are authorized by this feature documentation.
