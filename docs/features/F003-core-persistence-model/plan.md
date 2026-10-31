# F003 implementation plan

Applies to [specification revision 1](spec.md) and its [persistence contract](persistence-contract.md). Status: implemented on 2026-10-31 after owner authorization. The sequence below records the selected approach; actual completion and verification are in [tasks.md](tasks.md) and [implementation.md](implementation.md).

## Approach

Map the unchanged F001 schema using the existing Spring Data JPA/Hibernate stack. Reuse F002's narrow `Repository` pattern and nationalized text conventions. Keep the root WorkflowConfiguration scanning marker and host infrastructure. No new dependency, module, service, controller, application property or migration is expected.

Use two responsibility groups beneath the established technical layers: `definition` for definition storage and `runtime` for the instance aggregate and its child/history/receipt storage. Keeping runtime persistence in one feature group allows the later runtime service to coordinate these repositories without introducing cross-feature service calls for individual table mutations. F002's `tasktype` group remains unchanged. Subsequent definition-to-runtime interactions must use documented service contracts; the scalar definitionVersionId does not authorize arbitrary cross-feature repository use.

## Exact package and file assignments

Root package: `io.github.leontecristian.workflow.core`. Paths below are relative to `workflow-core/src/main/java/io/github/leontecristian/workflow/core/`.

| Package/path | Types to add |
| --- | --- |
| entity/definition | WorkflowDefinitionEntity, WorkflowDefinitionVersionEntity |
| entity/runtime | WorkflowInstanceEntity, WorkflowActivityEntity, WorkflowTaskEntity, WorkflowVariableEntity, WorkflowHistoryEntity, WorkflowCommandReceiptEntity, WorkflowVariableId, WorkflowHistoryId |
| repository/definition | WorkflowDefinitionRepository, WorkflowDefinitionVersionRepository |
| repository/runtime | WorkflowInstanceRepository, WorkflowActivityRepository, WorkflowTaskRepository, WorkflowVariableRepository, WorkflowHistoryRepository, WorkflowCommandReceiptRepository |
| dto/definition | DefinitionStatus, DefinitionValidationStatus |
| dto/runtime | InstanceStatus, ActivityStatus, TaskStatus, CompletionPolicy, TaskClosureReason, VariableValueType, ActorKind |

Each named type has its own matching `.java` file. There are eight new entities, two embeddable key classes, eight repository interfaces and nine enums. With existing F002 there are nine entities/repositories total. Embeddables and enums are not entities.

Do not create empty service, mapper or exception packages. No separate domain/model tree, abstract base entity, generic CRUD service or mirrored interface/implementation pair is needed. The enums represent established domain concepts; entities represent persistence state. An additional value object needs a concrete responsibility rather than a duplicate of a row.

## Implementation sequence

1. Read AGENTS.md, the constitution, constraints, assumptions, development workflow, coding/package guidance and F003 documents. Inspect status, current POMs and F001/F002 code. Record the implementation baseline and preserve unrelated changes. Recheck the actual F001 DDL rather than inferring physical types from an older architecture illustration.
2. Add enums and composite IDs. Use exact persisted strings and the physical key types. Key equality is owner-aware and case-sensitive; no entity-wide mutable equality or new public command model.
3. Add definition mappings, including every lifecycle/validation-envelope field. Keep definition version, content revision, schema version and technical lock version distinct. Map the legacy policy read-only. Add only read access; do not create draft factories or publication methods.
4. Add instance/activity/task mappings. Keep optional correlation paired by the existing CHECK and ownership expressed through scalar IDs/FKs. Map instance @Version without introducing child versions, task multiplicity behavior, assignment methods or lifecycle transitions.
5. Add variable/history/receipt mappings. Cover all typed nullable columns, both composite IDs, assigned UUID receipts, fixed hashes and opaque JSON strings. Preserve extensible categories. Hash getters copy arrays. Do not introduce converters that parse arbitrary JSON or normalize fingerprinted content.
6. Add the exact eight repository interfaces and queries from the persistence contract. Declare read-only REQUIRED semantics, fixed predicates/order and bounded pagination. Use scalar property paths, including embedded-ID paths, in JPQL. No write surface, locks, sequence reservation or global exception translation is introduced.
7. Review every mapping against the F001 dictionary/DDL and record a per-table inventory in implementation.md. Resolve provider details against the existing dependency set, especially long nationalized text, fixed binary, SQL Server UUID and temporal precision. Prefer explicit local field mappings over host-global settings. Do not change storage semantics to satisfy Hibernate's validator.
8. Follow plan.md within the later implementation session's scope. Build only after Java changes. Where local startup/read-only inspection is included, capture new discovery/schema evidence and stop the owned process. Do not reuse F002 evidence as proof of F003 mappings.
9. Reconcile feature records and project indexes. Record implementation and verification separately, leave genuine gaps visible, review the final diff and do not commit unless requested.

