# Implementation — F005 Minimal SINGLE workflow execution

## 1. Implementation Summary

**Core IMPLEMENTED, manual acceptance BLOCKED — 2026-10-31.** T01–T10 implement and statically review/package the selected core slice. **T11 remains unchecked**: AC01–AC13 have no executed behavioral evidence. T12 records this boundary rather than treating missing adapters as a passing runtime test.

Implementation baseline: `4a56862799b354eb1807224862945a6ceb8a1b6f`, initially clean. The F005/F006 preparation documents were committed at that baseline. Concurrent F006 revision-2 documentation/navigation changes appeared during this session; they are preserved, not discarded or attributed to F005. F006's planned real sandbox support is not yet installed in the host.

The implemented slice starts one exact published version, links host data after allocated identity, resolves one assignment, waits at SINGLE, completes the assigned task to END, records ordered immutable evidence/receipts and calls the required durable recorder. It also supplies detached runtime queries. It adds no runtime endpoint, host outbox, fake adapter, variable write, migration or F006 implementation. No staging, commit or push was performed.

## 2. Components and Files Changed

- **T02 / R02–04, R06–11:** commands, trusted context, host-start intent/allocation, assignment values, immutable execution/current views, pages, submissions, operation and event DTOs in dto.runtime; three SPIs in service.runtime: SingleTaskAssignmentResolver, StartLinkAction, WorkflowEventRecorder. WorkflowExecutionException exposes the specified sanitized categories.
- **T03 / R01/R11:** MinimalDefinitionLoader owns the verbatim shared F004 schema/unsupported/graph checks. DefinitionValidationService retains publication-only catalog/capability/evidence work. WorkflowDefinitionRuntimeReader returns detached ExecutableDefinition, holds PESSIMISTIC_WRITE during refreshed start eligibility, and permits PUBLISHED/RETIRED pinned loads. PinnedDefinitionIdentity permits current-state views without demanding executable topology.
- **T04 / R06/R08/R11:** private RuntimeJson uses fixed-field canonical request fingerprints, strict schema-1 receipt/result decoding, exact Long strings, UTC times and bounded JSON. Both command methods and both findReplay overloads resolve receipts before lifecycle/token/adapter checks. No failed-transaction recovery query or automatic mutation retry exists.
- **T05–T08 / R02–09/R11:** controlled constructors/transitions on the five runtime entities, plus insert-only history/receipt constructors. WorkflowExecutionService owns sequencing, the early managed instance claim, refreshed ownership/roster/assignment checks, final state flush/token capture, immutable receipt storage and ordered event recording. RuntimeHistoryBatch/RuntimeProgression/RuntimeHostSupport/RuntimePersistenceErrors keep evidence, bounds and integration failures focused.
- **T09 / R08/R10:** WorkflowRuntimeQueryService, RuntimeViews and RuntimeDefinitionPins provide exact/current/submission reads, SQL-bounded task pages and a history-envelope projection excluding details. Task/activity submissions decode only the accepted no-result schema and cross-check ownership, actor, closure time and outcome.
- Existing F002 service/repository code, definition mutation APIs, variable mappings, all changelogs/POMs and host sources/configuration/tests remain unchanged. Definition helper extraction and the documented internal reader are the only F004 implementation changes.

## 3. Technical Decisions and Deviations

No material behavioral contract change was needed. The plan records one engineering clarification: current instance identity reads do not require an executable graph, preserving read access to stored CANCELLED/ALL values. Mutations still accept only the selected minimal graph.

**Pinned integrity and retirement.** The immutable INSTANCE_STARTED details retain the pinned hash rather than inventing an instance column. Command loading checks canonical text/hash/indexed identity and shared structural rules. Runtime pin/current identity reads compare definition identity/hash with that start fact and stored correlation. New start refreshes the definition under a transaction-held write lock and rechecks PUBLISHED; completion claims the instance first and loads its PUBLISHED/RETIRED pin without a definition write lock. Runtime catalog resolution uses exact findByCode and ignores selectability. No publication validation or validation-evidence update occurs during execution.

