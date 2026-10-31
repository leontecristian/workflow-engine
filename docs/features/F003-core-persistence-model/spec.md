# F003 — Core persistence model

**Status:** IMPLEMENTED revision 1. Static mapping review, JDK 21 packaging and local discovery/schema validation passed on 2026-10-31. Hydration, lookup/paging execution and runtime guarantees remain unverified; see [evidence](evidence.md).

**Revision:** 1 — 2026-10-31. **Owner:** Workflow project owner. **Constitution:** [v3.0.0](../../constitution.md).

## Purpose and delivery decision

Complete the Java persistence foundation above the nine tables created by F001. A later Codex agent can implement this feature from these documents before definition management and the first executable workflow. The owner selected this preparatory feature after discussing the next development stage and explicitly requested documentation now, with implementation later.

F002 already maps and manages the task-type catalog. F003 adds the remaining eight entity mappings, their internal read repositories and established value types. It does not add one CRUD service per table. Services will coordinate specified definition and workflow operations in subsequent features.

The proposed continuation is F004 for minimal definition management/validation and F005 for the minimal SINGLE workflow. Those identifiers describe the planned sequence, not specifications or authorization to implement those later features. The executable milestone remains `START → HUMAN_TASK (SINGLE) → END` from [language design section 16.3](../../architecture/workflow-definition-language-design.md#163-completing-the-language-specification).

## Sources and prerequisites

- [F001 specification](../F001-initial-database-schema/spec.md) and [physical schema contract](../F001-initial-database-schema/schema-contract.md).
- The actual [initial changelog](../../../workflow-core/src/main/resources/db/changelog/workflow/changes/001-initial-schema.xml), which remains unchanged.
- [F002 specification](../F002-task-type-catalog/spec.md), implementation and evidence; preserve its catalog API and mappings.
- [Database design v1.4](../../architecture/Workflow_Engine_Database_Design_v1.md), [language design](../../architecture/workflow-definition-language-design.md) and [source precedence](../../design-sources.md).
- [Development workflow](../../specification-driven-development.md), [package conventions](../../package-structure.md) and [coding standards](../../coding-standards.md).

The F001 contract and applied DDL describe physical storage; later owner decisions describe intended language behavior. Neither may silently overwrite the other. The legacy-column treatment below resolves the difference for this read-only foundation only.

## Scope and actors

The consumer is a future core application service running inside the host's persistence context. Hosts continue to depend on documented services and detached DTOs, not these entities or repositories. The development host supplies entity/repository scanning, datasource, transaction manager and migration execution.

Included: all remaining columns and primary keys, scalar foreign-key references, established enum values, exact numeric/binary/temporal storage mappings, internal exact and bounded reads, mapping documentation and verification evidence.

Excluded: entity creation/update APIs, application services, public read APIs, controllers, generic CRUD, lifecycle transitions, publication validation, JSON parsing or schemas, canonicalization/fingerprint algorithms, expression execution, start/completion/cancellation commands, assignment, task roster execution, variable writes, receipt replay, audit/event production, outbox adapters, APOLO integration, sample records and schema changes. Mapping JSON columns and ALL policy values does not implement those capabilities.

## Requirements

- **F003-R01 — Complete physical coverage.** Add one entity for each of `workflow_definition`, `workflow_definition_version`, `workflow_instance`, `workflow_activity`, `workflow_task`, `workflow_variable`, `workflow_history` and `workflow_command_receipt`. Together with F002 there must be nine mapped tables, with every F001 column represented exactly once. Follow [persistence-contract.md](persistence-contract.md).
- **F003-R02 — Identity and ownership.** Match generated identities, assigned UUIDs and composite keys. Store foreign keys as scalar identifiers, retaining database ownership constraints. Add no graph-wide eager loading, cascading persistence/deletion, orphan removal or APOLO relationships. In particular, tasks inherit instance/type through their activity; history retains both composite ownership constraints.
- **F003-R03 — Faithful values.** Preserve nullable values, typed null versus absent variable rows, exact decimal precision, Unicode, UUIDs, binary hashes and temporal precision. Use named enum values only for closed sets already established by the schema. Preserve extensible history/operation/activity-closure categories as text. No silent trimming, rounding, timezone conversion through the machine default or JSON rewriting.
- **F003-R04 — Technical versions.** Map `lock_version` as nullable-before-persist Java `Long` with JPA `@Version` on definitions, definition versions and instances; preserve the existing catalog mapping. Add no version column/property to activities, tasks, variables, history or receipts. Content revisions and history sequence numbers are ordinary fields, not JPA versions. No version setter or simulated version increment is introduced.
- **F003-R05 — Narrow internal reads.** Implement only the operations and bounded-query contracts in the persistence contract. Missing exact results return `Optional.empty()`; collection reads return empty lists. Do not inherit unrestricted save/delete/findAll operations, implement update/bulk SQL or expose repositories as supported host APIs.
- **F003-R06 — Controlled model surface.** Provide JPA construction and read access without general setters, public all-fields constructors, builders or lifecycle mutation methods. Composite keys provide value equality; entities do not derive equality/hash codes from mutable fields or payloads. Binary arrays are defensively copied when returned. Domain value types cover established concepts; do not create duplicate entity/domain/DTO graphs merely for symmetry.
- **F003-R07 — Legacy expression-policy storage.** Retain and map the existing required `expression_policy` column as `legacyExpressionPolicy`, read-only (`insertable=false`, `updatable=false`). It is an internal storage artifact, not a selectable policy or a field in workflow JSON. No default/sentinel or derivation algorithm is introduced. Definition insertion is unsupported by F003 and must wait for a separately specified forward migration or write-compatible transition in the definition-management feature.
- **F003-R08 — Host and feature boundaries.** Keep the two modules, root scanning marker, existing transaction infrastructure and JPA schema validation. Internal repository reads may join an existing transaction or use a read-only transaction; no independent write transaction is added. F003 grants no authorization or receipt-replay capability. Preserve F002 behavior and its supported boundary.
- **F003-R09 — No schema mutation.** Preserve F001 resources, changeset identities, CHECKs, indexes, collations and NO ACTION FKs. Add no migration, seed data, extra columns/tables or schema-generation fallback. If the current database differs from F001, report the discrepancy; do not repair it by modifying the applied changelog.
- **F003-R10 — Honest verification and handoff.** Follow [verification.md](verification.md), the current no-tests policy and the later session's execution scope. Record implementation, build/static checks and any permitted startup/read-only inspection separately. Unperformed round-trip, concurrency and runtime behavior remain NOT RUN. Keep tasks, implementation and evidence records current.

## Internal read and failure contract

Repository parameters are trusted, validated internal inputs, not a public API. Future services own input validation, access control, error translation and detached response creation. A repository's successful lookup grants no authority to reveal or mutate the row. Reads use current database state and make no snapshot, locking or command-isolation guarantee beyond the caller's transaction.

Exact identity and natural-key comparisons preserve database case-sensitive semantics. A singular query encountering multiple rows fails rather than selecting an arbitrary result. Mapping failures, unknown persisted closed-set enum values and schema mismatches remain explicit persistence/startup failures; do not substitute defaults, catch-and-return-empty or change schema validation to get a successful startup. F003 defines no new HTTP/error-code catalog.

History and receipts are read-only through this feature. Their intended append-only semantics do not permit a generic save/merge/delete API. Later command features must specify creation and enforce exactly one completion record, successful-receipt replay, aggregate claims and atomic history/receipt/event writes.

## Acceptance scenarios

- **F003-AC01 (R01–R04, R09):** Given the F001 schema, inspection of all mappings accounts for every column, correct table/schema, identifiers, Java/JDBC types, lengths, nullability and technical versions. No schema change or duplicate catalog mapping exists.
- **F003-AC02 (R01, R08–R10):** Given a permitted local startup against the unchanged schema, the host discovers nine entities and nine repositories in total and completes JPA validation. Existing F002 wiring remains valid. If startup is outside scope or blocked, record NOT RUN or BLOCKED rather than claiming success.
- **F003-AC03 (R02, R05):** Given present and absent identities/natural keys, exact lookups return the matching row or empty; composite variable/history keys distinguish different owners and keys/sequences. Case-distinct technical keys remain distinct.
- **F003-AC04 (R05):** Given multiple task/history rows, bounded reads filter by the requested owner, return ascending stable IDs/sequences strictly after the supplied cursor, and apply the requested database limit without an unrestricted collection fetch.
- **F003-AC05 (R03, R06):** Given persisted nulls, Unicode text, exact decimals, UUIDs, hashes and temporal values within F001 precision, hydration preserves their meanings. SQL NULL typed values remain distinct from a missing variable row; JSON text is not parsed or normalized. A caller modifying a returned hash array cannot mutate entity state.
- **F003-AC06 (R04, R08):** Static inspection finds numeric versions only on the four versioned tables, no child-level substitute for the instance claim and no independent transaction manager or write boundary. This does not verify concurrent mutation behavior.
- **F003-AC07 (R06, R08):** Inspection finds no unrestricted entity mutation, inherited CRUD/delete API, eager entity graph, cascades, public persistence-based response or duplicate service/interface layer. Existing F002 remains unchanged.
- **F003-AC08 (R07, R09):** The legacy column is hydrated without becoming a JSON field, runtime policy selector or writable property. F003 cannot insert a definition version; the later migration/write-contract prerequisite is documented explicitly.
- **F003-AC09 (R02, R03, R09):** Review confirms that history ownership FKs, completion indexes and variable checks remain in the database and extensible category strings are not narrowed into speculative enums. Unknown closed-set values fail hydration instead of falling back.
- **F003-AC10 (R10):** The later agent records the actual revision, files, checks, failures and remaining gaps, stops any verification process it started and separates mapping completion from runtime or production readiness.

These are required properties, not permission to add/run automated tests or create database fixtures. [Evidence](evidence.md) distinguishes static coverage from execution of each scenario.

## Decisions and verification gates

No unresolved business decision blocks the selected mapping/read-only scope. Exact packages, repository surface, enum placement and legacy-column handling are selected in this feature's plan and persistence contract.

- **Before definition writes:** definition-management owner specifies legacy-column migration/transition, lifecycle mutations, canonicalization/hash contracts and validation freshness. F003 does not invent policy identifiers to satisfy the old NOT NULL constraint.
- **During F003 implementation:** verify Hibernate/SQL Server mapping compatibility for NVARCHAR(MAX), BINARY(32), UUID and timestamp precision using current dependencies. Resolve provider-specific annotation choices within this storage contract; a change in observable value semantics requires a specification update.
- **Before runtime commands:** runtime owner specifies aggregate claim/flush ordering, final version capture, identity/authorization, writes, append-only history, fingerprints, receipts and durable handoff (CON-06/07, ASM-02/03/04).
- **Before result or variable operations:** define codecs, bounds, supported read APIs and exact-value evidence (CON-13/14, ASM-10/11). Raw storage mapping provides none of those guarantees.

## Completion boundary

Implementation completion requires all eight new entities/repositories and the selected value types, documented static mapping/query checks, a successful prescribed package build, and reconciled feature records. If the build or code work is blocked, record the blocker and leave implementation incomplete. Mapping/startup and behavioral verification statuses must remain separately visible. Do not mark a blocked implementation task complete merely to label the feature IMPLEMENTED.

F003 creates no executable workflow or new supported service API. Following documentation preparation, the owner authorized implementation, packaging, local startup and read-only verification. The mapping/read foundation is implemented; this status does not establish behavioral acceptance or production readiness.
