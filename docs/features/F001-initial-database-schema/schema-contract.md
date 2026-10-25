# F001 schema contract

This is the physical contract for [F001 revision 1](spec.md). The column dictionaries below are copied from database design v1.4 at `c32ffb07cc9f4f2f3d1d7bf087455c1af13acbc3`; their prose describes intent, not implemented behavior. Read this contract with source sections 5–15. If a dictionary and the following rules conflict, resolve the discrepancy before dependent implementation.

## Physical choices selected for F001

- All nine tables belong to `wf`. `N` means NOT NULL; `Y` means nullable. `key` expands to `VARCHAR(100) COLLATE Latin1_General_100_BIN2`; `ref` expands to `NVARCHAR(200)` with that same collation. Apply the explicit BIN2 collation to `code` and `expression_policy` too. Other text uses the host database collation; this feature does not alter database defaults.
- All `id BIGINT IDENTITY` columns use seed/increment `(1,1)` and clustered primary keys. The variable composite PK and history composite PK are clustered. Receipts have a nonclustered command-ID PK and a separate clustered `(instance_id, command_id)` index. No additional identity columns are introduced.
- Use exactly the dictionary SQL lengths, decimal precision/scale and timestamp precision. Numeric lock columns are ordinary `BIGINT NOT NULL`, with no SQL-generated values, defaults or triggers. No extra lock columns on activities/tasks/variables. No defaults, computed columns or triggers are required by F001.
- JSON columns use `NVARCHAR(MAX)`. Non-null JSON values must satisfy `ISJSON(value) = 1` using the SQL Server object/array form; no SQL Server 2022-only syntax is necessary. This does not validate workflow schemas or reject duplicate property names. For nullable JSON columns use `value IS NULL OR ISJSON(value) = 1`.
- Adopt `DATALENGTH(definition_json) <= 2097152`; `DATALENGTH(validation_details_json)`, `DATALENGTH(details_json)` and `DATALENGTH(result_json)` must each be at most `262144` bytes. Preserve nullable validation evidence. These measure stored UTF-16 bytes, including the full event/result envelope. No independent extra task-result allowance exists.
- `json_value` gets syntax/type checks but no invented byte/depth/aggregate limit in this initial DDL. Concrete variable and envelope budgets must be specified before its write API is implemented. Embedded input/result schema limits belong to codecs, not new SQL columns.
- Technical-key grammar, opaque-reference whitespace policy, UTC normalization, exact numeric conversion and immutable-field updates require future application validation. Explicit lengths/collations alone do not prove those rules. Do not introduce speculative triggers/functions to implement them.
- All FKs use NO ACTION on update/delete and all CHECK/FK constraints must be enabled and trusted. Do not use `WITH NOCHECK`, cascading deletes or orphan-removal mechanisms.

## Required row checks

Use explicit `IS NULL`/`IS NOT NULL` in presence branches. A nullable equality can evaluate UNKNOWN and pass a SQL CHECK, so it cannot establish required presence by itself.

| Table | Required checks |
| --- | --- |
| workflow_definition_version | `version`, `schema_version`, `content_revision` positive. Status DRAFT/PUBLISHED/RETIRED. DRAFT has neither publication nor retirement pair; PUBLISHED has publication actor/time and neither retirement field; RETIRED has both pairs. The seven current-validation fields (`validation_status`, `validated_content_revision`, `validated_content_hash`, `validation_context_hash`, `validation_details_json`, `validated_at`, `validated_by`) are all null or all populated; populated validation_status is PASSED/FAILED and validated_content_revision is positive. JSON validity and byte limits apply. |
| workflow_instance | business_type/business_id both null or both populated. Status ACTIVE/COMPLETED/CANCELLED. ACTIVE has null result/end actor/end time/termination reason. COMPLETED has result and end actor/time, null termination reason. CANCELLED has end actor/time and termination reason, null result. ended_at >= started_at when present; last_history_sequence >= 0. |
| workflow_activity | activation_sequence > 0; required_task_count between 1 and 100. Policy SINGLE/ALL: SINGLE requires count 1 and null contribution_outcome_key; ALL requires contribution_outcome_key. Status ACTIVE/COMPLETED/CANCELLED. ACTIVE has null outcome and all closure fields; COMPLETED has outcome and all closure fields; CANCELLED has all closure fields and null outcome. closed_at >= entered_at when present. |
| workflow_task | Status OPEN/COMPLETED/CANCELLED. OPEN has null outcome and all closure fields. COMPLETED has outcome, closed_at/closed_by and non-null closure_reason = TASK_OUTCOME. CANCELLED has null outcome, closed_at/closed_by and non-null reason in ACTIVITY_RESOLVED/INSTANCE_CANCELLED/REVIEW_SUPERSEDED. closed_at >= created_at when present. No additional due_at ordering rule is selected. |
| workflow_variable | value_type in STRING/LONG/DECIMAL/BOOLEAN/DATE/DATETIME/JSON. Each value column is null or its type discriminator matches. All value columns null is valid for every declared type (typed null); no row means absent. JSON syntax applies when json_value is non-null. Do not require one non-null value or permit nonmatching populated columns. |
| workflow_history | sequence_number > 0; details_schema_version > 0; actor_kind USER/SYSTEM. task_id requires activity_id. TASK_COMPLETED requires task_id and activity_id. JSON syntax and byte limit apply. |
| workflow_command_receipt | result_schema_version > 0; actor_kind USER/SYSTEM. JSON syntax and byte limit apply. |

