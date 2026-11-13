# Implementation — F021 ALL collective review

## Capacity refinement — revision 2, 2026-11-13

The owner requested tracing actual copies because revision 1's surrounding capacity limits were too high. The 64 KiB result, 8 MiB UTF-16 collection, authoritative task evidence and no-reservations decisions are unchanged. Specification C01 now owns the complete copy/headroom derivation; plan/tasks and navigation references follow revision 2.

Read-only source trace covered RuntimeVariables.apply (old/new values and source metadata), RuntimeServiceProgression (separate service input/output history and gateway evaluation evidence), RuntimeHistoryBatch (cumulative details and returnability), DefinitionContentCanonicalizer/RuntimeJson and ViewerWire/ConsoleWire (canonical text and one detailsJson escape), and F021's planned shared snapshot. The latter remains a plan, not existing runtime support.

Revised engineering ceilings in MiB (UTF-16 / UTF-8): collection/value 8/12; variable/service/human input maps 10/15; history record 24/36; history per command 64/96. History detail response is 40 MiB UTF-8; large task/variable responses and raw start/value ingress are 16 MiB UTF-8. Preserve 1 MiB metadata/submission/history-summary pages and the 4 MiB dual-encoding gateway evaluation-evidence boundary. The planned history CHECK becomes 25,165,824 bytes; no migration file was created or applied.

Calculation: the selected resolving command can retain about 8 MiB output creation + 20 MiB service input/output maps + 20 MiB old/new variable values + 10 MiB next input snapshot = 58 MiB before small metadata. The 64 MiB cumulative bound covers this path with headroom; it does not guarantee arbitrary sequences of large services. A 24 MiB canonical UTF-16 record needs at most 36 MiB UTF-8 when its canonical text is escaped once by the current non-ASCII-literal codec, plus a small response envelope; 40 MiB is sufficient. Maps leave 2 MiB around the selected 8 MiB collection without reserving it for any task.

A small in-memory arithmetic/serialization illustration covered ASCII, quotes, backslashes, BMP Unicode, supplementary Unicode and escaped controls. This checked the derivation only; it did not run the Java codecs or establish production heap/transport capacity. Independent read-only review confirmed record and response arithmetic and identified the gateway evidence boundary, now explicitly retained in C01/P02. Whole-value expression reads may hit that boundary even when a value fits storage; small path reads remain subject to unchanged expression work rules.

No runtime code, tests, builds, DB access or processes were used. Implementation must verify actual bounded memory, exact private-codec transport, boundary failures and rollback under the focused P07 checks. Earlier revision 1 review statements establish only that earlier planning review; the larger superseded numbers are not current contracts.

## Planning handover — revision 1, 2026-11-13

**Status:** specification and plan prepared; runtime implementation NOT STARTED. The owner requested documents only and explicitly withheld implementation. No application code, schema/migration file or dependency was changed. APOLO integration waits for owner access.

The decisions are captured in [spec.md](spec.md): fixed 1–100 task roster, task-owned USER/GROUP/ROLE assignment and claiming, shared immutable input snapshot/result contract, independent answers, contribution or early resolution, original evidence only on TASK_COMPLETED, activity resolution only on ACTIVITY_COMPLETED, resolved collection bindings and optional output copy. Individual results remain 64 KiB; collection ceiling is 8 MiB UTF-16 with related boundary increases and no reservations.

Engineering choices include compact task-identified collection entries; activity-scoped shared snapshot evidence with per-task references; compatible ALL provider/receipt contracts; a preserving history-constraint migration; and boundary-specific limits/decoders. These are specified work, not existing implementation. No remaining product decision is identified; implementation discoveries changing these contracts require reconciliation before code.

## Inspection and verification

- Code inspection baseline: `ad659e9`; the roadmap-only commit `5cae043` was present at final review. This chat's ALL-priority changes were preserved and reconciled with F021; no external commit was reverted.
- Read-only inspection found SINGLE guards in definition parsing, assignment result/provider, activity entity, stage entry, completion, receipt validation and submission binding. Existing SQL activity constraints permit ALL; actual Java/runtime support is absent.
- Read-only inspection found task creation lookup assumes activationSequence + 1 and F020 repeats input evidence per task. The plan replaces that assumption and shares new ALL snapshots to avoid multiplying large values by 100.
- Read-only inspection confirmed current task-result/value/map/history/response limits and the 4 MiB physical history CHECK; C01/P02 specify coordinated increases and migration without applying them.
- Two bounded read-only GPT-6.1 Sol Medium workers mapped capacity/storage and runtime/adapter paths. They made no edits, ran no tests and started no processes. The lead authored and reconciled the records; no model switch is claimed.
- PASS — focused independent document reviews checked runtime/receipt/collection compatibility and capacity/storage contracts. Corrected the entry event name to ACTIVITY_ENTERED, made shared-snapshot uniqueness/command/ordering invariants explicit, and named the history wire parsers whose decoded string bounds must increase. No unresolved review finding remains.
- PASS — specification/plan/task consistency, local Markdown file targets, roadmap contents anchors, exact four-document feature set, new-file whitespace and git diff --check. This is document validation, not runtime evidence.
- Unit tests, Maven/UI builds, SQL integration, browser/runtime acceptance, migration execution and APOLO compatibility: **NOT RUN — documentation-only task.** Prior feature evidence is listed as candidate reuse in plan.md, not a fresh PASS for F021.

## Remaining work and process state

T02–T08 remain open and require a separate implementation request. Validate the larger memory/transport/history path and changed authorization/receipt boundaries during that implementation; planning does not establish runtime capacity or production readiness. No host, browser fixture, database process or listener was started. Changes are uncommitted; no push.
