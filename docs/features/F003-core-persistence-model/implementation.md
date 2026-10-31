# F003 implementation record

**Specification:** [revision 1](spec.md). **Status:** IMPLEMENTED; static review, build and local schema/discovery checks passed. Behavioral checks remain NOT RUN.

## Historical documentation preparation

On 2026-10-31 the owner requested the feature documents and stated that a later Codex agent will implement them. This session adds no Java source, repository interface, enum, migration, build configuration or runtime behavior.

The inspected engine baseline is `ef265bff8147b68c5096f63b496683e1ac38bbfe`, with a clean working tree before documentation preparation. F001 supplies nine tables; F002 supplies TaskTypeEntity, TaskTypeRepository and TaskTypeCatalogService. The eight additional mappings and repositories specified here do not exist yet.

## Selected approach

See [plan.md](plan.md) for exact packages and [persistence-contract.md](persistence-contract.md) for keys, values, enums and read operations. The implemented approach uses scalar FK references, narrow internal repositories, no new services/mutation APIs, and a read-only legacyExpressionPolicy field without schema changes.

These choices were first recorded during preparation and implemented during the subsequent authorized session. No implementation deviation is recorded.

## Required updates during implementation

The implementing agent must record its actual starting revision/worktree state, map each added component to F003 requirements/tasks, and add a per-table field/type inventory or inspection record showing complete F001 coverage. Record provider-specific annotation choices and their rationale, query implementations and any deviations before marking dependent tasks complete.

Track task progress in [tasks.md](tasks.md) and check outcomes in [evidence.md](evidence.md). If a discovery changes storage or observable semantics, update the specification and plan before dependent work. Keep legacy-column write compatibility and runtime command guarantees explicitly deferred.

## Implementation session — 2026-10-31

The owner subsequently authorized implementation, JDK 21 packaging, local host startup and read-only database inspection. Starting HEAD is `ef265bff8147b68c5096f63b496683e1ac38bbfe`. The seven F003 documents were already staged; README.md, docs/README.md, docs/design-sources.md and docs/features/README.md had existing unstaged navigation changes. These are preserved; this session does not stage, commit or push.

T01–T06: added the eight entities, two embedded IDs, nine enums and eight read repositories in the exact plan packages. F002, the POMs, scanning configuration and migration resources remain unchanged. All references are scalar IDs, and only definition, definition-version and instance introduce numeric JPA versions. Embedded keys validate non-null/positive components and case-sensitive technical keys without normalization. Entities expose getters and protected JPA construction only; hash getters clone arrays.

Provider choices use the installed Hibernate 6.6.13.Final API: `@Nationalized` for Unicode, `@JdbcTypeCode(SqlTypes.LONG32NVARCHAR)` with local `nvarchar(max)` column definitions for opaque long text, `SqlTypes.BINARY`/length 32 for fixed hashes, `SqlTypes.UUID` for SQL Server UUIDs, `@FractionalSeconds(7)` for timestamps and `@TimeZoneStorage(NATIVE)` for offset values. UTC audit fields use LocalDateTime with field documentation; nullable typed values retain reference types. No global provider setting or JSON conversion was added.

Repository interfaces extend only Spring Data Repository and declare read-only REQUIRED transactions. Exact reads return Optional. Task/history lists use fixed owner/cursor predicates and ascending order with Pageable database limits and no count query. Callers must supply the documented trusted inputs. Fixed ACTIVE and TASK_COMPLETED lookups remain singular. Definition insertion remains unsupported until the legacy required policy column is reconciled in a later feature.

## Completed mapping inventory — T07, R01–R04/R06/R07/R09

Compared each F001 dictionary and DDL column with the source annotations and local SQL catalog. Every new table column is represented once; key components are in the embedded IDs where applicable. The following inventory lists SQL column names in declaration order; Java names are camelCase except expression_policy → legacyExpressionPolicy.

- **workflow_definition (7):** id, workflow_key, catalog_name, catalog_description, created_at, created_by, lock_version. IDENTITY Long ID and Long version; VARCHAR(100) key, nationalized 200/2000 text and UTC timestamp(7).
- **workflow_definition_version (26):** id, definition_id, version, schema_version, expression_policy, definition_json, status, content_revision, content_hash, canonicalization_version, created_at, created_by, updated_at, updated_by, validation_status, validated_content_revision, validated_content_hash, validation_context_hash, validation_details_json, validated_at, validated_by, published_at, published_by, retired_at, retired_by, lock_version. Complete nullable validation/publication/retirement envelope; Integer version, Short schema version, Long revision/version, three fixed 32-byte hashes, opaque long nationalized JSON and UTC timestamps(7). Legacy VARCHAR(100) is excluded from insert/update without a placeholder.
- **workflow_instance (14):** id, definition_version_id, business_type, business_id, current_node_key, status, result_key, started_at, started_by, ended_at, ended_by, termination_reason, last_history_sequence, lock_version. IDENTITY Long ID, scalar version reference, paired nullable correlation, UTC lifecycle times and Long version; no child version substitute.
- **workflow_activity (15):** id, instance_id, node_key, activation_sequence, task_type_id, completion_policy, contribution_outcome_key, required_task_count, review_revision_ref, status, outcome_key, entered_at, closed_at, closed_by, closure_reason. IDENTITY Long ID and scalar owner/type IDs, Integer required count, string closure category and no technical version.
- **workflow_task (11):** id, activity_id, task_key, assigned_user_ref, status, outcome_key, created_at, due_at, closed_at, closed_by, closure_reason. IDENTITY Long ID; scalar activity owner only, closed-set closure enum, no duplicate instance/type/result or version field.
- **workflow_variable (10):** instance_id, variable_key, value_type, string_value, long_value, decimal_value, boolean_value, date_value, datetime_value, json_value. Embedded owner/key; seven nullable typed columns use String, Long, BigDecimal(38,10), Boolean, LocalDate, OffsetDateTime with native offset timestamp(7), and opaque nationalized long text. No row and typed-null row remain distinct.
- **workflow_history (15):** instance_id, sequence_number, event_type, command_id, correlation_id, actor_kind, actor_ref, occurred_at, activity_id, task_id, source_node_key, target_node_key, transition_key, details_schema_version, details_json. Embedded owner/sequence, UUID correlation, text event category, actor enum, Short envelope version, UTC time and opaque JSON. Both composite history ownership FKs remain database constraints.
- **workflow_command_receipt (10):** command_id, instance_id, operation_type, actor_kind, actor_ref, request_fingerprint, fingerprint_version, recorded_at, result_schema_version, result_json. Assigned UUID ID without generator, scalar owner, text operation category, fixed copied hash, Short envelope version and UTC time.

All nullability and VARCHAR/NVARCHAR lengths match F001, including actor/reference lengths and BIN2-backed technical keys. Existing TaskTypeEntity still maps the remaining six catalog columns, giving 114 columns across nine tables. No entity relationships, cascades, setters, builders, lifecycle methods, payload equality/toString or new services were introduced. Exact closed-set strings match the contract; unknown enum hydration is not caught or defaulted.

T08–T09: final package build, nine-entity/nine-repository startup and unchanged metadata comparison passed; the verified temporary host PID 29468 was stopped and subsequently absent. No fixture/application DML, new migration, tests, staging or commit occurred. The local database has no workflow rows, so entity hydration, exact lookup results, paging results and value round trips remain NOT RUN. Detailed commands/results and remaining gates are in [evidence.md](evidence.md). No material contract conflict or implementation deviation was found.
