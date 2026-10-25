# F001 local verification runbook

Use this during **implementation**, not while preparing feature documentation. Scope: local `localhost:1433/apolo_workflow_dev`, Windows authentication, package with tests skipped, two temporary host startups and read-only SQL inspection. Follow [AGENTS.md](../../../AGENTS.md) and the [project README](../../../README.md). Do not run automated tests or create fixture rows to exercise constraints.

## 1. Confirm the environment and preserve the baseline

Inspect Git state and existing F001 files before changing anything. Read the effective POMs and host settings, including overrides such as `WORKFLOW_TEST_DB_URL`. Confirm that the effective connection still targets the authorized local development database. Do not start the host against a different environment without owner authorization. Do not print credentials.

Select an installed JDK 21 through `JAVA_HOME` and verify it and Maven before packaging:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -version
Get-Command sqlcmd -ErrorAction SilentlyContinue
```

If `sqlcmd` is not on PATH, locate the installed SQL Server command-line utility and invoke its verified absolute path. Record the resolved version/path. Do not change system PATH or install tools without an identified need. The connection probe is read-only:

```powershell
sqlcmd -S 'tcp:localhost,1433' -d 'apolo_workflow_dev' -E -C -l 5 -t 15 -b -Q "SET NOCOUNT ON; SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name, SUSER_SNAME() AS login_name, SERVERPROPERTY('ProductVersion') AS product_version, SERVERPROPERTY('Edition') AS edition;"
```

`-E` uses the current Windows identity; `-C` accepts the local development certificate; `-b` makes SQL errors fail the command. Record the exit code. An unreachable database is a blocker for startup/inspection, not permission to start services, change authentication or reset the database. Continue independent authoring/build work and record remaining checks as BLOCKED/NOT RUN.

Before the first startup, capture target-table existence and discover Liquibase's tracking schema:

```sql
SELECT s.name AS schema_name, t.name AS table_name
FROM sys.tables t JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE s.name = N'wf'
   OR t.name IN (N'DATABASECHANGELOG', N'DATABASECHANGELOGLOCK')
ORDER BY s.name, t.name;
```

If tracking tables exist, inspect their current records before startup. If any target table already exists but the matching F001 identities do not, stop the apply step and report the collision. Do not erase or silently adopt it. If both identities already exist, verify their schema/checksums and report that clean first-application evidence was unavailable; do not claim to have observed initial creation. Preserve all pre-existing objects/records.

## 2. Build and inspect the artifact

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
& "$env:JAVA_HOME/bin/jar.exe" tf workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar
```

Check the exit code, effective versions and the two core changelog resource paths. Verify the packaged host JAR and matching native authentication DLL under `workflow-test-host/target/native`. Use the current POM artifact/version if it has changed; record the actual artifact instead of copying a stale path. Do not run `test` or `verify`, or omit the test-skip option.

## 3. Start the packaged host

From the repository root, the current launch command is:

```powershell
& "$env:JAVA_HOME/bin/java.exe" '-Djava.library.path=workflow-test-host/target/native' -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
```

For managed background verification, use `Start-Process -WindowStyle Hidden -PassThru` with the equivalent arguments, explicit repository working directory and separate redirected stdout/stderr files under ignored `workflow-test-host/target/f001-verification/`. Record the PID and full command identity. Check logs/process state at bounded intervals, and capture both Liquibase completion and Spring Boot's completed startup. Do not add a health endpoint/dependency just for this check. Never kill an unrelated process to free a port.

Successful startup alone is insufficient: there are currently no workflow entities to make Hibernate validate all nine table definitions. If startup fails, preserve the failure evidence, inspect actual migration/tracking state and stop owned processes. Do not disable validation, weaken constraints or edit tracking records to bypass the failure.

## 4. Inspect the actual database structure

Use `sqlcmd` with the same connection options. Read-only SQL can be saved under the ignored verification directory and passed with `-i`; use `-o` plus sufficiently wide output, or another lossless format, so CHECK/filter definitions are not truncated. These catalog queries are inspection tools, not automated tests. Compare their results with every entry in [schema-contract.md](schema-contract.md).

Column/identity metadata:

```sql
SELECT t.name AS table_name, c.column_id, c.name AS column_name,
       ty.name AS sql_type, c.max_length, c.precision, c.scale,
       c.is_nullable, c.collation_name, c.is_identity,
       ic.seed_value, ic.increment_value,
       dc.definition AS default_definition, c.is_computed
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.columns c ON c.object_id = t.object_id
JOIN sys.types ty ON ty.user_type_id = c.user_type_id
LEFT JOIN sys.identity_columns ic
  ON ic.object_id = c.object_id AND ic.column_id = c.column_id
LEFT JOIN sys.default_constraints dc ON dc.object_id = c.default_object_id
WHERE s.name = N'wf'
ORDER BY t.name, c.column_id;
```