Activity closure reasons and history event/receipt operation categories remain extensible text at this schema stage, apart from required closure presence and the specific TASK_COMPLETED rules. The source's proposed operation-specific activity reasons belong to later execution contracts; do not infer additional enum constraints. Workflow/type/key immutability, validation freshness and published content immutability are not old-row/new-row checks supplied by F001.

## Keys, relationships and indexes

Implement these access paths with stable names. Equivalent PK/UQ indexes count; do not duplicate them. Include column order, uniqueness, clustering and filter in verification.

| Table | Keys and indexes |
| --- | --- |
| workflow_definition | PK(id); UQ(workflow_key). |
| workflow_definition_version | PK(id); UQ(definition_id, version). |
| workflow_task_type | PK(id); UQ(code). |
| workflow_instance | PK(id); nonunique IX(business_type, business_id, id); nonunique IX(definition_version_id, status, id). No active-business uniqueness. |
| workflow_activity | PK(id); UQ(instance_id, activation_sequence); UQ(instance_id, id); unique filtered IX(instance_id) WHERE status = 'ACTIVE'. |
| workflow_task | PK(id); UQ(activity_id, task_key); UQ(activity_id, id); nonunique IX(assigned_user_ref, created_at, id) WHERE status = 'OPEN', INCLUDE(activity_id, due_at). |
| workflow_variable | PK(instance_id, variable_key). |
| workflow_history | Clustered PK(instance_id, sequence_number); nonunique IX(command_id, instance_id, sequence_number); unique IX(task_id) WHERE event_type = 'TASK_COMPLETED'; nonunique IX(activity_id, sequence_number) with the same TASK_COMPLETED filter. |
| workflow_command_receipt | Nonclustered PK(command_id); clustered IX(instance_id, command_id). |

Required FKs (ten total):

1. definition_version.definition_id → definition.id.
2. instance.definition_version_id → definition_version.id.
3. activity.instance_id → instance.id.
4. activity.task_type_id → task_type.id.
5. task.activity_id → activity.id.
6. variable.instance_id → instance.id.
7. history.instance_id → instance.id.
8. history.(instance_id, activity_id) → activity.(instance_id, id).
9. history.(activity_id, task_id) → task.(activity_id, id).
10. command_receipt.instance_id → instance.id.

Names above omit the `wf.workflow_` prefix for readability. Qualify actual tables fully. The two nullable composite history FKs work with the explicit task/activity presence CHECK; do not replace them with independent ID FKs that permit cross-instance ownership.

Do not add a history-command-receipt FK in either direction. Do not add task.instance_id, task.task_type_id, task result JSON, one-task-per-activity uniqueness, uniqueness per assignee, one-draft/one-published-version restrictions, outcome/graph tables, APOLO association FKs or optional dependency projections. All expected application tables are empty after a clean first migration; no seed DML is included.

The completion index enforces **at most one** TASK_COMPLETED per task. Exactly one event for each completed task, no events for open/cancelled tasks, outcome agreement, roster counts, graph membership, append-only history/receipts and retained catalog codes require the later command/administration contract. F001 must not claim to enforce or verify those behaviors.

## Column dictionaries

The following snapshot includes both the section 7.2 definition-version columns and the section 6 envelope. No envelope fields may be omitted.

### workflow_definition

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `workflow_key` | key | N | Immutable portable family key. |
| `catalog_name` | NVARCHAR(200) | N | Current catalog label; not a historical version label. |
| `catalog_description` | NVARCHAR(2000) | Y | Current catalog description. |
| `created_at` | DATETIME2(7) | N | Creation time in UTC. |
| `created_by` | ref | N | Creating principal. |
| `lock_version` | BIGINT | N | JPA-managed concurrency value for catalog metadata edits. |