**Identity, fingerprints and JSON.** Actor/reference/key/comment bounds and exact seven-digit UTC precision are checked without trimming or normalization. Host intent/callback pairing is checked before invocation. RuntimeJson reuses the existing F004 canonical encoder; it changes no host ObjectMapper. Fingerprints include all specified semantic fields and explicit nulls, with Longs as decimal strings; time, correlation and command UUID are excluded from the digest. Receipt decoding rejects unknown/missing fields, duplicate JSON names, ordinal enums, invalid Long encodings and inconsistent result ownership/state. Integer decoding is exact even for the canonical encoder's exponent spelling. Original result snapshots remain independent of current state; replay never calls links/resolver/recorder or refreshes them from current rows.

**Concurrency, flush order and recovery.** Completion refreshes/checks the instance, changes last_history_sequence through reserveHistorySequence and explicitly flushes that real @Version write before child mutation or host effects. Task/activity are refreshed afterward. The first reservation is 5 for the selected graph; final progression reaches 8. Start writes sequences 1–4 with activation sequence 3. State is flushed before capturing the provider-managed version; no predicted increment is returned. Receipt insert is flushed before recording, exposing a duplicate PK loser without querying a failed transaction. A final flush/snapshot comparison rejects unsupported callback changes that would make the returned token/state stale.

A ThreadLocal reentry guard is scoped to the call and removed in finally; runtime mutation reentry fails. No REQUIRES_NEW, FORCE_INCREMENT, retry loop, dispatcher or competing transaction infrastructure is introduced. Known optimistic exceptions map to STALE_VERSION. Only the named receipt PK (including the bounded SQL Server 2627 fallback) maps to CONCURRENT_COMMAND; null/unknown constraint names remain safe, and unrelated failures become HOST_CONFIGURATION. After rollback, only the host may authorize a fresh findReplay call with the original command.

**Host adapters.** Resolver IDs are snapshotted once; duplicate/malformed registration blocks new starts, not management-only bootstrap. Cardinality is rejected before copying/traversing provider rosters. Completion requires task capability support and the durable recorder, but does not resolve/re-require an executable assignment resolver. No absent dependency gets a permissive default. The ordered immutable event batch has stable instance:sequence IDs and only the specified limited payload; comments, host fingerprints and business material are excluded.

**Reads and work bounds.** SQL pages fetch limit+1; task/history cursors are exclusive and ordered. Complete page JSON, including continuation, is measured in both UTF-8 and UTF-16 with a 1 MiB limit. Byte exhaustion returns the last emitted cursor without splitting/skipping an item; a single oversized item fails. Accepted submissions have immutable empty inputs and no result field. START and END each consume one automatic step; unsupported nodes cannot fall through. The minimal graph cannot demonstrate a 50/51-step boundary.

## 4. Verification

### Historical preparation

Preparation at 34929ae reported source review, 113 file links across F005/F006/navigation, requirement/task/scenario traceability and whitespace PASS. It performed no implementation, build, runtime command, database change or automated test. Those historical observations are not behavioral evidence.

### Source review and package — PASS (T10)

Read the repository guidance, F005 documents, F001 physical contract/DDL, F002–F004 contracts and actual code. Reviewed field coverage/identity/version placement, DTO/SPI boundaries, semantic fingerprints, receipt-first call order, early parent claim, child refresh, final-token capture, append-only evidence, callbacks, event payloads and bounded query/projection paths. This is source inspection, not executed concurrency or rollback evidence.

Explicitly selected installed **Corretto 21.0.13+10-LTS x64** through JAVA_HOME. java -version and wrapper -version confirmed **JDK 21** and **Maven 3.10.0**. Ran the prescribed command after implementation and source-review refinements:

```powershell
./mvnw.cmd '-Dmaven.test.skip=true' package
```

All package attempts passed; final package finished **19:50:51 +02:00**. Log: target/f005-build.log. Production compilation uses release 17. Both modules explicitly skipped test compilation/execution. No automated tests or self-running scenario harnesses were created/run.

