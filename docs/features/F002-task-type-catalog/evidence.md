# F002 evidence

Historical scope: specification revision 1. Status: **revision 1 IMPLEMENTED; authorized build/startup/read-only verification PASS** on 2026-10-25. **AC01–AC09 behavioral execution NOT RUN.** Changes are uncommitted. Revision 2 relocation and its fresh verification are recorded separately below. The [current specification](spec.md) is revision 3; its completed concrete-service simplification and fresh verification are appended separately below.

## Revision 3 convention preparation — historical documentation-only step

The owner selected concrete services by default, with interfaces reserved for actual integration/substitution requirements. The package guide, coding standards, agent instructions and F002 contract now specify TaskTypeCatalogService directly, without the former interface/impl pair. T12/T13 track implementation and fresh verification. This update changes documentation only; Java sources and earlier evidence are preserved. No build, application startup or database checks were run for this update.

## Revision 2 convention preparation — historical documentation-only step

The owner selected technical layers first, with features underneath and `dto` terminology. The [package guide](../../package-structure.md) and revised spec/plan define exact assignments; T10/T11 track relocation and verification. No Java files were moved or modified for this documentation update, and no build, application or database check was run. Earlier evidence below applies to the original package layout.

## Baseline and environment

- Implementation began at workflow-engine commit `cbea3a40d5c9d0cc728b02f6ab0b09eedbe58ba5` with a clean working tree. F001 prerequisite commit remains `4a7bf64c632b6e07216a5b285265f5ab5a0387ec`. Earlier F002 preparation was documentation only; its historical no-execution statements do not describe this implementation run.
- Read the pinned database design v1.4 section 8.1 at apolo-docs HEAD `c32ffb07cc9f4f2f3d1d7bf087455c1af13acbc3`, F001/F002 contracts and repository guidance. No material discrepancy was found.
- Windows 11 amd64; Amazon Corretto JDK 21.0.13+10-LTS; Maven wrapper 3.3.4 selecting Maven 3.10.0; Java compilation release 17. Spring Boot 3.4.5, Hibernate 6.6.13.Final, Liquibase 4.29.2, JDBC 12.8.1.jre11 and matching native DLL 12.8.1.x64. Added direct parent-managed Hibernate and Spring Data Commons dependencies for types used in core.
- Target: localhost:1433/apolo_workflow_dev, Windows integrated authentication. WORKFLOW_TEST_DB_URL, SPRING_DATASOURCE_URL, Spring config/profile overrides, JAVA_TOOL_OPTIONS and JDK_JAVA_OPTIONS were absent. Host configuration remains unchanged, including JPA ddl-auto=validate and the single datasource/migration runner.
- SQL Server Express 16.0.1000.6, compatibility 160, Latin1_General_CI_AS; confirmed read-only with sqlcmd. Detailed logs/catalog exports and provider inspection output are under ignored workflow-test-host/target/f002-verification/.

## Static implementation review — PASS (T01–T06; R01–R07)

Supported API: TaskTypeCatalog in core.catalog plus immutable create/update/query/view/change/page records, filter enum and categorized exception. Persistence lives in core.persistence.catalog. No supported API exposes entities, repositories, Pageable/Page or provider objects. Page items are copied, snapshots contain immutable values, create has Optional.empty before, and no-op returns equal snapshots.

Reviewed all five operations against the spec:

