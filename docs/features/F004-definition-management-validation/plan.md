# F004 implementation plan

Applies to [specification revision 1](spec.md). Implemented after the owner's subsequent authorization; execution status is in [tasks.md](tasks.md) and actual checks/gaps in [implementation.md](implementation.md).

## Approach and components

Extend F003's definition mappings with controlled lifecycle operations and a concrete `WorkflowDefinitionService`. Keep persistence, JSON processing and validation as separate responsibilities within the existing core module. Use a private workflow JSON configuration; never alter the host ObjectMapper or introduce another datasource, transaction manager or migration runner. The host continues to own endpoints, identity, authorization and management audit.

Root package: `io.github.leontecristian.workflow.core`. Use these exact package assignments; helpers may be split within the assigned package when necessary, without adding mirrored service interfaces or a generic framework:

| Package/resource | Responsibility and named components |
| --- | --- |
| `service.definition` | Concrete WorkflowDefinitionService, DefinitionDocumentCodec, DefinitionContentCanonicalizer, DefinitionValidationService and package-private validation/persistence-error helpers. Only the documented administration service is a supported application service. |
| `service.definition` | DefinitionCapabilityProvider: a justified host integration interface with snapshot(), not an interface mirroring the administration service. |
| `dto.definition` | ImportWorkflowDraft, ReplaceWorkflowDraft, UpdateWorkflowDefinition, CheckWorkflowDefinition, RetireWorkflowDefinition, DefinitionManagementContext; definition/version views and summaries, change results, DefinitionVersionQuery/Page and DefinitionCheckResult/ValidationReport/Diagnostic. Reuse existing status enums. |
| `dto.definition` | DefinitionCapabilities, SingleAssignmentCapability, TaskTypeCapability and diagnostic stage/severity types. Immutable standard/workflow-owned values, no Spring/JPA/Jackson types. |
| `dto.definition.document` | MinimalWorkflowDefinition and its typed start/human/end/transition/outcome/assignment values, used internally after structural validation. Preserve canonical source separately; never rebuild opaque designer metadata through a lossy typed model. No second persisted graph. |
| `entity.definition` | Extend WorkflowDefinitionEntity and WorkflowDefinitionVersionEntity with controlled construction, metadata/draft replacement, validation, publication and retirement methods; no unrestricted setters. Remove legacyExpressionPolicy when the new migration is delivered. |
| `repository.definition` | Extend existing repositories with bounded family/status/cursor reads and required exact lookups. Writes use managed entities/EntityManager persist and explicit flush, not inherited CRUD or detached merge. |
| `exception.definition` | WorkflowDefinitionException with the specification's bounded categories/context. |
| `service.tasktype`, `repository.tasktype` | Add the documented `findByCodeForPublication(String)` catalog operation returning Optional<TaskTypeView>, requiring an existing transaction and holding the selected row stable until its completion. Add only the internal query/lock support it needs; preserve F002's existing public operations. |
| `schemas/workflow-definition/minimal-v1.schema.json` under core resources | Bundled Draft 2020-12 structural schema for the F004 subset, local-only references and explicit closed executable objects. This is the single authoritative machine-readable schema, not a duplicate feature-folder copy. |
| `db/changelog/workflow/changes/002-definition-management.xml` under core resources | New gated drop-column migration, included from the existing master after F001. |

No core controller, runtime command service, fixture runner, production audit adapter or automatic sample registration belongs here. The existing test host should still start without a capability provider; publication/validation then returns the explicit unavailable-capability diagnostics. If more than one provider is registered, fail configuration rather than choosing one arbitrarily. Obtain optional host integration through a constructor-injected provider mechanism; absence is not an allow-all implementation.

## Implementation sequence

1. **Confirm baseline and storage (R01, R10–11).** Read AGENTS and project guidance, the selected F004 contract, F003 implementation and F001 DDL. Record status/revision before changes. The current primary baseline is `4c67943`; recheck rather than assuming it is still current. Never rewrite applied migrations or infer an empty database from earlier observations.
2. **Define the neutral API (R01–02, R04, R07–09).** Implement command/view/diagnostic/capability types from the spec with documented nullability, operation behavior and host transaction/audit preconditions. Reuse established enums. Use immutable copies and safe output limits; do not invent HTTP contracts.
3. **Build ingestion and content identity (R02–03).** Strict duplicate/Unicode/trailing-token parsing; early raw-byte/depth/token bounds; exact numbers, deterministic property ordering/escaping and versioned hashes; storage/designer bounds on the exact persisted text. Saving a draft requires ingestion validity, not a valid graph. Avoid a floating-point intermediate even inside designer metadata. Reject oversized exponent tokens before expensive conversion and never expand exponents to huge strings.
4. **Add structural and semantic validation (R05, R08).** Package the minimal schema; validate with an engine-local Draft 2020-12 implementation. Classify recognized unsupported language features clearly, then check closed structure, graph ownership/reachability/routes and minimal human contracts. Typed models are constructed only after sufficient structure is valid. Bound diagnostic accumulation and serialize the complete report/evidence under their budgets. Provider errors are not document failures.
5. **Wire capabilities/catalog compatibility (R06, R08).** Consume one immutable host snapshot per check and compute deterministic relevant-context provenance. Match exact task/outcome/resolver contracts without invoking business code. Extend the catalog service for transaction-held publication inspection; ordinary validate may observe a current snapshot. Acquire the catalog lock after loading/checking the definition version and before its publication write; keep the order consistent across F004 paths. Verify actual SQL Server/provider behavior and translate conflicts without automatic retries.
6. **Reconcile storage and controlled mutations (R04, R07, R10).** Add the explicitly gated migration and remove the legacy entity property in the same feature. Use existing nullable validation columns as one atomic unit. Keep content, technical lock and business version separate. Preserve publication actor/time on retirement and all content on immutable rows.
7. **Implement administration and reads (R01–09).** Use the managed service's transaction interceptors. Import never overwrites; replacing drafts is expected-version checked; metadata edits affect only the family. Validate/publish persist ordinary authoring failures through normal reports. Flush real writes before after-snapshots, retain actual managed versions and expose no runtime receipt/history side effects. Summary pages fetch limit+1 with stable ID ordering and no count-all/document payload.
8. **Verify and reconcile (R11).** Follow the procedure below within the later task's execution scope. Keep actual outcomes and provider/dependency choices in implementation.md, completion in tasks.md, and update the specification first if a discovery changes behavior.

