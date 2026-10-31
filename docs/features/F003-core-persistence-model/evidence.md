# F003 evidence

**Specification:** [revision 1](spec.md). **Implementation:** IMPLEMENTED. **Evidence date:** 2026-10-31. Historical preparation below is retained; current outcomes follow in the implementation verification section.

## Documentation preparation

The owner selected a persistence foundation before definition management/runtime execution, requested specs/plan/tasks, then explicitly stated implementation will be performed by a later Codex agent. This record concerns documentation only.

Inspected engine baseline: `ef265bff8147b68c5096f63b496683e1ac38bbfe`. `git status --short` was empty before preparation. The separate apolo-docs workspace has unrelated existing changes; this feature does not modify them.

Read repository instructions, constitution/constraints/assumptions, development workflow, coding/package guidance, F001/F002 contracts and relevant evidence, current POMs, scanning configuration, host properties, initial DDL and the refined language decisions. Source inspection confirms nine created tables but only the existing task-type Java persistence/service slice. The required legacy expression_policy column remains in F001 although the language no longer exposes policy selection.

Prepared spec.md, persistence-contract.md, plan.md, tasks.md, verification.md, implementation.md and this evidence record, with project navigation links. The files distinguish implementation readiness from implementation and behavioral evidence.

Documentation checks — PASS:

- `git diff --check` reported no whitespace errors in tracked changes. Git emitted its existing LF-to-CRLF normalization notices, not check failures.
- A PowerShell document inspection resolved all 88 relative file links across the seven new documents and four updated navigation/source files. All destinations exist. External links were not fetched; link fragments were reviewed against the relevant headings.
- The same inspection checked all new files for trailing whitespace; none was found.
- Content review confirmed ten requirement IDs, ten acceptance scenarios, nine implementation tasks, consistent package/type counts and explicit distinction between mapped storage and future write/runtime contracts.
- `git status --short` contains only the seven new feature documents and four documentation updates. No Java, POM, properties, migration or test file changed.

## Historical acceptance status at documentation preparation

| Scenario | Status now | Evidence needed later |
| --- | --- | --- |
| AC01 | NOT RUN — no F003 code | Complete mapping inventory and physical compatibility review. |
| AC02 | NOT RUN — no build/startup | Fresh entity/repository discovery and schema validation when in scope. |
| AC03 | NOT RUN — no read execution | Exact and composite-key lookup observations. |
| AC04 | NOT RUN — no query implementation | Query inspection plus separately identified paging execution. |
| AC05 | NOT RUN — no hydration | Exact-value/null/array observations; static defensive-copy review separately. |
| AC06 | NOT RUN — no F003 code | Correct version placement and transaction-boundary review. |
| AC07 | NOT RUN — no F003 code | Surface, encapsulation and F002 preservation review. |
| AC08 | NOT RUN — no F003 mapping | Legacy read-only field inspection and deferred insert gate. |
| AC09 | NOT RUN — no F003 code | Ownership/storage/enum inspection; runtime failures only if observed. |
| AC10 | NOT RUN — implementation handoff pending | Later completed implementation/evidence record and process cleanup. |

## Execution boundaries

No build, automated test, host startup, database connection/query, migration or workflow command was performed. No process was started for verification. No files were staged or committed. F001/F002 historical checks remain historical and do not establish F003 behavior.

Later implementation evidence must append actual commands, code revision, environment, outcomes and artifacts. Leave unexecuted checks visible; do not replace this preparation record with a claim that implementation already existed.

## Implementation verification — 2026-10-31

Owner authorization: implement F003, package with tests skipped, start the local host and inspect apolo_workflow_dev read-only, without fixtures or application-data changes. Baseline HEAD remains `ef265bff8147b68c5096f63b496683e1ac38bbfe`; code and feature records are uncommitted. Seven feature documents were staged before this session; four navigation/source documents already had unstaged changes. Their prior content and staging were preserved. No test source, F002 implementation, POM, host configuration or migration resource changed.

### Static review and package — PASS

- Read project guidance, F003 contracts and F001 dictionaries/DDL; inspected current F002 mapping/repository/service boundary, POMs, host properties and root scanning. The per-table inventory in implementation.md covers all 108 new columns plus the six unchanged F002 columns. All required fields, IDs, nullability, widths and closed enum sets are accounted for. Scalar references retain database ownership checks; exactly four entities have Long @Version including F002. No child entity has one.
- Reviewed the two embedded key constructors/equality, defensive hash getters, absence of setters/builders/entity equality/payload toString, legacy policy insert/update exclusion and all eight repository surfaces. Task/history JPQL has fixed owner/cursor/order predicates, Pageable limits and List returns without counts. Fixed-state exact queries are singular. Every new repository declares readOnly=true and REQUIRED propagation; no lock/write API or infrastructure was added. These are source observations, not executed repository results.
- Selected installed Corretto 21.0.13+10-LTS x64 explicitly via JAVA_HOME, confirmed with `java -version`. Wrapper `-version` reported Maven 3.10.0. Hibernate is 6.6.13.Final, Spring Boot 3.4.5; JDBC driver/native auth remain 12.8.1. Inspected installed Hibernate annotation APIs with javap before choosing local mappings.
- Initial sandbox wrapper invocation failed with `Cannot index into a null array`; rerunning with environment permission reached Maven. The first unquoted PowerShell property was split, producing `Unknown lifecycle phase ".test.skip=true"` before compilation. Corrected invocation: `./mvnw.cmd '-Dmaven.test.skip=true' package`, semantically the prescribed command with PowerShell quoting. Both corrected runs succeeded; final run after Java review finished at 17:42:48 +02:00. Both modules explicitly reported no test-source compilation and tests skipped; production compilation used release 17. No automated tests were created or run.
- `jar tf workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar` showed eight new entities/repositories, two embedded IDs, nine enum classes and the existing catalog/changelogs. Core remains an ordinary JAR without BOOT-INF; Maven repackaged only the host. Local build log: `target/f003-build.log` (ignored).

