# Implementation record — F030

## Authorization and delivery

The owner approved [specification revision 1](spec.md) and its [plan](plan.md) on 2026-11-19, completing T01. Implementation remained deferred at design approval. In a separate implementation request on 2026-11-19, the owner authorized [T02–T04](tasks.md) for revision 1.

The 18-event inventory was traced to runtime event emission and envelope construction at `7d0b3b0`. Design review covered assignment and completion examples, writer lookup, receipt replay and the distinction between mocked command tests and the real handoff. These were source and documentation checks.

F030 is IMPLEMENTED. The core and sandbox use `WorkflowEventBatchWriter.write(List<WorkflowEvent>)`. The [core integration guide](../../../workflow-core/README.md) covers host migration, writer lookup, replay and delivery retries, and links to the 18-event catalog. Hosts using the prior SPI must update their adapters and rebuild. Existing serialized events and database rows require no migration.

## Verification

- **PASS — reactor compilation.** On Windows with Corretto 21.0.13 and Java release 17, the root Maven wrapper compiled main and test sources for core, web and the test host, including migrated integration fixtures. The first attempt hit the sandbox's Maven cache lock restriction before compilation. The offline retry compiled core and its tests but stalled at Mockito's dynamic attach. It was interrupted, and the offline run with an explicit Mockito startup agent completed successfully.
- **PASS — 24 focused unit tests.** `RuntimeHostSupportUnitTest` passed both real-handoff checks. A recording writer received one complete batch in order. A failing writer's exception escaped as `HOST_CONFIGURATION` with the original cause. `RuntimeCommandsUnitTest` passed all 22 cases with mocked host support.
- **PASS — A01/A03 source review.** `RuntimeHostSupport` still requires a single registered writer when execution requests the adapter. Start, completion, ownership, reassignment and cancellation return a valid replay before requesting the writer. Lookup-only paths stay receipt-only. The sandbox writer retains `MANDATORY` transaction propagation, ordered serialization and insertion. The core handoff retains exception translation and its position after receipt flush. Java sources contain no old SPI references.
- **PASS — A02 catalog and links.** Matched 18 catalog entries to the command, progression and variable producers and checked trigger, payload and optional-reference rules. `RuntimeHistoryBatch` emits version 3 for `TASK_CREATED` and version 1 for the other 17, independently of history details versions. Checked the direct and GROUP payloads, the APOLO association contract and authorized instance/task reads, and the contribution/resolution order. Parsed the example JSON. Rendered the event and writer API Javadoc on JDK 21 with the standard doclet and doclint. Resolved the link from generated `WorkflowEvent.html` to the copied catalog and the core README link to the source catalog. Generated files are under `workflow-core/target/f030-catalog-docs`.
- **REUSED — F021 revision 2 guarded SQL rollback evidence** from [F021 delivery at `9fb2e5a`](../F021-all-collective-review/implementation.md#actual-verification). The final/early ALL resolution rollback cases in `AllCollectiveReviewIT` failed the writer after transactional outbox inserts and downstream effects. They verified rollback of the resolving command and preservation of earlier contributions. F030 preserves the mandatory transaction boundary, receipt flush/handoff order, outbox SQL, serialization and exception translation, so that evidence applies to rollback at this boundary.
- **NOT RUN — fresh database and APOLO checks.** The focused checks found no change or failure implicating the transaction or outbox path. No database was accessed and no host process was started. APOLO production integration remains separate.

Successful reactor command from the repository root, with `JAVA_HOME` set to Corretto 21 and `$agent` set to the cached `mockito-core-5.14.2.jar`:

```powershell
./mvnw.cmd -o -B -ntp '-Daether.syncContext.named.factory=noop' "-DargLine=-javaagent:$agent" '-Dtest=RuntimeHostSupportUnitTest,RuntimeCommandsUnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Focused self-review covered the mechanical SPI rename, sandbox transaction boundary and catalog. Final diff, link and whitespace checks passed. The existing roadmap edits and other unrelated worktree changes were preserved.