- Validation runs before persistence/no-op: non-null commands/query/filter, positive ID, nonnegative expectedVersion/cursor, limit 1–100, full-string ASCII code grammar with length 1–100, name isBlank and 200 UTF-16-unit bound, nullable description with 2000-unit bound. No trimming, normalization or truncation. Null description replaces/clears it; empty remains distinct.
- Exact reads include nonselectable rows and valid missing identities return Optional.empty. Cursor JPQL filters id > afterId and selectability in the database, orders ID ascending and receives a PageRequest limited to limit+1. No count query or unbounded service query. nextAfterId uses the last returned ID only if another match was fetched.
- Six-column entity mapping: generated Long IDENTITY, non-updatable VARCHAR(100) code, Hibernate @Nationalized NVARCHAR(200)/NVARCHAR(2000), BIT selectable, nullable Java Long @Version on non-null BIGINT lock_version. No manual ID/version assignments, relationships, cascades or mutable-state equality/toString. Entity metadata mutation and snapshot methods are package-private; no code setter.
- Mutation entry points have MANDATORY transactions on the managed implementation; reads use read-only REQUIRED transactions. Core never creates an independent write commit. Host-owned authorization/audit/commit and provisional results are documented in the public interface and [README integration example](../../../README.md#task-type-catalog).
- Creation uses persist plus explicit flush inside one translation boundary, including an IDENTITY insert failure during persist. No advisory duplicate query, upsert, retry or recovery query after failure.
- Update validates, loads, rejects missing rows, snapshots and checks expectedVersion before checking all mutable values for exact equality. Real changes dirty the loaded entity and flush before returning the actual provider-managed version. No bulk update, merge, detached save, forced increment or expectedVersion+1 calculation. No-op performs no catalog mutation/explicit flush and does not claim to reserve state.
- TaskTypeCatalogException exposes stable INVALID_INPUT, NOT_FOUND, DUPLICATE_CODE, STALE_VERSION and STORAGE_FAILURE categories with bounded fixed field context; messages do not echo input or SQL. Causes are retained for internal diagnosis. Jakarta/Spring/Hibernate optimistic failures are recognized through cause chains. Duplicate handling requires the named uq_workflow_task_type_code constraint, with a SQL Server 2627/quoted-constraint fallback through chained SQL exceptions. Other persistence failures remain STORAGE_FAILURE. Translated failures are unchecked and leave joined-transaction rollback semantics intact.

Inspected the installed Hibernate 6.6.13.Final classes with javap: StaleStateException extends HibernateException, which extends Jakarta PersistenceException; SQLServerDialect extracts unique constraint/index names for 2627/2601, and ConstraintViolationException exposes getConstraintName. This supports static wrapping review, not an executed race result. An initial sandbox dependency read was denied; the approved read-only inspection succeeded. No database failure was manufactured or bypassed.

AC10 static surface/ownership review: **PASS**. No rename/delete/upsert/business versions, endpoints, seeds, task/definition execution, fake audit/security adapter, runtime history/receipt use or migration change. Host authorization and durable audit adapters remain outside this library feature.

## Build and artifacts — PASS (T07; AC11 wiring scope)

Selected JAVA_HOME as installed C:/Users/crist/.jdks/corretto-21.0.13. Commands from repository root:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -version
./mvnw.cmd '-Dmaven.test.skip=true' package
& "$env:JAVA_HOME/bin/jar.exe" tf workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar
Get-FileHash workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
```

Package ran with approved Maven-cache access, exit 0, **BUILD SUCCESS**, 10.441 seconds, finished **2026-10-25T19:08:07+02:00**. Test resources were not copied, test sources not compiled and tests skipped. No automated tests were created or run; the existing test source is preserved.

Core JAR includes the catalog API/implementation/entity/repository and unchanged F001 resources. Host artifact: workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar, SHA-256:

`D124DA9C9C0009597C5867431C0C505B81F76845B4FABD5A07C8BC39BD578F31`.

## Authorized startup — PASS (T08; AC11)

Before startup, read-only catalog inspection confirmed the existing six columns, identity, nullability, types/collations, clustered PK and unique code constraint/index; catalog row count **0**. All F001 bookkeeping matched its evidence. No prerequisite repair or new migration was needed.

Started the selected JDK bin/java.exe with Start-Process -WindowStyle Hidden -PassThru, explicit repository working directory, RedirectStandardOutput to target/f002-verification/host.out.log and RedirectStandardError to host.err.log. Exact argument list:

```text
-Djava.library.path=workflow-test-host/target/native -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar --logging.level.org.hibernate.boot.model.internal=DEBUG --logging.level.org.hibernate.tool.schema=DEBUG
```

PID **15092**, created **19:08:35** local. Logs record **1 JPA repository interface**, bind TaskTypeEntity to workflow_task_type, bind all six mapped fields, initialize the JPA EntityManagerFactory with unchanged ddl-auto=validate, initialize repository query infrastructure, and complete startup at **19:08:47.399+02:00** (11.093 seconds, port 8080). No validation/bean initialization errors; stderr empty. Successful startup includes construction of the catalog service with its repository/shared EntityManager dependencies. This is wiring/mapping evidence only.

Liquibase: **database up to date, Run: 0 / Previously run: 2**. No catalog API was invoked to create/update data. No fixture/throwaway records, automated tests or new startup runner were used.

## Read-only database and tracking comparison — PASS (T08; AC11)

Reused the local ignored F001 catalog.sql inspection from its runbook, exporting ordered full-width JSON of columns, indexes/keys, FK/CHECK definitions/trust, row counts, triggers, tracking records and lock. Exact commands, both exit 0:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f002-verification/before.raw
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f002-verification/after.raw
```

Removed only the output header/surrounding whitespace; parsed via ConvertFrom-Json and inspected catalog fields, row counts and tracking. before.json and after.json have identical SHA-256:

`CB6810C8326739673B7D41813756EDA857A16A2EA3EF89BDE6F9FEC4DBD9FFA5`.

This covers all exported workflow schema/tracking metadata, not just row count. Catalog remains **0 rows** with the same six columns and keys. Code is VARCHAR(100) Latin1_General_100_BIN2; name/description are NVARCHAR(200)/(2000); description nullable; generated BIGINT IDENTITY(1,1), BIT and ordinary BIGINT version unchanged. No schema object or seed was added by startup.

Tracking schema **dbo**. Exactly one row each, author **workflow-engine**, filename **db/changelog/workflow/changes/001-initial-schema.xml**, EXECTYPE **EXECUTED**, Liquibase **4.29.2**, deployment **2945369603**, unchanged before/after:

- f001-001-create-wf-schema: checksum **9:a759c434c85142e959b6b927a4949d90**, execution **2026-10-25T18:22:49.687**, order **1**.
- f001-002-create-initial-tables: checksum **9:3333ca75046b0857a7a23556e03e0d52**, execution **2026-10-25T18:22:49.853**, order **2**.

Lock row 1 before/after: **LOCKED=false; LOCKGRANTED=null; LOCKEDBY=null**. No new tracking rows; no migration resources or host configuration changed in Git.

Environment version probe, exit 0:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -h -1 -W -Q "SET NOCOUNT ON; SELECT DB_NAME() AS database_name, CAST(SERVERPROPERTY('ProductVersion') AS varchar(40)) AS server_version, CAST(SERVERPROPERTY('Edition') AS varchar(80)) AS edition; SELECT compatibility_level,collation_name FROM sys.databases WHERE name=DB_NAME();"
```

## Cleanup and reconciliation — PASS (T08–T09)

Before Stop-Process -Id 15092, Get-CimInstance Win32_Process confirmed the exact selected Java executable, packaged-host command line and creation timestamp. Wait-Process followed; a subsequent Get-Process check confirmed **PID 15092 absent**. No unrelated process or SQL Server service was stopped.

Updated feature status/tasks, plan/runbook, README usage and project status records. Reviewed code, new files, local documentation links, POM/XML and whitespace; git diff --check passed. Generated logs/artifacts are ignored, no files staged or committed. F001 resources and test-host sources/configuration are unchanged.

## Behavioral execution — NOT RUN

- **AC01:** create, host audit/commit and persisted exact reads.
- **AC02:** live invalid/boundary input execution.
- **AC03:** identical duplicate, case-distinct creation and concurrent duplicate race.
- **AC04:** live update/post-flush version and stale rejection.
- **AC05:** concurrent winning/losing versioned updates.
- **AC06:** live no-op version preservation and stale no-op rejection.
- **AC07:** disable/re-enable/list/read behavior with retained rows.
- **AC08:** live missing-row and multi-page/filter/cursor behavior.
- **AC09:** missing transaction, explicit rollback and required audit-failure rollback.

Implementation paths were reviewed statically; build/startup/read-only inspection does not execute these behaviors. No environmental blocker remains for the authorized verification. Production administration still requires concrete host authorization/durable same-transaction audit, appropriate isolated behavioral evidence, production dependency alignment and future publication/capability checks. Published-workflow execution with nonselectable types is deferred until workflow execution exists.

## Revision 2 relocation and fresh verification — PASS (T10/T11)

Executed on **2026-10-25** after the owner authorized package alignment. This section is fresh evidence for revision 2; the revision 1 results and documentation-preparation history above remain historical.

### Baseline and preservation

HEAD remained `cbea3a40d5c9d0cc728b02f6ab0b09eedbe58ba5`. The worktree already contained the uncommitted revision 1 implementation, POM and documentation changes, revision 2 guidance, and a staged new docs/package-structure.md. Those changes were preserved; no index changes, reset or commit were made. Baseline source copies, process records, logs and metadata exports for this run are under ignored `workflow-test-host/target/f002-revision2-verification/`.

### Relocation review

Moved all **14** existing source files using the exact package-guide assignments:

- TaskTypeCatalog: service.tasktype.
- Seven commands/query/filter/result types: dto.tasktype.
- TaskTypeCatalogException and nested category: exception.tasktype.
- JpaTaskTypeCatalog, TaskTypeValidation and TaskTypePersistenceErrors: service.tasktype.impl.
- TaskTypeEntity: entity.tasktype.
- TaskTypeRepository: repository.tasktype.

Updated declarations, imports, README usage and entity Javadoc. The entity's creation constructor, replaceMetadata and snapshot now have public visibility for cross-layer service access. Its protected JPA constructor and private fields are unchanged; no ID/code/version setter was introduced. Public implementation visibility does not expand the supported host API. Helpers remain package-private. No mapper, facade or duplicate implementation was added.

Source-body comparison against the saved pre-relocation files found every body unchanged after excluding package/import/Javadoc differences and those three visibility changes. Mapping/transaction annotations, JPQL, validation, error translation and no-op behavior are unchanged. WorkflowConfiguration remains at the core root; component scanning and host entity/repository scanning still use that marker. Source/configuration/migration hashes were compared for preservation.

The obsolete source files no longer exist at their original paths. Removed only their **15 generated class files** (including the nested exception category) from the two obsolete package directories under workflow-core/target/classes, after checking absolute paths stayed within that generated target. This prevented stale bytecode from producing duplicate beans/entities in the new artifact; historical logs and other generated evidence were preserved. JAR inspection found exactly the relocated classes and **zero obsolete-package class entries**.

### Build

Toolchain: Windows 11 amd64, Corretto **21.0.13+10-LTS**, Maven wrapper selecting **3.10.0**, compiler release **17**. Dependencies remain the revision 1 baseline: Boot 3.4.5, Hibernate 6.6.13.Final, Liquibase 4.29.2, JDBC/native authentication 12.8.1. No POM change was needed for relocation.

Commands executed from the root with JAVA_HOME explicitly selecting installed Corretto 21:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -version
./mvnw.cmd '-Dmaven.test.skip=true' package
& "$env:JAVA_HOME/bin/jar.exe" tf workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar
Get-FileHash workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
```

The first build at 19:22:31 failed because three exception imports had been rewritten to service.tasktype instead of exception.tasktype. Corrected those imports; no behavior change. The subsequent full prescribed build exited **0**, **BUILD SUCCESS**, at **19:22:53+02:00**, total **8.542 seconds**. Test resources were not copied, test sources not compiled and tests skipped. No automated tests were created or run.

Packaged host SHA-256:

`507A8E742356357586945210F40311BFE6B6D8B00570A89F35153846F196CEE7`.

### Fresh startup and wiring

Confirmed no WORKFLOW_TEST_DB_URL, SPRING_DATASOURCE_URL, Spring config/profile or Java option overrides. Existing host target remained **localhost:1433/apolo_workflow_dev**, Windows native authentication, JPA **ddl-auto=validate**. No host configuration was changed.

Started only PID **23164**, created **19:23:32** local, using selected JDK bin/java.exe, Start-Process -WindowStyle Hidden -PassThru, explicit repository working directory and separate stdout/stderr files under this run's ignored evidence directory. Exact arguments:

```text
-Djava.library.path=workflow-test-host/target/native -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar --logging.level.org.hibernate.boot.model.internal=DEBUG --logging.level.org.hibernate.tool.schema=DEBUG --logging.level.org.springframework.beans.factory.support.DefaultListableBeanFactory=DEBUG
```

Logs confirmed **1 JPA repository interface**, mapped **core.entity.tasktype.TaskTypeEntity** to workflow_task_type, completed EntityManagerFactory initialization with validation enabled, and explicitly autowired **jpaTaskTypeCatalog** to **taskTypeRepository** and **jpaSharedEM_AWC_entityManagerFactory**. Startup completed at **19:23:43.198+02:00** in **9.693 seconds**, port 8080. Stderr was empty. This is new revision 2 discovery/wiring/validation evidence, not reliance on the earlier run.

### Read-only database comparison

Before/after commands reused the existing ignored F001 catalog inspection SQL; both exited 0:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f002-revision2-verification/before.raw
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f002-revision2-verification/after.raw
```

Removed only output headers/surrounding whitespace and parsed the JSON. Both complete exports have SHA-256 **CB6810C8326739673B7D41813756EDA857A16A2EA3EF89BDE6F9FEC4DBD9FFA5**. All exported schema, row counts and bookkeeping are unchanged; the catalog remains **0 rows**. No fixtures, catalog calls or database writes were used for verification. Liquibase startup reported **Run: 0 / Previously run: 2**, database up to date.

Exactly one dbo.DATABASECHANGELOG row per existing identity, author **workflow-engine**, filename **db/changelog/workflow/changes/001-initial-schema.xml**, EXECTYPE **EXECUTED**, deployment **2945369603**:

- f001-001-create-wf-schema: unchanged checksum **9:a759c434c85142e959b6b927a4949d90**, execution **2026-10-25T18:22:49.687**, order **1**.
- f001-002-create-initial-tables: unchanged checksum **9:3333ca75046b0857a7a23556e03e0d52**, execution **2026-10-25T18:22:49.853**, order **2**.

Lock ID 1 before/after: **LOCKED=false, LOCKGRANTED=null, LOCKEDBY=null**. Applied migrations and database objects were not changed.

### Cleanup, handoff and gaps

Verified PID 23164's Java executable, exact packaged-host command and creation time using Get-CimInstance before Stop-Process -Id 23164 and Wait-Process. A later Get-Process query confirmed **PID 23164 absent**. No unrelated process or SQL Server service was stopped.

T10/T11 are complete; current feature/index/usage documentation was reconciled. Final source, diff, local links and whitespace inspection passed, including new source files; generated artifacts remain ignored. The pre-existing staged package guide and other user work remain preserved. No commit was created.

**No revision 2 verification blocker remains.** AC01–AC09 CRUD, boundary, paging, no-op, race and audit/transaction rollback execution remain **NOT RUN**. Production authorization/durable host audit and compatibility gates remain as recorded for revision 1. Relocation/startup evidence does not establish behavioral or production acceptance.

## Revision 3 concrete service and fresh verification — PASS (T12/T13)

Executed **2026-10-25** after the owner supplied the updated concrete-service convention during revision 2 handoff. This section adds fresh revision 3 evidence; historical revision 1/2 results and convention-preparation records remain intact. All work remains uncommitted, including the preserved pre-existing staged package guide and other user changes.

### Implementation and static review

Consolidated the former TaskTypeCatalog interface and JpaTaskTypeCatalog into **core.service.tasktype.TaskTypeCatalogService**. Moved the complete public class/operation contract Javadoc onto the concrete service. Moved TaskTypeValidation and TaskTypePersistenceErrors beside it as package-private helpers. Removed the obsolete source interface and impl package; no compatibility facade or second implementation remains. DTO, exception, entity and repository assignments are unchanged.

Compared the prior implementation/helpers with the new sources, ignoring package/import/Javadoc differences, the service/constructor rename, removal of implements and redundant @Override annotations. All method bodies and transaction annotations are unchanged. The service and its public operations are non-final, constructor-injected and Spring-managed, with no new self-invocation or direct-instantiation call site. Class-level read-only REQUIRED behavior and method-level MANDATORY create/update annotations remain in place. Root WorkflowConfiguration, entity/repository scanning, JPA mappings and Liquibase resources are unchanged.

Updated README usage to inject the concrete service and retained the host authorization, audit, commit and provisional-result requirements. No service interface/substitution abstraction was retained merely for injection. Removed the four obsolete generated service/interface/helper class files from verified paths within workflow-core/target/classes before packaging, so old bytecode could not leave duplicate beans. New JAR inspection found only TaskTypeCatalogService and its two helpers under service.tasktype, and zero old-interface/JpaTaskTypeCatalog/impl class entries.

### Build — PASS

Explicitly selected installed Corretto **JDK 21.0.13+10-LTS**, Maven wrapper selecting **3.10.0**, compiler release **17**, existing dependency versions unchanged. Commands:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -version
./mvnw.cmd '-Dmaven.test.skip=true' package
& "$env:JAVA_HOME/bin/jar.exe" tf workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar
Get-FileHash workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
```

Package exit **0**, **BUILD SUCCESS** at **19:28:18+02:00**, total **9.095 seconds**. Test resources were not copied, test sources not compiled and tests skipped. No automated tests were created or run. Artifact SHA-256:

`33516076189565E3A04516B3E09E761C8A71C885C95C701F26FCA9A99BE45DA0`.

Logs/source snapshots/metadata are under ignored `workflow-test-host/target/f002-revision3-verification/`, separately from prior runs.

### Fresh proxy/wiring/startup — PASS

The existing Windows-authenticated target remained **localhost:1433/apolo_workflow_dev**, JPA **ddl-auto=validate**, with no datasource, transaction manager, migration or host configuration changes. Started PID **5132**, created **19:28:43** local, with selected JDK java.exe, hidden Start-Process, explicit repository working directory, PassThru and separate host.out.log/host.err.log under the revision 3 evidence directory. Exact arguments:

```text
-Djava.library.path=workflow-test-host/target/native -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar --logging.level.org.hibernate.boot.model.internal=DEBUG --logging.level.org.hibernate.tool.schema=DEBUG --logging.level.org.springframework.beans.factory.support.DefaultListableBeanFactory=DEBUG --logging.level.org.springframework.aop.framework=TRACE --logging.level.org.springframework.transaction.annotation=TRACE
```

Fresh logs confirmed:

- **1 JPA repository interface**, TaskTypeEntity bound under core.entity.tasktype to workflow_task_type, completed EntityManagerFactory initialization with existing validation enabled.
- Concrete bean **taskTypeCatalogService** constructor-wired to taskTypeRepository and jpaSharedEM_AWC_entityManagerFactory.
- **CglibAopProxy: Creating CGLIB proxy** for core.service.tasktype.TaskTypeCatalogService.
- Effective transaction metadata: **create/update PROPAGATION_MANDATORY**; **list/findById/findByCode PROPAGATION_REQUIRED, readOnly**.
- Successful startup at **19:28:53.535+02:00**, **8.973 seconds**, port 8080; stderr empty. Liquibase **Run: 0 / Previously run: 2**, database up to date.

These observations establish proxy construction and recognized transaction metadata, not execution of missing-transaction rejection, writes, concurrency or rollback.

### Read-only metadata — PASS

Fresh before/after inspections both exited 0:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f002-revision3-verification/before.raw
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f002-revision3-verification/after.raw
```

Complete extracted catalog JSON exports have identical SHA-256 **CB6810C8326739673B7D41813756EDA857A16A2EA3EF89BDE6F9FEC4DBD9FFA5**. Schema and all exported bookkeeping are unchanged; catalog still **0 rows**, with no fixtures or catalog mutations.

Existing dbo.DATABASECHANGELOG identities remain once each, author **workflow-engine**, filename **db/changelog/workflow/changes/001-initial-schema.xml**, EXECTYPE **EXECUTED**, deployment **2945369603**, unchanged:

- f001-001-create-wf-schema: checksum **9:a759c434c85142e959b6b927a4949d90**, execution **2026-10-25T18:22:49.687**, order **1**.
- f001-002-create-initial-tables: checksum **9:3333ca75046b0857a7a23556e03e0d52**, execution **2026-10-25T18:22:49.853**, order **2**.

Lock ID 1 before/after: **LOCKED=false, LOCKGRANTED=null, LOCKEDBY=null**. No database object or applied migration was changed.

### Cleanup and completion

Verified PID 5132 executable, full command line and creation time before Stop-Process -Id 5132 and Wait-Process. Final Get-Process observation: **PID 5132 absent**; revision 2 PID 23164 remains absent. No unrelated process or SQL Server service was stopped.

T12/T13 completed; revision 3 status, package guide, plan, README and indexes were reconciled. Source/new-file, local-link and whitespace inspection passed, including git diff --check. Index state remains as supplied; no staging or commit was performed. Generated artifacts/logs remain ignored.

**No revision 3 blocker remains.** AC01–AC09 functional CRUD, input boundaries, paging, no-op, concurrent duplicate/stale updates and audit/transaction rollback remain **NOT RUN**. Production host authorization/durable audit and compatibility gates remain open. Successful proxy creation and startup do not establish those behaviors.
