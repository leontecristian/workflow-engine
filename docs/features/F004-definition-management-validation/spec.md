# F004 — Definition management and validation

**Status:** READY for implementation; implementation NOT STARTED. **Revision:** 1 — 2026-10-31. **Owner:** Workflow project owner. **Constitution:** [v3.0.0](../../constitution.md).

## Outcome and scope

Provide a trusted core Java API to create/import and edit workflow drafts, inspect/export definitions, validate the supported document contract, publish immutable versions and retire them. Build on F003's persistence mappings. The owner selected **minimal SINGLE workflow validation** and **explicit version numbers in portable JSON** on 2026-10-31. This feature prepares definitions for F005; it does not execute workflows.

Authority: [language design sections 12–14 and 16.3](../../architecture/workflow-definition-language-design.md#12-document-representation-and-versioning), [database design sections 5–7](../../architecture/Workflow_Engine_Database_Design_v1.md), [source precedence](../../design-sources.md), [F001 physical contract](../F001-initial-database-schema/schema-contract.md) and [F003 implementation](../F003-core-persistence-model/implementation.md). Apply CON-01–04, CON-08–10 and CON-12; ASM-01/02/03/05/08 remain verification obligations, not established integration guarantees.

Included: definition-family creation/metadata edits, explicitly numbered version import, draft replacement, exact/bounded reads, structural/graph/capability validation, versioned canonical content hashes, lifecycle concurrency, host management-audit handoff and one forward migration for legacy storage.

Excluded: runtime commands/tasks/receipts/history/events, executing resolvers or guards, service tasks, expressions/gateways, ALL, variables, nonempty input contracts, structured results, designer UI, REST endpoints, automatic version allocation, clone convenience APIs, deletion, bulk imports, production APOLO adapters and automated tests. The language remains schemaVersion 1; this release implements a documented subset, not a new language version or a claim of full v1 support.

## Requirements

- **F004-R01 — Identity and portable content.** Workflow key is permanent and case-sensitive. The JSON supplies positive `version` (1 through 2147483647) and `schemaVersion: 1`; these must match indexed metadata. No automatic latest/next allocation, renumbering, task-type version or expressionPolicy field. Several drafts and published versions may coexist.
- **F004-R02 — Safe authoring.** Create/import and replace drafts using the ingestion contract below. Parseable, bounded documents with valid identity may be stored before graph/host validation succeeds. Never silently remove unknown fields, apply defaults, discard metadata or accept malformed/duplicate-key JSON.
- **F004-R03 — Content identity.** Use the canonicalization/hash contract below for storage and comparisons. Formatting/object-property order alone is a no-op; arrays, string values, null/absence and designer metadata remain significant. Version algorithms and never rewrite existing published fingerprints silently.
- **F004-R04 — Lifecycle and revisions.** Only DRAFT content is editable. A real replacement increments content_revision and clears all seven validation fields atomically. A persisted validation change advances technical locking state but not content_revision. Publication revalidates current content and host/catalog support. Retirement blocks future direct selection without deleting or changing pinned content.
- **F004-R05 — Supported validation.** Apply JSON Schema Draft 2020-12 structural checks and separate graph/contract/capability checks for the minimal subset. Reject all unsupported semantics before publication. Validation has no business effects and cannot invoke assignment, authorization guards or workflow actions.
- **F004-R06 — Host compatibility.** Check an existing selectable task code, supported node contract and registered zero-input SINGLE assignment resolver against a deployed-host snapshot. Metadata alone does not implement the resolver or prove runtime behavior. Preserve published/retired content and compatibility obligations when catalog selectability later changes.
- **F004-R07 — Transactions and concurrency.** Management mutations join an existing host transaction, use expected numeric lock versions and flush before reporting resulting state. Do not retry business/admin writes automatically or use runtime receipts. Host authorization and durable management audit remain in the same transaction.
- **F004-R08 — Diagnostics and freshness.** Return bounded stable diagnostics and persist validation status against the exact revision/hash/context. Errors prevent publication; warnings do not. Old PASSED metadata never substitutes for a fresh publication check. Infrastructure failure is distinct from an invalid document.
- **F004-R09 — Reads and retention.** Return immutable neutral views, exact-version exports and bounded summary pages. No entities, mutable trees, implicit latest-version resolution, deletes or host authorization bypass. Retired versions remain retrievable by exact identity.
- **F004-R10 — Compatible persistence change.** Remove the unused expression_policy column through a new ordered changeset under the empty-version-table gate described below, and remove F003's legacy field. Preserve F001 identities/checksums, existing catalog/runtime tables, and host-owned migrations. A nonempty legacy version table blocks this migration pending a separately specified conversion.
- **F004-R11 — Verification and handover.** Implement the contracts and acceptance scenarios, record actual results in implementation.md and retain NOT RUN/BLOCKED gaps. Documentation preparation runs no Java build, migration, application or automated test.

## Administration operations

Expose a concrete `WorkflowDefinitionService`; immutable command/query/view records use `dto.definition`. The host authenticates and authorizes every operation before calling. Java IDs/lock tokens use long/Long, definition versions int, and audit time Instant converted explicitly to UTC DATETIME2(7). No HTTP encoding is selected here.

Every mutation receives a `DefinitionManagementContext(actorRef, occurredAt)` constructed by the trusted host, not copied from request JSON. actorRef is nonblank, at most 200 code points/400 UTF-16 bytes, untrimmed; time is required and exactly representable at seven fractional digits. Metadata name is nonblank, at most 200 code points/400 UTF-16 bytes; nullable description is at most 2000 code points/4000 UTF-16 bytes. Reject malformed Unicode and never truncate values.

| Operation | Command/query and result | Behavior |
| --- | --- | --- |
| `importDraft` | ImportWorkflowDraft(documentJson), context → DefinitionChange | JSON identity supplies key/version. If absent, atomically create family and DRAFT version. Initial family metadata comes from valid document name/optional description. Existing family metadata is not overwritten by version import. An existing equivalent version of any lifecycle returns unchanged; different content at the same key/version conflicts, including DRAFT. |
| `replaceDraft` | ReplaceWorkflowDraft(versionId, expectedLockVersion, documentJson), context → DefinitionChange | Explicit revision-checked whole-document replacement; same key/version/schemaVersion required. No merge/patch. Exact canonical equivalence is unchanged after checking expected version and DRAFT state. |
| `updateMetadata` | UpdateWorkflowDefinition(definitionId, expectedLockVersion, name, description), context → DefinitionMetadataChange | Change only the family catalog label/description. Equal values are unchanged after the stale check. Portable version content and its validation remain untouched. |
| `validateDraft` | CheckWorkflowDefinition(versionId, expectedLockVersion), context → DefinitionCheckResult | Only DRAFT. Run current checks and persist PASSED/FAILED evidence; return report and before/after summary. Ordinary validation errors are a report, not a transaction-aborting exception. |
| `publish` | CheckWorkflowDefinition(versionId, expectedLockVersion), context → DefinitionCheckResult | Only DRAFT. Always revalidate. On success, save current evidence and PUBLISHED actor/time atomically. On ordinary validation failure, remain DRAFT and save FAILED evidence; return published=false. The host may commit this failed-validation result and audit it. |
| `retire` | RetireWorkflowDefinition(versionId, expectedLockVersion), context → DefinitionChange | PUBLISHED → RETIRED, preserving content and publication evidence. RETIRED is an unchanged observation after the expected-version check. DRAFT is an invalid state. |
| `findDefinition`, `findVersion`, `findVersionByKey` | definitionId; versionId; key/version → Optional view | Include all lifecycle states; absent rows return empty. |
| `listVersions` | DefinitionVersionQuery(definitionId, optional status, afterId, limit) → DefinitionVersionPage | Ascending local version-row ID, strict id > afterId; default limit 50, valid 1–100, fetch limit+1, return nullable nextAfterId only when another matching row exists. Items are summaries without document/diagnostic payloads. Missing family returns an empty page. |
| `exportVersion` | versionId → canonical JSON String | Exact stored version, including designer values, regardless of lifecycle; missing ID is NOT_FOUND. No audit/runtime/persistence fields are inserted into the document. |

IDs must be positive; expectedLockVersion and cursors nonnegative. Changes contain immutable before/after summaries (before absent for create), `changed`, operation and management context. Version summaries include local IDs, key/version/schemaVersion, lifecycle, contentRevision, lockVersion, content hash/canonicalization identifier, audit timestamps/actors and nullable validation summary. Detailed version views additionally contain canonical document JSON and the stored validation report, never JPA/Jackson objects. Hashes are lower-case hexadecimal strings at this Java DTO boundary; stored hashes remain BINARY(32). Collections are defensively copied. Do not duplicate full documents in list/change results.

`DefinitionCheckResult` contains the version change, report and `published` flag. Validation uses the supplied actor/time for validatedAt/validatedBy and updatedAt/updatedBy. If the complete validation and audit values are identical to the stored values, it may return unchanged after the expected-version/state checks and fresh validation; otherwise flush the metadata update. Return the actual provider-managed lockVersion without assuming one increment or a forced increment that occurs only at commit. No-op results are observations, not reservations. Import equivalence is content-based reuse, not authenticated command-receipt replay; the host still authorizes it. A concurrent duplicate insert may return CONFLICT and be retried by the caller in a new transaction; do not query or retry inside a failed transaction.

## Ingestion and canonicalization

Ingestion requires one JSON object, no trailing tokens/comments, strict Unicode and duplicate-property detection at every depth, including equivalent escaped property names. Require valid key, schemaVersion, version, name and optional description before saving; optional identity/descriptive fields cannot use null. Future schema versions are rejected at ingestion. Other missing/unknown/unsupported structure may remain in a DRAFT and is diagnosed before publication. For every recognizable HUMAN_TASK with a supplied string taskType, new import/content replacement checks the current catalog and rejects an unknown or nonselectable code: draft storage must not bypass the existing new-authoring rule. A missing/malformed field may remain an incomplete draft but cannot publish. Capability registration is checked on validation/publication, not required merely to save a draft. Equivalent reuse/no-op is not a new authoring selection. Apply the complete-definition and metadata budgets to drafts too; inability to preserve content blocks saving.

Use canonicalization identifier `wf-json-c14n-v1` and SHA-256 over UTF-8 canonical JSON, storing the same canonical text in definition_json. Compare canonical text as well as the stored algorithm/hash for reuse; do not trust a digest match alone. Stored records with an unsupported algorithm fail explicitly rather than being silently rehashed.

Canonical serialization rules:

1. Sort object property names lexicographically by Unicode scalar values; preserve array order. Preserve decoded string contents without Unicode normalization, trimming or typed conversion. Emit no insignificant whitespace.
2. Escape quotes and backslashes, and emit U+0000–U+001F as lower-case six-character `\u00xx` escapes. Emit other valid Unicode literally; do not escape `/`. Null and Boolean spellings are JSON's standard lowercase forms.
3. Preserve JSON numbers exactly without floating-point conversion. Normalize their exact decimal coefficient/exponent: concatenate mantissa digits, account for fractional places and explicit exponent, remove insignificant leading/trailing coefficient zeros, and represent nonzero values as an optional minus sign, integer coefficient and `e` plus the adjusted exponent only when that exponent is nonzero. The exponent has no leading plus/zeros. Zero is `0`. Thus 1, 1.0 and 10e-1 canonicalize to 1; 12.50 canonicalizes to 125e-1. Never expand a large exponent into a huge plain decimal. Bound original numeric tokens to 128 code points and use exact/bounded coefficient/exponent handling. Numeric strings remain strings, so "12.5" and "12.50" differ.
4. Omitted optional fields remain omitted; an explicit empty guards array is not inserted or removed. This is content identity, not equivalence of executable behavior. Include all designer content.

Enforce the existing language limits relevant to this subset: complete canonical definition ≤2 MiB UTF-16, raw document supplied to core ≤4 MiB UTF-8 (hosts must additionally bound the complete upload envelope), definition depth ≤64, designer ≤256 KiB UTF-16/depth 16, each designer object ≤100 properties/array ≤1000 items, metadata strings ≤16000 code points/property names ≤256. Draft node/transition arrays are bounded by the language's 200/500 ceilings even though publication supports only three/two. Apply selected name/description/key limits and reject invalid text before persistence. Test feasibility through the implementation's permitted checks, not by weakening published-value semantics. General embedded-schema/runtime-value limits are out of scope because those declarations cannot publish here.

## Supported publication contract

The canonical document must contain exactly three nodes and two transitions: one START, one HUMAN_TASK with SINGLE and one END. Validate references and types rather than relying on array positions. Require distinct node keys, unique transition keys, exactly one unconditional START → human transition, one human → END transition for its sole declared outcome, no other routes and no incoming START/outgoing END. Every node is reachable and reaches END. Node and transition key namespaces remain separate.

All fields and names follow language design section 12.1. Required top-level fields are schemaVersion, key, version, name, startPolicy, variables, nodes and transitions. Optional description/designer are permitted. In this release:

- startPolicy.inputContract and variables are empty arrays; guards are omitted/empty and initialize omitted/empty.
- Human task requires taskType, assignment `{resolver, inputs: {}}`, completion `{policy: "SINGLE"}`, inputContract `[]`, resultContract `{mode: "FORBIDDEN"}` and exactly one outcome `{key, label, requiresComment, optional guards}`. guards is omitted/empty; requiresComment is an explicit Boolean, either value permitted.
- START has only key/type/name/optional description. END additionally requires a nonempty technical-key result. Common names/descriptions follow L16 limits. taskType follows the existing catalog code grammar; resolver is a nonblank logical identifier ≤100 code points, not a bean expression or class name to instantiate.
- Transitions use key/from/to; only the human source adds the required outcome key. Reject conditions, defaults and extra executable properties. designer is an opaque bounded object whose unfamiliar properties are retained, not interpreted.
- Unknown executable fields fail STRUCTURE. Recognized but unimplemented types, nonempty inputs/variables/guards, ALL and permitted/required structured results fail UNSUPPORTED_FEATURE. This diagnostic does not declare them invalid in the full language. No unsupported feature may pass by being ignored.

This is the normative example for F004; catalog/capability registrations are host prerequisites, not seeded by this feature:

```json
{
  "schemaVersion": 1,
  "key": "sandbox.singleReview",
  "version": 1,
  "name": "Single review",
  "startPolicy": { "inputContract": [] },
  "variables": [],
  "nodes": [
    { "key": "BEGIN", "type": "START", "name": "Start" },
    {
      "key": "REVIEW", "type": "HUMAN_TASK", "name": "Review",
      "taskType": "SANDBOX_REVIEW",
      "assignment": { "resolver": "sandbox.singleReviewer", "inputs": {} },
      "completion": { "policy": "SINGLE" },
      "inputContract": [], "resultContract": { "mode": "FORBIDDEN" },
      "outcomes": [ { "key": "COMPLETE", "label": "Complete", "requiresComment": false } ]
    },
    { "key": "DONE", "type": "END", "name": "Done", "result": "COMPLETED" }
  ],
  "transitions": [
    { "key": "beginReview", "from": "BEGIN", "to": "REVIEW" },
    { "key": "finishReview", "from": "REVIEW", "to": "DONE", "outcome": "COMPLETE" }
  ]
}
```

## Capability checks and validation evidence

Introduce a genuine host extension `DefinitionCapabilityProvider.snapshot()` returning immutable `DefinitionCapabilities`: a set of `SingleAssignmentCapability(resolver)` entries and `TaskTypeCapability(taskType, supportedOutcomes)` entries. Each resolver entry explicitly certifies an actual registered resolver with zero declared arguments and the SINGLE result contract. Each task entry certifies presentation/handling for SINGLE, no scalar inputs, forbidden structured results, optional/required comments and its listed outcome keys. These descriptors are deliberately scoped to F004; broader contracts require a specified extension. Duplicate IDs, null values or incompatible descriptors are host configuration errors. Capability names remain stable contracts, not separately versioned capabilities.

The host supplies one immutable deployment snapshot; hot reload within a process is outside scope. Missing provider/required entry yields a HOST_CAPABILITY_UNAVAILABLE diagnostic, never an allow-all fallback. Provider exceptions or malformed provider data are infrastructure errors that roll back the operation without writing misleading FAILED language evidence. Validation never invokes the represented functions. A registration cannot prove future runtime data, actor permission or implementation behavior; F005 must implement and verify that boundary.

Resolve the task code through the documented catalog service. Validate requires existing/selectable; publication must hold the referenced catalog row stable through its transaction while checking selectability, so a concurrent disable is serialized before or after publication. Add a narrowly scoped catalog read operation for this purpose rather than reaching into another feature's repository. A disable after publication leaves that published version usable, including future starts. Ordinary reads/export/equivalent import of an existing immutable version do not reapply authoring selectability.

Use validator contract identifier `wf-minimal-single-validator-v1`. validation_context_hash is SHA-256 of canonical JSON containing this identifier, the bundled structural-schema digest, schemaVersion, canonicalization identifier, the referenced task's code/selectable/lockVersion and the relevant sorted capability descriptors (including explicit absence). Sort sets only in this context projection; never reorder definition arrays. It is provenance, not a user-selectable expression/capability policy. Publication always recomputes it and the report. Unrelated capability entries do not affect the relevant context.

Persist all seven F001 validation fields together: status, validated content revision/hash, context hash, details JSON, timestamp and actor. validation_details_json uses reportSchemaVersion 1 and includes those context identifiers/provenance plus the report. A draft edit clears all seven together. Retirement preserves publication-time evidence; no revalidation of immutable rows is implemented here.

`DefinitionValidationReport` contains valid, diagnostics and detailsOmitted. Each diagnostic has stable code, stage (PARSE/STRUCTURE/GRAPH/CONTRACT/HOST), severity (ERROR/WARNING), bounded message and optional JSON Pointer, elementKey and related pointer. Report independent errors where possible; skip checks dependent on malformed structure. Initial categories cover MALFORMED_JSON, DUPLICATE_PROPERTY, INVALID_IDENTITY, LIMIT_EXCEEDED, UNSUPPORTED_SCHEMA_VERSION, UNKNOWN_FIELD, INVALID_STRUCTURE, UNSUPPORTED_FEATURE, DUPLICATE_KEY, UNKNOWN_NODE, INVALID_GRAPH, UNKNOWN_OUTCOME, TASK_TYPE_UNAVAILABLE and HOST_CAPABILITY_UNAVAILABLE. Prefix document diagnostic codes with `WF_DEF_`. Warnings are optional, never required to deliver this slice.

Use L16 diagnostic limits: ≤100 details; message ≤1000 code points; each detail ≤8 KiB in both UTF-8/UTF-16; complete report ≤128 KiB in both; complete stored evidence ≤256 KiB UTF-16. Bound accumulation, reserve a failure summary/omission indicator, and never turn omitted errors into valid=true or truncate a pointer into a misleading location. No raw JSON, secrets, Java stack traces or SQL in public diagnostics. The implementing agent may refine internal work counters under L16, documenting them in the plan without changing these observable limits.

## Atomicity, conflicts and failure behavior

All mutations, including validateDraft, require the host transaction (MANDATORY); reads join it or use read-only REQUIRED. On a version mutation, load, compare expected lockVersion, verify lifecycle, change only allowed fields and flush the managed versioned update. No blind merge, bulk update, manual @Version increment, implicit retry or independently committed audit. No-op checks remain stale-sensitive. Family creation/version insertion is atomic and constrained by the existing unique keys; report a concurrent uniqueness conflict and leave recovery to a new transaction.

The host persists management audit before commit, including actor/time, operation, family/version identity, before/after lifecycle/revision/hash/lock values and validation outcome/context when changed. Use the returned summaries as the handoff. Audit failure rolls back the management change. No synthetic workflow instance, runtime history, command receipt or outbox is added. All results remain provisional until host commit. Failed validation returned normally may be committed as authoring evidence; technical/persistence failures abort the transaction.

Expose workflow-owned `WorkflowDefinitionException` categories INVALID_INPUT, NOT_FOUND, CONFLICT, INVALID_STATE, UNSUPPORTED_CANONICALIZATION and HOST_CONFIGURATION. Input ingestion failures carry bounded document diagnostics where applicable. Provider/database integration failures retain causes internally and rollback; do not mislabel them as invalid graphs. Host maps errors to transport responses. Exact permission names and production audit storage remain host-owned, not missing core behavior.

## Migration and compatibility

Add `changes/002-definition-management.xml`, logical path `db/changelog/workflow/changes/002-definition-management.xml`, author workflow-engine and changeset `f004-001-drop-legacy-expression-policy`. Include once after F001. Preconditions require SQL Server, the expected table/column and **zero rows in workflow_definition_version**; otherwise HALT with a clear prerequisite message. Drop only expression_policy. Do not use MARK_RAN, clear checksums, rewrite F001 or delete existing rows to pass the gate. Existing task catalog data is preserved.

The empty-table gate avoids silently reinterpreting old independent-policy definitions. A populated environment needs a separately reviewed data-preserving conversion before this migration can proceed. This is an upgrade gate, not permission to reset a database. Deploy the new mapping and migration together; F003's old mapping is not compatible after the column is removed. Do not regenerate lost policy data in an automatic rollback. Runtime tables and existing F001 validation/hash column capacities remain unchanged.

## Acceptance scenarios

- **AC01 (R01–03, R07):** Import a valid, explicitly numbered document into a new/existing family. New version is DRAFT, revision 1, without validation/publication metadata; canonical export preserves values. Equivalent reimport returns unchanged; changed same-key/version content conflicts, regardless of lifecycle. Family metadata is not overwritten.
- **AC02 (R02–04, R07):** Save a bounded identity-valid but graph-invalid draft; validate returns FAILED. Correct it using the current lock token: revision increments and all old validation fields clear. Formatting-only replacement is unchanged; stale replacement fails even when values would match. A new import/replacement explicitly selecting an unknown or nonselectable task code is rejected, while an equivalent existing-version reuse does not reapply authoring selection checks.
- **AC03 (R03):** Property-order/whitespace and equal JSON numeric spellings compare equal; array reordering, string spellings, absent/null and designer changes compare different. Duplicate escaped keys, malformed Unicode, over-limit values and lossy numeric handling are rejected without partial writes. Unsupported stored canonicalization is explicit.
- **AC04 (R05–06, R08):** With a selectable catalog code and matching real capability declarations, the normative example validates and publishes. Invalid topology, unresolved references, extra fields or any unsupported feature fail with bounded relevant diagnostics; no resolver/action/guard is executed.
- **AC05 (R04, R06–08):** Validate PASSED, then change the capability deployment or disable the task type. Publication rechecks and fails rather than trusting the old flag. A concurrent catalog disable is ordered with publication; disabling afterward does not invalidate the published document. Provider technical failure rolls back instead of becoming a graph failure.
- **AC06 (R04, R07–09):** Publish a draft and attempt content or designer-only edits: reject. Retirement changes only lifecycle envelope and keeps exact content/hash/export available. A repeated current-token retirement is unchanged; stale retirement conflicts. Later instances must keep using pinned retired versions (runtime execution is not claimed here).
- **AC07 (R07):** Competing replacement/publication/retirement commands cannot both overwrite the same version. Capture actual post-flush versions. A host audit/commit failure rolls back family/version/validation mutations; no receipt or runtime row is created. Sequential no-ops are observations, not lock reservations.
- **AC08 (R08):** Failed validation remains DRAFT with stored FAILED evidence when the host commits the returned report. Diagnostic truncation preserves failure and sets detailsOmitted. Required full provenance fits the storage ceiling or the operation fails explicitly without partial evidence.
- **AC09 (R09):** Exact reads include disabled-family task references and retired versions without dropping data; pages are owner/status-filtered, bounded, stable-order summaries with correct continuation. Host authorization precedes reads and returned values expose no mutable provider objects.
- **AC10 (R10–11):** On an empty-version F001/F003 database, the new migration removes only the legacy column and repeat startup does not reapply it. F001 checksums/catalog/runtime schema remain intact. A populated legacy version table blocks migration without modifying rows. Record what was actually verified and all remaining gaps.

No application/migration execution is authorized by this documentation preparation. The next agent implements the selected contract and records acceptance evidence within its own authorized scope. Validator dependency/annotation choices are implementation verification work; full-language validation and populated legacy-data conversion are explicitly outside this feature.
