# F001 evidence

Specification: [revision 1](spec.md). Status: **IMPLEMENTED; authorized local schema and restart verification PASS** on 2026-10-25. Changes are uncommitted.

## Baseline and environment

- Started at workflow-engine commit `f5300f3c5fa980d69629e65f6db6aa12ddf336df`, clean working tree. Earlier documentation preparation used `85fc1b1f892bc622be141e1f2b53faf6d27d20b1`; that work did not implement or verify a migration.
- Read database design v1.4 at apolo-docs HEAD `c32ffb07cc9f4f2f3d1d7bf087455c1af13acbc3`, matching the pinned baseline. No contract discrepancy required a decision.
- Windows 11 amd64; Amazon Corretto JDK 21.0.13+10-LTS; Maven wrapper 3.3.4 selecting Maven 3.10.0; Spring Boot 3.4.5; Liquibase 4.29.2; JDBC 12.8.1.jre11 with matching 12.8.1.x64 authentication DLL; Hibernate 6.6.13.Final. Compiler release remains 17.
- SQLCMD.EXE 16.0.1000.6 under the Microsoft SQL Server Client SDK ODBC/170 Tools/Binn directory.
- Confirmed `tcp:localhost,1433/apolo_workflow_dev`, current Windows identity using integrated authentication, no WORKFLOW_TEST_DB_URL override. SQL Server Express 16.0.1000.6, compatibility 160, database collation Latin1_General_CI_AS. Machine/account names are omitted from this shared record.
- Before startup: no wf tables; dbo.DATABASECHANGELOG empty; dbo.DATABASECHANGELOGLOCK row 1 unlocked with null grant/owner. The schema-create changeset subsequently created wf conditionally. No unrelated objects or tracking rows were changed.
- Local logs, read-only inspection SQL, catalog exports and PID records are under ignored `workflow-test-host/target/f001-verification/`. Generated artifacts are not Git deliverables.

## Static implementation review — PASS (AC01/02/05; T01–T03)

Core master includes changes/001-initial-schema.xml once. Stable logical path: `db/changelog/workflow/changes/001-initial-schema.xml`. Exactly two ordered identities, author `workflow-engine`: `f001-001-create-wf-schema`, then `f001-002-create-initial-tables`. Both pending changesets have HALT/HALT preconditions for SQL Server and absence of all nine target tables. Conditional schema creation preserves an existing wf schema. No file-level absence check, MARK_RAN, CONTINUE, runAlways or runOnChange.

Reviewed every column and relational rule against the contract before application. Native SQL preserves explicit collations, named clustered/nonclustered keys, null-safe CHECKs and filtered/covering indexes in normal transactional changesets. No seed DML, cascade actions, extra storage, module or Java behavior. Host master, datasource, sole Liquibase runner and JPA validate remain unchanged.

Unsupported-database/collision rejection was reviewed statically only, as AC05 requires. Deliberate collisions and unsupported database execution were **NOT RUN**. No tracking entries/checksums were edited or failures bypassed.

## Build — PASS (T04)

JAVA_HOME explicitly selected installed `C:/Users/crist/.jdks/corretto-21.0.13`. Commands executed from the repository root:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -version
./mvnw.cmd '-Dmaven.test.skip=true' package
& "$env:JAVA_HOME/bin/jar.exe" tf workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar
& "$env:JAVA_HOME/bin/jar.exe" tf workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
Get-ChildItem workflow-test-host/target/native
Get-FileHash workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
```

Initial sandbox wrapper attempts failed inspecting the Maven cache (Cannot index into a null array); the approved unrestricted invocation resolved Maven. An unquoted PowerShell property attempt failed as an invalid lifecycle phase before compilation. Quoting the same test-skip property passed it intact. Final package exit 0, BUILD SUCCESS at 18:22:18+02:00, 15.166 seconds. Logs confirm no test resources copied, no test sources compiled, tests skipped. **No automated tests were created or run.**

Core JAR contains workflow-master.xml and changes/001-initial-schema.xml at the required classpath paths. Host JAR contains core, Liquibase and JDBC; matching native DLL exists. Artifact: `workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar`. SHA-256 before both starts:

`6FBBBB0124516E8082B531F458A823B680BD9AE11A95333B8E09BEB69BBBD8A5`.

## Local preflight and first startup — PASS (AC01; T05)

Read-only preflight command, exit 0:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -Q "SET NOCOUNT ON; SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name, SUSER_SNAME() AS login_name, SERVERPROPERTY('ProductVersion') AS product_version, SERVERPROPERTY('Edition') AS edition; SELECT compatibility_level, collation_name FROM sys.databases WHERE name=DB_NAME(); SELECT s.name AS schema_name,t.name FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id WHERE s.name=N'wf' OR t.name IN(N'DATABASECHANGELOG',N'DATABASECHANGELOGLOCK'); SELECT * FROM dbo.DATABASECHANGELOG; SELECT * FROM dbo.DATABASECHANGELOGLOCK;"
```

A second version query cast SERVERPROPERTY values to bounded VARCHAR for readable CLI output.

