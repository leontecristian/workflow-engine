# F003 persistence contract

Applies to [F003 revision 1](spec.md). The [F001 column dictionaries](../F001-initial-database-schema/schema-contract.md#column-dictionaries) are the exhaustive field inventory; the [applied DDL resource](../../../workflow-core/src/main/resources/db/changelog/workflow/changes/001-initial-schema.xml) fixes physical types, lengths, constraints and indexes. Map every dictionary column; this document selects Java representation and access behavior without duplicating or changing the DDL.

## Table and key assignments

All tables are in `wf`. Every entity uses explicit table/column names and field access. Java property names use the column's camelCase equivalent, except the legacy policy field identified below. Existing F002 remains unchanged.

| Table | Entity | Identifier | Technical version |
| --- | --- | --- | --- |
| workflow_definition | WorkflowDefinitionEntity | Long id; IDENTITY | Long lockVersion |
| workflow_definition_version | WorkflowDefinitionVersionEntity | Long id; IDENTITY | Long lockVersion |
| workflow_task_type | Existing TaskTypeEntity | Existing Long id; IDENTITY | Existing Long lockVersion |
| workflow_instance | WorkflowInstanceEntity | Long id; IDENTITY | Long lockVersion |
| workflow_activity | WorkflowActivityEntity | Long id; IDENTITY | None |
| workflow_task | WorkflowTaskEntity | Long id; IDENTITY | None |
| workflow_variable | WorkflowVariableEntity | Embedded WorkflowVariableId(instanceId, variableKey) | None |
| workflow_history | WorkflowHistoryEntity | Embedded WorkflowHistoryId(instanceId, sequenceNumber) | None |
| workflow_command_receipt | WorkflowCommandReceiptEntity | UUID commandId; assigned, no generator | None |

The two embedded IDs are ordinary `@Embeddable`, `Serializable` classes with protected JPA no-argument constructors, public value constructors, getters and equality/hash codes over both key components. They have no setters; Java records are not required for JPA embeddables. Require non-null components, positive instance IDs and positive history sequences; variable keys follow the existing technical-key grammar and 100-character ceiling. Compare strings case-sensitively without trimming or normalization. IDs belong in the entity layer, not the supported DTO/API surface.

## Java and SQL values

| SQL representation | Java representation and mapping contract |
| --- | --- |
| BIGINT | Long, including nullable values and JPA-managed versions. No SQL ROWVERSION mapping. |
| INT | Integer; definition version and required task count remain separate concepts. |
| SMALLINT | Short for schemaVersion, detailsSchemaVersion and resultSchemaVersion. This is persistence width, not a new JSON encoding rule. |
| VARCHAR(n) | String or the specified string-mapped enum; explicit length n, non-nationalized. |
| NVARCHAR(n) | String with explicit nationalized mapping and length n. Retain UTF-16 content and nulls. |
| NVARCHAR(MAX) | String with provider-specific nationalized long-text mapping compatible with the actual SQL Server column. Do not rely on a default length of 255 or accidentally require NCLOB storage. |
| BINARY(32) | byte[] mapped as fixed-length binary, length 32; clone non-null arrays in getters. Do not map as text, UUID, varbinary(MAX) or a Java-serialized object. |
| BIT | Boolean for the nullable variable value; retain F002's existing boolean selectable. |
| DECIMAL(38,10) | BigDecimal with precision 38, scale 10. Do not use float/double or add implicit rounding. |
| DATE | LocalDate. |
| DATETIME2(7) | LocalDateTime representing stored UTC audit/lifecycle time. Document UTC on these fields; no JVM-default-zone conversion. |
| DATETIMEOFFSET(7) | OffsetDateTime for datetimeValue, with explicit offset storage. Do not normalize to a local or offset-free timestamp. |
| UNIQUEIDENTIFIER | UUID with the SQL Server-compatible JDBC mapping; no generated UUID default. |

Timestamp mappings must match seven fractional digits. F003 reads existing valid values; it does not define a rounding policy for future writes with finer precision. Select exact Hibernate annotations against the locally installed dependency version and record them in implementation.md. Verify types/precision without changing host-global Hibernate/Jackson settings. Schema validation may not check every detail; review the SQL catalog as appropriate and keep unexecuted round trips explicit.

All nullable columns use nullable Java references. JSON is opaque String storage in this feature: no tree model, mutable map, validation codec, default application, canonicalization or automatic task-result-to-variable merge. SQL ISJSON checks remain database checks rather than evidence of language validation.

Variable absence is no row. A row with its discriminator and all value columns null represents typed null. Populate no default Java scalar for a null database value. Map all seven value columns and `value_type`; do not collapse typed columns into a generic serialized Object. Future writes must enforce the matching discriminator and exactly representable values.

## Established enum types

Map with `EnumType.STRING` and the existing VARCHAR column length; never ordinals or a provider-created database enum. Unknown persisted values must fail mapping.

| Type | Values | Mapped fields |
| --- | --- | --- |
| DefinitionStatus | DRAFT, PUBLISHED, RETIRED | Definition version status |
| DefinitionValidationStatus | PASSED, FAILED | Nullable definition version validationStatus |
| InstanceStatus | ACTIVE, COMPLETED, CANCELLED | Instance status |
| ActivityStatus | ACTIVE, COMPLETED, CANCELLED | Activity status |
| TaskStatus | OPEN, COMPLETED, CANCELLED | Task status |
| CompletionPolicy | SINGLE, ALL | Activity completionPolicy |
| TaskClosureReason | TASK_OUTCOME, ACTIVITY_RESOLVED, INSTANCE_CANCELLED, REVIEW_SUPERSEDED | Nullable task closureReason |
| VariableValueType | STRING, LONG, DECIMAL, BOOLEAN, DATE, DATETIME, JSON | Variable valueType |
| ActorKind | USER, SYSTEM | History and receipt actorKind |

Keep `eventType`, `operationType` and activity `closureReason` as String because their complete vocabularies are not fixed by F001. Outcome keys, node keys, correlation references, canonicalization/fingerprint identifiers and the legacy policy are also strings. Do not add a node-type enum solely for a JSON graph not yet parsed by this feature. Exposing enum types in `dto` packages does not introduce a command or wire API.

## Relationships and encapsulation

Use scalar foreign-key IDs; F003 introduces no `@ManyToOne`, `@OneToMany` or collection navigation. This avoids implicit graph loading and duplicate writable mappings. The database continues to enforce all ten FKs, including history's composite owner links. Scalar references do not remove those constraints or authorize skipping ownership checks in future services.

Preserve these references exactly: definitionVersion.definitionId; instance.definitionVersionId; activity.instanceId/taskTypeId; task.activityId; variable.id.instanceId; history.id.instanceId plus optional activityId/taskId; receipt.instanceId. History.commandId remains a logical UUID correlation without a receipt FK. Do not invent task.instanceId, task.taskTypeId or task.resultJson.

Use private mapped fields, protected entity no-argument constructors and public read access only as required by core callers. Public Java visibility is not a supported host integration boundary. Do not provide entity creation builders, public all-fields constructors or field setters in this mapping/read feature. Future service features add controlled construction/mutation methods with their invariants.

Keep Object identity equality for entities in F003; use key equality for the embeddable IDs. Do not use Lombok @Data or include payloads, actor/business references or relationships in generated toString. Getter generation must not expose raw mutable byte arrays. History and receipts have no update/delete methods; their future insert-only behavior is a command responsibility. Do not mark mutable lifecycle entities globally immutable as a shortcut for this feature's read-only scope.

Map `expression_policy` to `legacyExpressionPolicy` with `insertable=false`, `updatable=false`, existing length and NOT NULL metadata. Do not create a new enum, schema field, policy resolver or placeholder value. Because the actual column remains NOT NULL, later definition insertion must first reconcile it. This is deliberately a read-compatible foundation, not an insert-ready definition-management API.

## Repository operations

Each new repository extends only Spring Data `Repository<Entity, Id>` and declares the methods below. Return entities internally; introduce no public response DTOs for these reads. Use `Optional<Entity>` for exact lookups and `List<Entity>` for bounded collections. Do not derive a new base repository or expose `JpaRepository`, `CrudRepository`, save, delete, flush, unbounded findAll or bulk mutation APIs.

| Repository | Required operations |
| --- | --- |
| WorkflowDefinitionRepository | findById(Long id); findByWorkflowKey(String workflowKey) |
| WorkflowDefinitionVersionRepository | findById(Long id); findByDefinitionIdAndVersion(Long definitionId, Integer version) |
| WorkflowInstanceRepository | findById(Long id) |
| WorkflowActivityRepository | findById(Long id); findActiveByInstanceId(Long instanceId), fixed status ACTIVE |
| WorkflowTaskRepository | findById(Long id); findPageByActivityId(Long activityId, long afterId, Pageable pageable) |
| WorkflowVariableRepository | findById(WorkflowVariableId id) |
| WorkflowHistoryRepository | findById(WorkflowHistoryId id); findPageByInstanceId(Long instanceId, long afterSequence, Pageable pageable); findCompletionByTaskId(Long taskId), fixed event type TASK_COMPLETED |
| WorkflowCommandReceiptRepository | findById(UUID commandId) |

Use explicit JPQL where method names do not express fixed predicates, cursor predicates or ordering. Active-activity and completion lookups rely on F001's filtered uniqueness; unexpected multiplicity is a failure, not a request to choose the first row. Definition-version lookup does not filter lifecycle status; determining whether a version may start belongs to a later service.

Task pages filter `activityId` and `id > afterId`, order by id ascending and include all task statuses. History pages filter embedded instanceId and `sequenceNumber > afterSequence`, order by sequenceNumber ascending and include all event types. Initial cursors are zero. Apply the limit in SQL through Pageable, not in memory; use no count query and return at most the requested size. No promise of stable pagination across concurrent commits is made.

Internal preconditions: IDs are present/positive as applicable, cursors are nonnegative, Pageable is paged at page zero with an unsorted page size from 1 through 100. The 100-row value is a conservative internal read ceiling for this feature, not a language or future public API limit. Internal callers must satisfy these preconditions; repository wrappers solely to validate trusted parameters are not required. The fixed query order cannot be overridden by caller sorting. Later supported services must validate external inputs before constructing these arguments.

Repository read methods use Spring's read-only REQUIRED transaction semantics: join the caller's transaction, or start a read-only transaction if there is none. They do not acquire explicit/pessimistic locks, reserve sequences, authorize access or claim an aggregate. Read-only transaction hints are not database write protection or a production authorization mechanism. Managed entities must not escape into host responses; no F003 service exposes them.
