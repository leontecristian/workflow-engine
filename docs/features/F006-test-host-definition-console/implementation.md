# Implementation — F006 Test-host workflow console

## 1. Implementation Summary

**NOT IMPLEMENTED — revision 3 documentation prepared 2026-10-31.** [Revision 3](spec.md) retains revision 2's F005 execution/host-console scope and permits isolated unit tests. Database integration tests still require explicit authorization. All [tasks](tasks.md) remain pending; runtime tasks depend on implemented F005 APIs.

Revision 2 preparation baseline: clean tree at `4a56862` (`docs: specify F005 execution and F006 definition console`). This documentation task changed no application code, migration, dependency, configuration, credential or fixture, and ran no build, startup, database operation or automated test. Concurrent F005 implementation changes are owned by the other agent.

## 2. Components and Files Changed

Revised only the four F006 documents, retaining the folder path while changing the title to Test-host workflow console. At the owner's scope clarification, this task's shared navigation/source edits were removed while preserving the other agent's changes. The F006 specification itself records the superseding scope decision. Another agent implements F005; its code and feature records remain untouched by this task. Shared indexes may describe the earlier F006 scope until separately reconciled.

Planned components: management/audit/family browsing, two-account security, persisted requests/start links, real assignment/task support, F005 runtime/replay/read adapters, transactional outbox recorder, manual local inbox dispatcher and Angular pages. None is claimed implemented.

For revision 3, the owner explicitly authorized correcting the repository AGENTS.md test policy. Changes are confined to that file and the four F006 records. Other project guidance and the active F005 agent's code/documents are preserved; AGENTS.md explicitly supersedes their older blanket testing restriction without changing historical evidence.

## 3. Technical Decisions and Deviations

The owner expanded F006 to execution after specifying F005. Revision 2 replaces the validation-only exception with real sandbox capabilities and retains management. F005 owns engine behavior; F006 owns host transactions, authorization, transport, adapters and UI. Missing F005 APIs cannot be replaced by placeholders.

Two authenticated accounts support assignment/denial observations. A small persisted sandbox request exercises the start-link callback without APOLO rules. Durable outbox/inbox support local handoff/recovery/duplicate checks; no scheduler or external broker is selected. Failure settings are startup-only. Production distributed delivery remains outside scope. Revision 3 supersedes the former automated-test exclusion: focused backend/frontend unit tests are allowed, while database integration tests (including embedded/Testcontainers/database-backed context tests) require explicit authorization.

## 4. Verification

### Historical revision 1 preparation

At `34929ae`, preparation inspected F002/F004 public boundaries/host master, added four documents and checked 90 relative links, 10 requirements, 10 tasks and 12 scenarios. No code/build/startup/database test occurred. Documents were subsequently committed in `4a56862`. These observations are historical, not revision 2 or runtime validation.

### Revision 2 preparation

- **PASS — contract/source review:** inspected F005 commands/context/results, start intent/callback, assignment, replay/query and event contracts and current host ownership. Required host adapters are absent at baseline.
- **PASS — documentation checks:** all 97 relative file-link destinations across the four F006 records and four navigation documents exist. Confirmed exactly four process files, 15 requirements, 14 tasks and 22 scenarios; reviewed their consistency, including recovery of duplicate starts that lose the host link update. Trailing-whitespace scans and scoped `git diff --check` passed. No separate evidence file added.
- **NOT RUN — builds, AC01–AC22 and null-name provider-error reproduction:** documentation-only redesign. No npm/Maven invocation, browser/runtime scenario, migration, database operation or automated test. Earlier F004/F005 records do not prove new behavior.

No temporary process was started. Revision 2 changes were unstaged/uncommitted at preparation; this task pushed nothing.

### Revision 3 policy clarification

- **PASS — policy/document review:** AGENTS.md now permits isolated unit tests and limits the restriction to creating/running database integration tests. F006 spec/plan/tasks consistently select unit-only verification plus the existing manual acceptance. The existing host context test remains preserved and excluded from unit-only runs.
- **PASS — documentation hygiene:** checked local links and whitespace for AGENTS.md and the four F006 files; no application/build configuration was edited.
- **NOT RUN — unit tests, integration tests, builds and manual acceptance:** this change updates policy/documents only. Allowing tests does not claim that tests have been written or executed.

The owner subsequently authorized committing only the four F006 documents. The AGENTS.md policy edit and all other workspace changes are excluded from that commit. No temporary process was started and no F005 agent-owned file was modified by this task.

## 5. Known Limitations

Application/adapters are not implemented. F005 availability is a dependency gate. Manual sandbox observations will not automatically prove production authorization, malformed third-party provider behavior, unobserved races/failures or distributed delivery. Null-constraint-name regression still needs controlled observation if feasible.

## 6. Remaining Work

Implement T01–T11, perform management acceptance T12 and execution/delivery T13, then reconcile T14. Record actual source/toolchain/migration revisions, retained data, restored settings/process state and per-scenario results here. Update F005 evidence only for observed matching behavior; reconcile all authoritative documents before any further contract change.
