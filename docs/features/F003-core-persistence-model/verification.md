# F003 verification guide

Applies to [F003 revision 1](spec.md). Documentation preparation performed no build, host startup, database access or automated tests. The subsequent owner-authorized implementation session performed packaging, local startup and read-only metadata inspection as recorded in [evidence.md](evidence.md). The no-tests policy remains in force.

## 1. Static mapping and boundary review

For each of the eight new mappings, compare every field with the [F001 dictionary](../F001-initial-database-schema/schema-contract.md#column-dictionaries) and actual initial changelog. Record coverage in implementation.md rather than assuming entity discovery proves completeness.

Check identifiers, generated versus assigned identity, both composite keys, enum strings, nullability, Unicode, declared lengths, DECIMAL(38,10), BINARY(32), UUID, DATETIME2(7), DATETIMEOFFSET(7), complete validation envelope and the legacy read-only field. Confirm only four tables have @Version, counting existing F002. Verify no duplicate column mapping or invented relationship/column exists.

Inspect fixed JPQL predicates, embedded property paths, cursor comparisons, stable ordering and database pagination. Inspect read-only REQUIRED transaction annotations, repository method surfaces and absence of bulk mutation/locking APIs. Verify hash getters copy arrays and entities have no generated mutable equality/setters or sensitive payload toString. Preserve all F001 resources and F002 semantics.

This covers static portions of AC01, AC04, AC06–AC09. It does not execute hydration, query results or transaction behavior.

## 2. Build after implementation

Confirm an installed JDK 21 and explicitly select it in JAVA_HOME for the current shell. Record `java -version` and wrapper version, then run from the repository root:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

No automated tests are created, compiled or run. Do not use plain package, test or verify. Inspect the packaged core JAR for expected new classes and existing changelogs, and the ordinary-library/host packaging boundary. Record build failures as failures until fixed and rerun; preserve the failed attempt in evidence when relevant.

## 3. Local startup and read-only inspection, when in scope

Use the existing documented development host/database and root scanning configuration. Check current target/configuration before connecting. Startup runs Liquibase and is not a read-only action; it belongs only to the later implementation task when its scope includes local runtime verification. F003 supplies no new migration.

- Record the selected artifact/JDK, effective target and relevant environment overrides without exposing secrets.
- Capture before metadata where permitted: F001 tracking identities/checksums and workflow table/constraint/index metadata. Do not assume a later database has the same empty data as historical F002 evidence.
- Start a temporary host using the README's command, Windows hidden-window convention and a recorded process ID/command/creation time. Save output under an ignored feature-specific target directory.
- Confirm discovery of nine entities and nine repository interfaces, including the existing task catalog; embeddables do not add entity counts. Confirm actual schema validation and successful EntityManagerFactory/application startup. Counts alone are insufficient; identify the mapped types and target tables.
- Confirm Liquibase applied no new F003 changeset and existing identities/checksums remain unchanged. Inspect physical type/precision/collation details where Hibernate does not validate them fully. Do not issue DML or create fixtures.
- Stop only the process started for this check after verifying its executable, command and creation time. Confirm it is absent and record final process state.

Do not disable validation, alter tracking rows, clear checksums, rewrite F001 or reset the database to obtain a successful result. Record an unavailable environment or schema mismatch as BLOCKED/FAIL with the affected checks. If runtime inspection is outside scope, use NOT RUN with that reason.

This can establish fresh AC02 evidence and physical parts of AC01/AC09. A successful startup against empty tables does not establish hydration or query correctness.

## 4. Behavioral evidence remains separate

AC03–AC05 need representative rows and executed reads to establish lookup, paging and exact-value behavior. F003 does not authorize fixture DML or automated tests. Read-only observations of suitable existing development records may be recorded when in scope, without exposing payloads; otherwise leave those scenarios NOT RUN. Do not create a hidden startup runner, command-line test harness or permanent endpoint to bypass the current no-tests policy.

Concurrent writes, optimistic conflict handling, parent claim timing, final managed-version capture, command rollback/replay, authorization, append-only production behavior and durable delivery are not implemented here. Do not claim them from annotations, compilation or schema validation. Preserve these future obligations under ASM-02/03/04/10/11.

## 5. Documentation and final state

Reconcile spec, plan, tasks, implementation and evidence. Check local links, new-file whitespace and `git diff --check`. Distinguish static PASS, executed PASS, FAIL, NOT RUN and BLOCKED. Report whether files are committed and whether any temporary process remains. Do not stage or commit without a request.