### Local host and read-only metadata — PASS

No WORKFLOW, SPRING, SERVER_PORT, JAVA_TOOL_OPTIONS or JDK_JAVA_OPTIONS overrides were present. The unchanged host properties target localhost:1433/apolo_workflow_dev using Windows native authentication, encrypted transport with the local certificate trusted, and ddl-auto=validate.

Before and after startup, executed:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 10 -b -W -s '|' -i target/f003-metadata.sql -o target/f003-before.txt
# Repeat with -o target/f003-after.txt after startup.
```

The ignored SQL file contains SELECTs only: DB_NAME, DATABASECHANGELOG identities/checksums, sys.tables/columns/types, FK actions/trust, CHECK definitions/trust, index columns/order/filter/clustering and partition row counts. Source review and catalog comparison confirmed F001 type/length/nullability/identity metadata, decimal precision 38/10, timestamp scale 7, fixed binary length 32, uniqueidentifier, nationalized max text, BIN2 keys, ten NO ACTION trusted/enabled FKs and the existing constraints/filtered completion indexes. All nine workflow tables had zero rows before and after. No representative hydration/paging observations were possible, and no fixtures were created.

Started the packaged host with the selected JDK using Start-Process -WindowStyle Hidden and these arguments:

```text
-Djava.library.path=workflow-test-host/target/native -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar --server.port=0 --logging.level.org.hibernate.boot.model.internal.EntityBinder=DEBUG --logging.level.org.springframework.data.repository.config=DEBUG
```

PID 29468 was created at 17:43:01.870 +02:00; its executable, command and creation time were recorded in ignored `target/f003-process.json`. Random HTTP port selection avoided disturbing other processes. Logs in `target/f003-host.out.log` and `target/f003-host.err.log` show nine repository interfaces, nine explicit entity-to-table bindings (the eight new Workflow*Entity classes plus TaskTypeEntity), successful EntityManagerFactory initialization after schema validation, and application startup at 17:43:12.970 +02:00. Repository query construction completed without errors. No permanent diagnostic runner, endpoint or test harness was added.

Liquibase reported zero changesets run, two previously run and database up to date. Existing identities remained:

- f001-001-create-wf-schema / workflow-engine / db/changelog/workflow/changes/001-initial-schema.xml — `9:a759c434c85142e959b6b927a4949d90`.
- f001-002-create-initial-tables / workflow-engine / same path — `9:3333ca75046b0857a7a23556e03e0d52`.

Compare-Object found identical before/after metadata and row counts. Both snapshot files have SHA-256 `67749B11A62DB86C79DC6547BA1F393DE34A114286995B50987B80EBCBAF1D63`. This includes schema/migration metadata, not proof of runtime transactional guarantees. No application DML or new migrations were issued.

Before Stop-Process -Id 29468, compared the live CIM executable, command, creation time and PID with the recorded identity. Only that verified process was stopped. An immediate process query briefly showed an exiting zero-handle process; a subsequent query confirmed PID 29468 absent. No owned temporary host remains running.

### Current acceptance disposition

- AC01 — PASS for source/physical metadata and startup compatibility; actual value round trips NOT RUN.
- AC02 — PASS: nine identified entity/table bindings and nine repositories; successful validation/startup, including F002.
- AC03 — NOT RUN: exact lookup/key/case behavior has source review only; no repository reads with representative records.
- AC04 — PASS for static cursor/order/limit surface and query construction; paging result execution NOT RUN.
- AC05 — PASS for static nullable/exact type/defensive-copy design; hydration, offsets, Unicode, decimal and binary round trips NOT RUN because no representative rows exist and fixture DML/tests are prohibited.
- AC06/AC07/AC08 — PASS for specified static version/boundary/surface/legacy mapping checks. Concurrent writes, authorization and definition insertion remain outside F003.
- AC09 — PASS for source/catalog ownership, checks and enum mapping; unknown persisted enum failure execution NOT RUN.
- AC10 — PASS: feature records reconciled, temporary process stopped, changes left uncommitted.

No lookup, round-trip, conflict, rollback/replay, final version capture, authorization, append-only enforcement, durable delivery or production readiness claim follows from these checks. Definition writes still require the separately specified legacy-column migration/transition; future runtime and JSON codecs retain their documented gates. Final whitespace/new-file/link review is recorded below.

### Final document and source review — PASS

`git diff --check` passed. Separately inspected all 27 untracked Java files for trailing whitespace: no findings. Checked 97 relative file links across the seven feature documents and four navigation/source documents: all destinations exist. Source review accounted for exact annotations and query paths; a scoped search found exactly four @Version annotations and no new relationship, CRUD, setter, builder or locking surface. F001 resources, F002 source, host configuration, POMs and tests have no diff. Git's existing LF-to-CRLF notices are normalization notices, not failures. Final state: T01–T09 complete for their specified implementation/verification dispositions, no material conflict, no owned host process, no staging/commit/push by this session. Previously staged documentation remains staged at its original preparation content; subsequent updates and new Java source remain unstaged.