## Dependency and schema decisions

Retain current Java 17/JDK 21 and parent-managed Spring/Jackson/Hibernate versions unless compatibility evidence requires a focused change. Draft 2020-12 is selected by the language; NetworkNT's Jackson 2 line is a candidate, not a preapproved dependency/version. During implementation inspect local dependencies and official provider documentation, select a compatible exact version and record its effective dependency tree and configuration. Do not upgrade the host's Jackson globally just to satisfy a transitive version without assessment. No schema validator dependency is added by this documentation work.

Implementation selection: NetworkNT 1.5.9 with the existing managed Jackson 2.18.3. Use the bundled closed schema with local fragment references, an external loader that throws, strict types and no default application. Structural validation uses fail-fast provider output to bound provider diagnostic allocation; the separate unsupported-feature scan and graph checks accumulate independent bounded errors. The parser has depth 64, numeric-token length 128 and an early 4 MiB UTF-8 document ceiling; further storage/designer/text limits follow the spec. Exact numeric nodes retain coefficient/exponent spelling without forcing BigDecimal's exponent width. No generic evaluation counters or full-language validator are introduced by this minimal slice.

The runtime schema artifact must use local bundled references only, disable external resolution/network access, reject unknown executable fields and preserve designer values. The minimal contract contains no embedded data schemas, so F004 must not accidentally claim the full L11 profile, format assertions or expression engine is implemented. Author-facing numeric/text/byte limits stay those selected in the spec; additional internal parser/validator controls are bounded engineering choices to verify and document.

The old expression-policy column is removed only under the zero-version-row migration precondition. A precondition failure is a real compatibility gate; do not relax it or add data deletion. Fresh database application runs F001 then F004; an already migrated database runs neither again. Preserve F001's logical path, identities and checksums. No rollback script fabricates obsolete policy values. F003 remains a historical feature baseline; the later implementation handover must explain how F004 supersedes its legacy read-only mapping.

## Transaction and compatibility review

- **CON-01–04 / ASM-01–02:** two existing modules, private core JSON/validation configuration, host persistence and audit; no APOLO entity/security dependency. Current local compatibility is not production compatibility.
- **CON-08 / ASM-05:** immutable published/retired content and stable task codes; actual host support is explicit. Publication serializes its selectable check with catalog changes; runtime reuse must not reapply authoring selectability.
- **CON-06–07 / ASM-03:** apply definition-row optimistic concurrency and host management-audit atomicity. Runtime aggregate claims, receipts, history and delivery are not implemented by this management slice.
- **CON-09–10:** no expressions or guard execution. Administration authorization is host-owned and distinct from optional workflow guards. Unsupported semantics fail publication.
- **CON-12:** no automated tests unless the owner changes that instruction. Preserve behavioral obligations and honest NOT RUN outcomes. No production readiness inferred from build/startup.

## Verification procedure

First inspect mappings, lifecycle branches, exact-value canonicalization, query limits, error/rollback paths and all requirement/scenario coverage. Include positive/negative canonicalization examples and the spec's normative definition as technical artifacts during implementation where useful; examples are not evidence that the implementation executed correctly.

After Java changes, explicitly select installed JDK 21 and run from the root:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

Record the actual toolchain, dependency choices, package result and artifact/resource inspection. No automated tests are created, compiled or run. Documentation-only work runs no build.

When the later implementation task authorizes local startup/database checks, confirm the current configured target and the migration's zero-version-row prerequisite before starting the host. Startup executes Liquibase. Capture prior tracking/schema metadata, verify exactly the new changeset applies and only the legacy column changes, verify JPA/repository/service wiring, then restart the same artifact to confirm no reapplication. Preserve task catalog rows and all unrelated data. Record and stop only the process started for verification after checking its identity. Do not manipulate the shared database to manufacture the blocked migration case; statically inspect its halt path unless a separately authorized environment is available.

Lifecycle CRUD, competing updates, failed-validation persistence, audit rollback, precise numeric/metadata round trips and catalog-disable races require actual behavioral observations to claim verification. A schema-validating startup does not exercise them. Do not add fixtures, automated harnesses or startup writers to bypass the no-tests instruction. If representative manual execution is outside the later task's scope, mark it NOT RUN with the reason and retain the acceptance obligation.

Before handoff, check links/whitespace and scoped diffs, including new files. Reconcile all four documents and any schemas/examples with actual code. Report migration/environment gates and unverified behaviors separately from implementation completion; do not stage, commit or push unless requested.