`max_length` is in bytes: NVARCHAR lengths are doubled and MAX is `-1`. Compare names and types, not just counts. Expected column counts are definition 7; definition_version **26**; task_type 6; instance 14; activity 15; task 11; variable 10; history 15; command_receipt 10. Dictionary ordering is not a runtime contract.

Keys/indexes, including key order, filters and includes:

```sql
SELECT t.name AS table_name, i.name AS index_name, i.type_desc,
       i.is_unique, i.is_primary_key, i.is_unique_constraint,
       kc.name AS constraint_name, kc.type_desc AS constraint_type,
       i.is_disabled, i.has_filter, i.filter_definition,
       ic.key_ordinal, ic.index_column_id, c.name AS column_name,
       ic.is_included_column, ic.is_descending_key
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.indexes i ON i.object_id = t.object_id
LEFT JOIN sys.key_constraints kc
  ON kc.parent_object_id = i.object_id AND kc.unique_index_id = i.index_id
JOIN sys.index_columns ic
  ON ic.object_id = i.object_id AND ic.index_id = i.index_id
JOIN sys.columns c
  ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE s.name = N'wf' AND i.index_id > 0
ORDER BY t.name, i.name, ic.is_included_column, ic.key_ordinal, ic.index_column_id;
```

Foreign-key definitions and trust status:

```sql
SELECT pt.name AS table_name, fk.name AS fk_name,
       fkc.constraint_column_id, pc.name AS parent_column,
       rs.name AS referenced_schema, rt.name AS referenced_table,
       rc.name AS referenced_column,
       fk.delete_referential_action_desc, fk.update_referential_action_desc,
       fk.is_disabled, fk.is_not_trusted
FROM sys.foreign_keys fk
JOIN sys.tables pt ON pt.object_id = fk.parent_object_id
JOIN sys.schemas ps ON ps.schema_id = pt.schema_id
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns pc
  ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
JOIN sys.tables rt ON rt.object_id = fk.referenced_object_id
JOIN sys.schemas rs ON rs.schema_id = rt.schema_id
JOIN sys.columns rc
  ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
WHERE ps.name = N'wf'
ORDER BY pt.name, fk.name, fkc.constraint_column_id;

SELECT t.name AS table_name, cc.name AS check_name,
       cc.definition, cc.is_disabled, cc.is_not_trusted
FROM sys.check_constraints cc
JOIN sys.tables t ON t.object_id = cc.parent_object_id
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE s.name = N'wf'
ORDER BY t.name, cc.name;
```

Confirm ten distinct FKs, including the two composite history ownership constraints, with NO_ACTION and enabled/trusted status. Read each CHECK definition and compare its semantics, including nullable branches, JSON syntax/bytes, lifecycle fields and typed-null behavior. Verify all required unique/filtered/covering indexes and the receipt's nonclustered PK. Confirm there are no extra application tables/columns, defaults, computed columns or triggers introduced by F001. Existing unrelated `wf` objects must be distinguished from objects introduced by this migration. Confirm the migration contains no seed DML; on a clean first application the nine new tables should be empty.

## 5. Inspect DATABASECHANGELOG and DATABASECHANGELOGLOCK

Use the schema discovered in step 1; do not assume Liquibase's tracking tables are under `wf`. The following examples use `dbo` and must be adapted to the actual verified schema:

```sql
SELECT ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED,
       EXECTYPE, MD5SUM, LIQUIBASE, DEPLOYMENT_ID
FROM dbo.DATABASECHANGELOG
ORDER BY ORDEREXECUTED, DATEEXECUTED, ID;

SELECT ID, LOCKED, LOCKGRANTED, LOCKEDBY
FROM dbo.DATABASECHANGELOGLOCK;
```

Compare the full identity tuple `(ID, AUTHOR, FILENAME)`, not just an ID prefix. Expect exactly one record each for `f001-001-create-wf-schema` and `f001-002-create-initial-tables`, author `workflow-engine`, filename `db/changelog/workflow/changes/001-initial-schema.xml`. Both must have EXECTYPE EXECUTED and non-null checksums, with schema creation ordered before table creation. Pre-existing unrelated rows remain. Capture the actual checksum values, dates and order; do not invent expected checksums or assume absolute order numbers begin at 1. After completed startup, LOCKED must be false/0. A lingering lock needs diagnosis, not a force-clear command.

## 6. Restart without reapplying and clean up

Stop the process started in step 3 after verifying its PID/command identity. Start the same packaged artifact again with the same configuration and record the second process. Require successful startup and logs consistent with zero pending F001 changesets. Repeat schema and tracking inspection: no new F001 rows, unchanged identity/checksum/date/order values, unchanged schema and released lock. Unchanged row count alone does not prove that `runAlways` or `runOnChange` did not rerun a migration.

Stop the second owned process after checking its identity. Use cleanup even when an intermediate check fails, and report both process states. Do not stop SQL Server or unrelated Java processes. Reconcile [tasks.md](tasks.md) and [evidence.md](evidence.md), review `git diff --check` and new files, and report implementation separately from verification. Any unperformed runtime check stays NOT RUN/BLOCKED with a concrete reason.