### workflow_definition_version — specific fields

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `definition_id` | BIGINT | N | FK to workflow_definition.id. |
| `version` | INT | N | Positive immutable version number. |
| `schema_version` | SMALLINT | N | Supported workflow JSON language version. |
| `expression_policy` | VARCHAR(100) BIN2 | N | Exact restricted expression policy identifier. |
| `definition_json` | NVARCHAR(MAX) | N | Complete graph, declarations, node outcome/input contracts and optional designer metadata. |

### workflow_definition_version — lifecycle envelope (same table)

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `status` | VARCHAR(12) | N | DRAFT, PUBLISHED or RETIRED. |
| `content_revision` | BIGINT | N | Starts at 1; increments only when content changes. |
| `content_hash` | BINARY(32) | N | Hash of current canonical content. |
| `canonicalization_version` | VARCHAR(40) | N | Content hashing contract identifier. |
| `created_at` | DATETIME2(7) | N | UTC creation time. |
| `created_by` | ref | N | Trusted principal reference. |
| `updated_at` | DATETIME2(7) | N | UTC time of the latest permitted row change. |
| `updated_by` | ref | N | Principal responsible for that change. |
| `validation_status` | VARCHAR(12) | Y | PASSED or FAILED; null when no current validation is retained. |
| `validated_content_revision` | BIGINT | Y | Content revision actually checked. |
| `validated_content_hash` | BINARY(32) | Y | Content hash actually checked. |
| `validation_context_hash` | BINARY(32) | Y | Digest of schema, expression policy and relevant host capabilities. |
| `validation_details_json` | NVARCHAR(MAX) | Y | Versioned diagnostics and capability provenance. |
| `validated_at` | DATETIME2(7) | Y | UTC validation time. |
| `validated_by` | ref | Y | Validation actor. |
| `published_at` | DATETIME2(7) | Y | First successful publication time. |
| `published_by` | ref | Y | Publishing actor. |
| `retired_at` | DATETIME2(7) | Y | Retirement time. |
| `retired_by` | ref | Y | Retiring actor. |
| `lock_version` | BIGINT | N | JPA-managed technical concurrency value; distinct from content revision. |

### workflow_task_type

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Internal primary key for relational references. |
| `code` | VARCHAR(100) BIN2 | N | Unique portable identifier, immutable from creation; follows the technical-key rules. |
| `name` | NVARCHAR(200) | N | Current human-readable catalog name. |
| `description` | NVARCHAR(2000) | Y | Optional current catalog explanation. |
| `selectable` | BIT | N | Whether authors may select the code for new workflow definitions. |
| `lock_version` | BIGINT | N | JPA-managed optimistic-lock value for catalog edits. |

### workflow_instance

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `definition_version_id` | BIGINT | N | FK to the exact immutable workflow version. |
| `business_type` | key | Y | Optional opaque business-correlation category; present only with business_id. |
| `business_id` | ref | Y | Optional opaque business-correlation reference; present only with business_type; no APOLO FK. |
| `current_node_key` | key | N | Current stable node after commit. |
| `status` | VARCHAR(12) | N | ACTIVE, COMPLETED or CANCELLED. |
| `result_key` | key | Y | END business result for normal completion. |
| `started_at` | DATETIME2(7) | N | UTC start time. |
| `started_by` | ref | N | Trusted initiating principal. |
| `ended_at` | DATETIME2(7) | Y | UTC completion or administrative termination time. |
| `ended_by` | ref | Y | Responsible principal. |
| `termination_reason` | NVARCHAR(2000) | Y | Required for administrative cancellation. |
| `last_history_sequence` | BIGINT | N | Starts at zero; reserves the first event for the early instance claim and allocates subsequent events. |
| `lock_version` | BIGINT | N | JPA-managed numeric aggregate concurrency value. |

### workflow_activity

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key. |
| `instance_id` | BIGINT | N | FK to workflow_instance.id. |
| `node_key` | key | N | Human node in the pinned workflow. |
| `activation_sequence` | BIGINT | N | Positive sequence allocated under the instance claim. |
| `task_type_id` | BIGINT | N | FK to workflow_task_type.id; shared by every task in this activation. |
| `completion_policy` | VARCHAR(12) | N | SINGLE or ALL. |
| `contribution_outcome_key` | key | Y | Required for ALL; null for SINGLE. |
| `required_task_count` | INT | N | Frozen roster size; 1 to 100. |
| `review_revision_ref` | ref | Y | Optional opaque host review/snapshot identity shared by all tasks. |
| `status` | VARCHAR(12) | N | ACTIVE, COMPLETED or CANCELLED. |
| `outcome_key` | key | Y | Final aggregate outcome when COMPLETED. |
| `entered_at` | DATETIME2(7) | N | UTC activation time. |
| `closed_at` | DATETIME2(7) | Y | UTC aggregate completion/cancellation time. |
| `closed_by` | ref | Y | Principal whose command closed the activity. |
| `closure_reason` | VARCHAR(40) | Y | Explicit closure category for the event/history contract. |