## Mapping decisions and rationale

- **Scalar references:** explicit FK identifiers avoid accidental eager loading, lazy proxy exposure, cascades and duplicate writable columns. SQL remains the owner of relational constraints. This feature does not require object navigation.
- **Read-only foundation:** protected JPA construction and getters are enough to establish and inspect mappings. Later services add factories/mutations only when their invariants and transactions are specified. Repositories do not provide generic persistence methods as placeholders.
- **Legacy column:** read existing expression_policy through legacyExpressionPolicy, but exclude it from INSERT/UPDATE. This leaves definition insertion deliberately unsupported until the later write contract/migration resolves the obsolete required column. Do not fabricate a policy ID.
- **Established value types:** enums cover closed SQL vocabularies; open event/operation categories stay strings. No full JSON-language object graph is implied.
- **Temporal representation:** UTC audit values use LocalDateTime for DATETIME2(7); typed offset values use OffsetDateTime for DATETIMEOFFSET(7). Conversion at future public boundaries must be explicit. The machine timezone is never the storage contract.
- **Existing capabilities:** F002 stays the only supported application service after F003. Do not refactor it to share speculative base types with these mappings.

## Schema and dependency impact

No schema change is planned. Preserve the core/host master includes and the complete F001 changeset resource. Do not add a migration merely to support a preferred JPA association, UUID type, enum type or JSON mapping. A discovered incompatibility is investigated and recorded before any scope change.

Keep the current parent-managed framework versions, Java 17 compilation and JDK 21 build/runtime. JPA, Hibernate, Spring transaction and Spring Data dependencies already exist in core. Check local dependency API support when choosing annotations; no JSON Schema validator is required for this feature. No new runtime scanning or datasource setup is expected because the host already scans the root marker.

## Constitution check

| References | Assessment for this plan |
| --- | --- |
| CON-01/02; ASM-01/09 | PASS: existing two-module library/host boundary and dependency baseline; no APOLO compatibility or source-move claim. |
| CON-03/10 | PASS: internal entities/repositories, no endpoints or public reads, logical host references only; no authorization bypass service. |
| CON-04 | PASS: exact F001 mapping with no migration, schema generation, seeds, cascades or changed ownership constraints. |
| CON-05/08 | PASS for foundation scope: retained task catalog and definition lifecycle storage, no runtime semantics inferred from mapped states. |
| CON-06/07; ASM-02/03/04 | PASS for read-only scope: correct version placement, no new command/write/event behavior. Aggregate concurrency, atomic writes and durable delivery remain runtime verification gates. |
| CON-09 | PASS: legacy policy is not a runtime selector; no expression support or policy implementation is claimed. |
| CON-11 | NOT APPLICABLE: no designer or preview. |
| CON-13/14; ASM-10/11 | PASS for storage scope: history/result and variable columns remain distinct and exact; no public codecs, writes, retrieval authorization or JSON evaluation is implemented. |
| CON-12; ASM-08 | PASS: documentation now; later static/build and scoped local verification with no automated tests. Availability and provider behavior require fresh evidence. |

These assessments establish plan compatibility, not evidence that the future implementation satisfies the requirements. Keep unresolved provider observations and runtime obligations in implementation.md.

## Verification procedure