Each startup used Start-Process with the selected JDK bin/java.exe, `-WindowStyle Hidden -PassThru`, explicit repository working directory, separate redirected stdout/stderr under target/f001-verification, and these exact arguments:

```text
-Djava.library.path=workflow-test-host/target/native -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
```

First logs: first.out.log / first.err.log; second: second.out.log / second.err.log. Full executable, command line and creation time were inspected via Get-CimInstance Win32_Process before stopping each owned PID.

First PID **3316**, created 18:22:44 local. Liquibase applied schema (45 ms) then tables (153 ms), reported **Run: 2 / Previously run: 0**, success and lock release. Spring completed startup at **18:22:51.656+02:00**, port 8080. Both startup stderr logs were empty. No migration failure occurred.

## Complete deployed structure — PASS (AC02; T06)

Read-only catalog.sql follows the runbook queries and exports columns, index/key columns, FK columns/actions/trust, CHECK definitions/trust, table row counts, triggers, changelog and lock state as one ordered JSON document. Successful commands, exit 0:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f001-verification/first-catalog.raw
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 5 -t 15 -b -y 0 -w 65535 -i workflow-test-host/target/f001-verification/catalog.sql -o workflow-test-host/target/f001-verification/second-catalog.raw
```

An initial -h -1 / -y 0 combination was rejected by sqlcmd without querying. Successful exports retained unlimited field length. Removed only the output header and surrounding whitespace, parsed via ConvertFrom-Json, and displayed metadata using Format-Table/ConvertTo-Json for manual comparison. These are read-only inspection commands, not automated constraint tests.

Every expected/actual column count matches: definition **7/7**; definition_version **26/26**; task_type **6/6**; instance **14/14**; activity **15/15**; task **11/11**; variable **10/10**; history **15/15**; command_receipt **10/10** — **114 columns** total. Names above omit workflow_ only for readability.

Reviewed each name, type, byte length, precision/scale, nullability, collation and identity against its dictionary. All six surrogate IDs are BIGINT IDENTITY(1,1). The four lock columns are ordinary non-null BIGINT, without child lock columns. Technical keys, code, expression_policy and references use Latin1_General_100_BIN2; other text inherits Latin1_General_CI_AS. NVARCHAR lengths are doubled in catalog bytes; MAX=-1; DECIMAL=(38,10); timestamp scale=7; hashes=BINARY(32). No discrepancy, missing envelope field or extra column found.

### Keys and index manifest

All **24** deployed indexes are enabled with ascending keys and correct uniqueness/clustering; no duplicate key-supplied indexes. Using full actual names:

- Definition: clustered pk_workflow_definition(id); uq_workflow_definition_key(workflow_key).
- Definition version: clustered pk_workflow_definition_version(id); uq_workflow_definition_version_version(definition_id, version).
- Task type: clustered pk_workflow_task_type(id); uq_workflow_task_type_code(code).
- Instance: clustered pk_workflow_instance(id); nonunique ix_workflow_instance_business(business_type, business_id, id) and ix_workflow_instance_definition_status(definition_version_id, status, id). No active-business uniqueness.
- Activity: clustered pk_workflow_activity(id); uq_workflow_activity_activation(instance_id, activation_sequence); uq_workflow_activity_instance_id(instance_id, id); unique ux_workflow_activity_active(instance_id) WHERE status='ACTIVE'.
- Task: clustered pk_workflow_task(id); uq_workflow_task_slot(activity_id, task_key); uq_workflow_task_activity_id(activity_id, id); nonunique ix_workflow_task_open_assignee(assigned_user_ref, created_at, id) WHERE status='OPEN', INCLUDE(activity_id, due_at). Multiple slots and repeated assignees remain possible.
- Variable: clustered pk_workflow_variable(instance_id, variable_key).
- History: clustered pk_workflow_history(instance_id, sequence_number); nonunique ix_workflow_history_command(command_id, instance_id, sequence_number); unique ux_workflow_history_task_completed(task_id) and nonunique ix_workflow_history_activity_completed(activity_id, sequence_number), both WHERE event_type='TASK_COMPLETED'.
- Receipt: nonclustered pk_workflow_command_receipt(command_id); nonunique clustered ix_workflow_command_receipt_instance(instance_id, command_id).

Every UQ is nonclustered. Filter text, key order and included columns were read from sys.indexes/sys.index_columns.

### Foreign-key manifest

All **ten** are enabled/trusted and NO_ACTION for update/delete; every referenced schema is wf:

- fk_workflow_definition_version_definition: definition_id to definition.id.
- fk_workflow_instance_definition_version: definition_version_id to definition_version.id.
- fk_workflow_activity_instance: instance_id to instance.id.
- fk_workflow_activity_task_type: task_type_id to task_type.id.
- fk_workflow_task_activity: activity_id to activity.id.
- fk_workflow_variable_instance: instance_id to instance.id.
- fk_workflow_history_instance: instance_id to instance.id.
- fk_workflow_history_activity_owner: (instance_id, activity_id) to activity.(instance_id, id), in order.
- fk_workflow_history_task_owner: (activity_id, task_id) to task.(activity_id, id), in order.
- fk_workflow_command_receipt_instance: instance_id to instance.id.

No history/receipt FK cycle or APOLO FK exists. Referenced table names above omit workflow_ for readability.

### CHECK manifest and exclusions

All **26** CHECKs have is_disabled=0 and is_not_trusted=0. Every actual definition was read and compared semantically with the required-row-check contract:

- ck_workflow_definition_version_{positive,lifecycle,validation,definition_json,validation_json}: positive versions/revisions; exact publication/retirement presence; seven validation fields all null or populated, PASSED/FAILED, positive validated revision; JSON syntax and 2,097,152/262,144-byte bounds.
- ck_workflow_instance_{business_pair,lifecycle,end_time,history_sequence}: paired nullable correlation; ACTIVE/COMPLETED/CANCELLED result/end/reason presence; end ordering and nonnegative sequence.
- ck_workflow_activity_{range,policy,lifecycle,close_time}: positive activation, count 1–100; SINGLE count 1/null contribution versus ALL non-null contribution; exact lifecycle outcome/closure presence and close ordering.
- ck_workflow_task_{lifecycle,close_time}: OPEN null closure/outcome; COMPLETED required outcome/closure and TASK_OUTCOME; CANCELLED null outcome, required closure and selected reasons; close ordering. Explicit closure_reason IS NOT NULL prevents UNKNOWN bypass. No due_at ordering rule.
- ck_workflow_variable_{value_type,typed_values,json}: seven discriminators, every populated value matches its discriminator, all-null typed values allowed, nullable JSON via ISJSON. No invented variable JSON byte/depth limit.
- ck_workflow_history_{positive,actor,task_activity,completion,json}: positive sequence/schema; USER/SYSTEM; task requires activity; TASK_COMPLETED requires both references; JSON syntax and 262,144-byte bound.
- ck_workflow_command_receipt_{positive,actor,json}: positive result schema; USER/SYSTEM; JSON syntax and 262,144-byte bound.

Braces enumerate actual suffixes, not additional objects. Nullable branches were reviewed explicitly. SQL Server's normalized parentheses/IN expansion preserve the selected AND/OR logic. Activity closure reasons and general event/operation strings remain extensible.

All nine tables are empty. Zero defaults, computed columns or triggers. No prohibited task instance/type/result columns, separate result storage, graph/outcome/association tables or task-type versions. SQL syntax checks do not establish codec semantics or duplicate-key rejection.

## Tracking records — PASS (AC03; T07)

Tracking schema **dbo**. Exactly one record per identity, author **workflow-engine**, filename **db/changelog/workflow/changes/001-initial-schema.xml**, EXECTYPE **EXECUTED**, Liquibase **4.29.2**, deployment **2945369603**:

- f001-001-create-wf-schema: MD5SUM **9:a759c434c85142e959b6b927a4949d90**; DATEEXECUTED **2026-10-25T18:22:49.687**; ORDEREXECUTED **1**.
- f001-002-create-initial-tables: MD5SUM **9:3333ca75046b0857a7a23556e03e0d52**; DATEEXECUTED **2026-10-25T18:22:49.853**; ORDEREXECUTED **2**.

There were no unrelated prior tracking rows. After both starts, lock ID 1: **LOCKED=false, LOCKGRANTED=null, LOCKEDBY=null**.

## Same-artifact restart and cleanup — PASS (AC04/07; T08)

Verified PID 3316 executable, command line and creation time, then Stop-Process -Id 3316. An immediate query briefly saw it during termination; a later query confirmed absence and the second startup bound port 8080 successfully.

Second PID **30688**, created 18:23:37 local; startup completed **18:23:44.468+02:00**. Logs: database up to date/no changesets to execute; **Run: 0 / Previously run: 2**. Artifact SHA-256 unchanged.

Full extracted first/second catalog JSON exports have identical SHA-256:

`CB6810C8326739673B7D41813756EDA857A16A2EA3EF89BDE6F9FEC4DBD9FFA5`.

This includes all inspected columns, keys/indexes, FKs, CHECKs, row counts, triggers, complete changelog records and lock state. Both identity/checksum/date/order/deployment records and schema are unchanged, not merely the tracking row count.

Verified PID 30688 executable, command line and creation time, then Stop-Process -Id 30688. Final Get-Process checks: **3316 absent; 30688 absent**. No unrelated process or SQL Server service was stopped.

## Reconciliation and limits (T09)

Updated feature status/tasks, implementation plan and project documentation to distinguish schema implementation from later runtime work. Reviewed final diff, documentation links, XML well-formedness, new-file trailing whitespace and git diff --check. Generated evidence/artifacts are ignored; nothing staged or committed.

**No local F001 blocker remains.** AC06 simulated unavailable-database/error path was NOT RUN; no actual migration failure occurred. AC05 was static review as specified. No automated tests, adversarial writes, rollback execution, concurrency/idempotency, authorization, expression safety, workflow execution, task-result retrieval, JSON replacement/retention or workload checks were run. Runtime guarantees and production/APOLO integration remain later feature/integration work.