### workflow_task

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `id` | BIGINT IDENTITY | N | Primary key and exact completion target. |
| `activity_id` | BIGINT | N | Parent activity. |
| `task_key` | key | N | Stable slot/obligation key within that activity. |
| `assigned_user_ref` | ref | N | Current direct assignee of the work item. |
| `status` | VARCHAR(12) | N | OPEN, COMPLETED or CANCELLED. |
| `outcome_key` | key | Y | Submitted task outcome; populated only for COMPLETED. |
| `created_at` | DATETIME2(7) | N | UTC creation time. |
| `due_at` | DATETIME2(7) | Y | Optional due-time metadata; no expiry behavior. |
| `closed_at` | DATETIME2(7) | Y | UTC completion/cancellation time. |
| `closed_by` | ref | Y | Actual trusted actor responsible for closure. |
| `closure_reason` | VARCHAR(40) | Y | TASK_OUTCOME, ACTIVITY_RESOLVED, INSTANCE_CANCELLED or REVIEW_SUPERSEDED. |

### workflow_variable

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `instance_id` | BIGINT | N | Owning instance. |
| `variable_key` | key | N | Declared variable in the pinned definition. |
| `value_type` | VARCHAR(12) | N | STRING, LONG, DECIMAL, BOOLEAN, DATE, DATETIME or JSON. |
| `string_value` | NVARCHAR(4000) | Y | Current string value. |
| `long_value` | BIGINT | Y | Signed 64-bit integer. |
| `decimal_value` | DECIMAL(38,10) | Y | Exactly representable decimal. |
| `boolean_value` | BIT | Y | True/false. |
| `date_value` | DATE | Y | Calendar date. |
| `datetime_value` | DATETIMEOFFSET(7) | Y | Timestamp with explicit offset. |
| `json_value` | NVARCHAR(MAX) | Y | Current bounded JSON object/array for value_type = JSON; SQL NULL represents explicit variable null. |

### workflow_history

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `instance_id` | BIGINT | N | FK to the owning instance. |
| `sequence_number` | BIGINT | N | Positive per-instance event order. |
| `event_type` | VARCHAR(60) | N | Stable workflow-owned event category. |
| `command_id` | UNIQUEIDENTIFIER | N | Correlation to the originating execution command. |
| `correlation_id` | NVARCHAR(100) | Y | Trusted host trace/business correlation. |
| `actor_kind` | VARCHAR(12) | N | USER or SYSTEM. |
| `actor_ref` | ref | N | Trusted principal; system actions use a stable system identity. |
| `occurred_at` | DATETIME2(7) | N | UTC event time. |
| `activity_id` | BIGINT | Y | Human activity when relevant. |
| `task_id` | BIGINT | Y | Exact task when relevant. |
| `source_node_key` | key | Y | Source node for a transition event. |
| `target_node_key` | key | Y | Target node for a transition event. |
| `transition_key` | key | Y | Selected edge for a transition event. |
| `details_schema_version` | SMALLINT | N | Versioned event evidence format. |
| `details_json` | NVARCHAR(MAX) | N | Validated, bounded event-specific evidence; TASK_COMPLETED holds the authoritative accepted submission, including any structured result. |

### workflow_command_receipt

| **Field** | **SQL type** | **Null** | **Meaning** |
| --- | --- | --- | --- |
| `command_id` | UNIQUEIDENTIFIER | N | Primary key; idempotency identity. |
| `instance_id` | BIGINT | N | FK to the affected or newly created instance. |
| `operation_type` | VARCHAR(40) | N | Stable command category. |
| `actor_kind` | VARCHAR(12) | N | USER or SYSTEM. |
| `actor_ref` | ref | N | Trusted initiating principal. |
| `request_fingerprint` | BINARY(32) | N | SHA-256 of the complete canonical semantic request. |
| `fingerprint_version` | VARCHAR(40) | N | Canonical request contract identifier. |
| `recorded_at` | DATETIME2(7) | N | Time the result was recorded inside the transaction; not a measured SQL commit timestamp. |
| `result_schema_version` | SMALLINT | N | Version of the immutable replay result. |
| `result_json` | NVARCHAR(MAX) | N | Original successful command result. |