The procedure below is retained from the former verification document. It describes checks, not new execution evidence or authorization beyond the feature/task scope. Actual results are in [implementation.md](implementation.md#4-verification).

Applies to [F003 revision 1](spec.md). Documentation preparation performed no build, host startup, database access or automated tests. The subsequent owner-authorized implementation session performed packaging, local startup and read-only metadata inspection as recorded in [implementation.md](implementation.md#4-verification). The no-tests policy remains in force.

### 1. Static mapping and boundary review

For each of the eight new mappings, compare every field with the [F001 dictionary](../F001-initial-database-schema/schema-contract.md#column-dictionaries) and actual initial changelog. Record coverage in implementation.md rather than assuming entity discovery proves completeness.

Check identifiers, generated versus assigned identity, both composite keys, enum strings, nullability, Unicode, declared lengths, DECIMAL(38,10), BINARY(32), UUID, DATETIME2(7), DATETIMEOFFSET(7), complete validation envelope and the legacy read-only field. Confirm only four tables have @Version, counting existing F002. Verify no duplicate column mapping or invented relationship/column exists.

Inspect fixed JPQL predicates, embedded property paths, cursor comparisons, stable ordering and database pagination. Inspect read-only REQUIRED transaction annotations, repository method surfaces and absence of bulk mutation/locking APIs. Verify hash getters copy arrays and entities have no generated mutable equality/setters or sensitive payload toString. Preserve all F001 resources and F002 semantics.

This covers static portions of AC01, AC04, AC06–AC09. It does not execute hydration, query results or transaction behavior.

### 2. Build after implementation

Confirm an installed JDK 21 and explicitly select it in JAVA_HOME for the current shell. Record `java -version` and wrapper version, then run from the repository root:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

No automated tests are created, compiled or run. Do not use plain package, test or verify. Inspect the packaged core JAR for expected new classes and existing changelogs, and the ordinary-library/host packaging boundary. Record build failures as failures until fixed and rerun; preserve the failed attempt in evidence when relevant.

### 3. Local startup and read-only inspection, when in scope

Use the existing documented development host/database and root scanning configuration. Check current target/configuration before connecting. Startup runs Liquibase and is not a read-only action; it belongs only to the later implementation task when its scope includes local runtime verification. F003 supplies no new migration.

- Record the selected artifact/JDK, effective target and relevant environment overrides without exposing secrets.
- Capture before metadata where permitted: F001 tracking identities/checksums and workflow table/constraint/index metadata. Do not assume a later database has the same empty data as historical F002 evidence.
- Start a temporary host using the README's command, Windows hidden-window convention and a recorded process ID/command/creation time. Save output under an ignored feature-specific target directory.
- Confirm discovery of nine entities and nine repository interfaces, including the existing task catalog; embeddables do not add entity counts. Confirm actual schema validation and successful EntityManagerFactory/application startup. Counts alone are insufficient; identify the mapped types and target tables.
- Confirm Liquibase applied no new F003 changeset and existing identities/checksums remain unchanged. Inspect physical type/precision/collation details where Hibernate does not validate them fully. Do not issue DML or create fixtures.
- Stop only the process started for this check after verifying its executable, command and creation time. Confirm it is absent and record final process state.

Do not disable validation, alter tracking rows, clear checksums, rewrite F001 or reset the database to obtain a successful result. Record an unavailable environment or schema mismatch as BLOCKED/FAIL with the affected checks. If runtime inspection is outside scope, use NOT RUN with that reason.

This can establish fresh AC02 evidence and physical parts of AC01/AC09. A successful startup against empty tables does not establish hydration or query correctness.

### 4. Behavioral evidence remains separate

AC03–AC05 need representative rows and executed reads to establish lookup, paging and exact-value behavior. F003 does not authorize fixture DML or automated tests. Read-only observations of suitable existing development records may be recorded when in scope, without exposing payloads; otherwise leave those scenarios NOT RUN. Do not create a hidden startup runner, command-line test harness or permanent endpoint to bypass the current no-tests policy.

Concurrent writes, optimistic conflict handling, parent claim timing, final managed-version capture, command rollback/replay, authorization, append-only production behavior and durable delivery are not implemented here. Do not claim them from annotations, compilation or schema validation. Preserve these future obligations under ASM-02/03/04/10/11.

### 5. Documentation and final state

Reconcile spec, plan, tasks, implementation and evidence. Check local links, new-file whitespace and `git diff --check`. Distinguish static PASS, executed PASS, FAIL, NOT RUN and BLOCKED. Report whether files are committed and whether any temporary process remains. Do not stage or commit without a request.