Inspected the core JAR for WorkflowExecutionService, WorkflowRuntimeQueryService, RuntimeJson, WorkflowDefinitionRuntimeReader, MinimalDefinitionLoader and both existing changesets. Core remains an ordinary JAR with no BOOT-INF; only the host is executable. javap -verbose on WorkflowExecutionService reported **major version 61** (Java 17). No new dependencies/resources/migrations or host configuration changes were required.

### Infrastructure-only bootstrap and metadata — PASS (AC14 subset)

No WORKFLOW/SPRING/SERVER_PORT/JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS overrides were present. The unchanged host targets localhost:1433/apolo_workflow_dev using Windows native authentication, matching JDBC/auth DLL 12.8.1 and ddl-auto=validate.

Captured schema/tracking/row-count metadata before and after bootstrap:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -b -W -s '|' -i target/f005-metadata.sql -o target/f005-before.txt
# Repeated with -o target/f005-after.txt.
```

The ignored SQL file contains SELECTs only: DB_NAME, Liquibase IDs/author/path/checksum/execution state, wf column/type/length/precision/nullability/collation/identity metadata, FK actions/trust, CHECK-definition hashes/trust, index columns/order/filter/clustering and partition row counts.

Started the packaged host hidden using the selected JDK and README command with --server.port=0 plus temporary entity/repository debug logging. **PID 40852**, created **19:38:18.803 +02:00**, started successfully at **19:38:29.087** on random port 57999. Logs identify all nine entity/table bindings, nine repositories, successful JPA schema validation, EntityManagerFactory and application startup. This also constructs the new codecs/services and validates repository query construction without installed runtime adapters. Bootstrap preceded the final error-translation/Javadoc refinement; final packaging afterward passed with unchanged bindings, queries and mappings. No workflow command or host callback was invoked.

Liquibase reported database up to date, **zero changesets run / three previously run**. Source resources and existing tracking values remained unchanged:

- f001-001-create-wf-schema: `9:a759c434c85142e959b6b927a4949d90`.
- f001-002-create-initial-tables: `9:3333ca75046b0857a7a23556e03e0d52`.
- f004-001-drop-legacy-expression-policy: `9:71ec11105b310c821a80ce7977a96b89`.

Before/after snapshots are identical, both SHA-256 `76875C349F2AC8BE23BE0B8A6A49E6C985168F1D3279642342CD5C45B5BD0155`. All nine workflow tables have zero rows. No fixtures, application-data writes or new migration were issued. Bootstrap can update Liquibase lock bookkeeping; it is not claimed to be a database read-only action.

Logs/identity: target/f005-host.out.log, target/f005-host.err.log and target/f005-process.json (ignored). Compared PID, executable, command and creation time with live CIM data before stopping only PID 40852; a subsequent process query confirmed it absent. No owned temporary process remains.

### Manual acceptance — BLOCKED / NOT RUN (T11 remains open)

Current host sources provide no real SingleTaskAssignmentResolver, compatible executable capability provider, WorkflowEventRecorder with inspectable participating durable storage, or trusted runtime transaction/authorization facade. F006 revision 2 specifies future sandbox adapters, but documentation is not an installed implementation. This task supplies no substitute adapter, host schema, endpoint, startup writer or permission fixture.

- **AC01–AC04:** NOT RUN — no authorized executable start/completion facade, real assignment/task support or durable recorder.
- **AC05–AC07:** NOT RUN — disabled-catalog/retired-version behavior, start/retire races, original-result replay and semantic conflicts require actual committed commands and adapters.
- **AC08–AC10:** NOT RUN — losing-command rollback, final managed-version/SQL ordering, duplicate-ID recovery in a fresh transaction, failed callbacks/recorder/receipt/outer commit require true transactional/concurrent observations.
- **AC11–AC13:** NOT RUN — current/bounded/submission reads, same-transaction visibility, malformed evidence, ownership/roster failures and transaction-mandatory behavior have source review only, no representative executed records.
- **AC14:** package/artifact/bootstrap/schema/checksum/cleanup portions PASS. Behavioral F002/F004 regression execution and F005 commands remain NOT RUN.
- Durable insertion/rollback, eventual delivery/restart/at-least-once recovery and host authorization remain production integration gates. The 50/51-step boundary requires a later supported graph. No automated tests were created or run.

Final link/whitespace and scoped-diff review is recorded after reconciliation below.

**Final review PASS:** all 96 relative file-link destinations across F005's four records and the four navigation/source documents exist; F005 anchors were reviewed against their headings. All 40 new Java files have no trailing whitespace, and `git diff --check` passed. F005 still has exactly four process documents. Source/JAR review confirms no resource/POM/test-host/test-source change and no additional entity/table, dependency or migration. After bootstrap, the final unsupported-schema error translation and runtime-entity Javadocs were reviewed and repackaged successfully; no binding, query, mapping or resource changed in that final refinement. The staged diff is empty, HEAD remains the starting revision, and PID 40852 is absent. Concurrent F006 revision-2 edits remain untouched except for targeted F005 status/navigation additions in shared indexes.

### Assigned-task review corrections — 2026-10-31

Resolved the two P2 findings without changing the specified value/read contract. Assigned-task queries now combine the existing BIN2 comparison with DATALENGTH(assigned_user_ref) equal to assignee.length() * 2, derived after Unicode/length validation. SQL Server's trailing-space padding can therefore no longer conflate `alice` with `alice `; the predicate is applied in SQL before cursor/limit pagination. Explicit byte count avoids depending on the JDBC parameter's VARCHAR/NVARCHAR binding, and unlike LEN preserves trailing-space distinctions.

Both assigned-task and instance-task page queries now join the activity and return RuntimeTaskRow scalars, including instanceId/taskTypeId and all task-view fields. Mapping uses only that tuple; it no longer calls owner(task) per page item. Existing exact-task/submission reads retain their own scoped ownership checks. SQL limits, ordering, continuation and DTO byte bounds are unchanged. No schema, fixture, migration, dependency or public API change was added. Verification results follow below; T11 remains open.

Review verification **PASS**: Corretto JDK 21.0.13 package with `./mvnw.cmd '-Dmaven.test.skip=true' package` completed at 20:02:05 +02:00 (target/f005-review-build.log). Tests were neither compiled nor run. A read-only SQL Server expression inspection confirmed BIN2 padded equality=1 for `alice ` versus `alice`, byte counts 12 versus 10, and equality plus DATALENGTH=10 returned exact_match=0. This used constants only, no fixtures/application writes.

Packaged-host bootstrap **PASS** at 20:04:00.580 +02:00, validating the revised JPQL/projection construction and JPA schema. No changesets ran; before/after metadata and workflow row counts were identical. Logs/snapshots are ignored target/f005-review-* files. PID 37920 was identified by executable/command/creation time before stopping; a later process check confirmed absence. Source inspection confirms both page mappings use the joined tuple only. Actual populated repository results and a measured 100-task query count remain **NOT RUN**; no adapters/fixtures were added to close T11. Whitespace/new-file review passed; nothing was staged or committed.

## 5. Known Limitations

Only START → HUMAN_TASK(SINGLE) → END executes. ALL, cancellation, reassignment, variables/results, expressions, service tasks/gateways, runtime HTTP/UI, production adapters and F006 implementation are excluded. Current-state projections can represent stored enum values without enabling those mutation paths.

Host code must authenticate/authorize every command, replay and read; provide actual stable resolver/task capability support, derive host-start fingerprints from all association inputs, write links and durable events in the same effective transaction, complete rollback before recovery reads, and report success only after commit. Core cannot certify adapter durability or that a host digest covers its business inputs. No transaction, race, codec round-trip or rollback guarantee is claimed from packaging/bootstrap.

## 6. Remaining Work

**T11 is still open/BLOCKED.** Install separately authorized real host adapters/facade and inspectable durable storage (potentially the separately specified F006 work), then record all missing AC01–AC14 observations, including real races, rollback, fresh-transaction replay, final version comparison and same-transaction submissions. Do not close T11 using static inspection, an in-memory sink or management-only fixtures.

The selected core implementation is complete and the handover records the remaining acceptance gate. No contract change, staging, commit or push was performed; concurrent F006 edits remain preserved. No verification data was created and no owned host remains running.
