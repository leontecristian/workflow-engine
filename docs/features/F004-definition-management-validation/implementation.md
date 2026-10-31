# Implementation — F004 Definition management and validation

## 1. Implementation Summary

**IMPLEMENTED revision 1 — 2026-10-31.** T01–T10 are complete for the selected code and verification dispositions. Draft import/replacement, metadata edits, exact reads/export, bounded summary pages, minimal SINGLE validation, publication and retirement are implemented. Behavioral acceptance remains **NOT RUN**, separately from package/bootstrap/migration PASS.

The owner authorized implementation, JDK 21 packaging, local migration/startup and read-only inspection without fixtures/application-data writes. Starting HEAD: `e1807db6bdfab02d270ea7161f8da753fc632101`, clean working tree. No files were staged, committed or pushed. No separate evidence/verification documents were created.

## 2. Components and Files Changed

- **T02/T08, R01/R04/R07–09:** immutable commands/context, family/version views/summaries, change/check/page/report/diagnostic/capability DTOs in dto.definition, and the internal typed minimal graph in dto.definition.document. Collections are copied; hashes cross the API as lowercase hex. Summary/change/page results contain no full documents or provider objects.
- **T03, R02–03:** DefinitionDocumentCodec, DefinitionContentCanonicalizer and DefinitionInputs in service.definition. Strict ingestion and exact canonical text/hash retain all designer content.
- **T04/T05, R05–06/R08:** DefinitionValidationService, bounded DefinitionDiagnostics, the justified DefinitionCapabilityProvider extension and the single bundled schemas/workflow-definition/minimal-v1.schema.json. Schema/semantic checks require exactly START → HUMAN_TASK(SINGLE) → END; no represented business function is executed.
- **T06/T07, R01/R04/R07/R10:** controlled definition constructors and lifecycle mutations; validation-envelope set/clear; immutable publication/retirement; legacyExpressionPolicy removed. WorkflowDefinitionService coordinates managed persistence/flush, expected-version/state checks, content reuse, immutable audit handoff and categorized errors.
- **T05/T08:** scalar-only DefinitionVersionRow summary projection and owner/status/cursor query, excluding both JSON payloads. The only F002 addition is the documented findByCodeForPublication service/repository operation: MANDATORY, PESSIMISTIC_WRITE and refresh under the held lock so an earlier managed snapshot cannot supply stale selectability.
- **T06:** changes/002-definition-management.xml and one master include. F001 is unchanged. No runtime-table migration, seed, fixture, endpoint or F005 execution.
- **T09/T10:** the four feature records and project navigation are reconciled. Raw logs/snapshots remain ignored under root target/.

## 3. Technical Decisions and Deviations

No material contract conflict or behavioral scope deviation was found.

**Dependencies.** Selected NetworkNT json-schema-validator **1.5.9** after inspecting its [tagged documentation](https://github.com/networknt/json-schema-validator/blob/1.5.9/README.md), factory API and installed bytecode. The effective tree preserves managed Jackson **2.18.3**, Hibernate **6.6.13.Final**, Spring **6.2.6** and Boot **3.4.5**. Validator transitives include itu 1.14.0, Jackson YAML 2.18.3/SnakeYAML 2.3 and SLF4J 2.0.17; only JSON is read. Jackson core and Spring beans are declared directly because core imports their APIs; their resolved versions do not change. No host mapper/global configuration was changed.

The bundled Draft 2020-12 schema compiles once, uses local fragment references and an external loader that throws, with strict types/no defaults. Provider fail-fast output bounds structural diagnostic allocation; installed bytecode confirms its default output converts FailFastAssertionException into returned messages. Other provider failures become HOST_CONFIGURATION and roll back. Unsupported-feature and graph checks independently accumulate bounded errors. Constructor/schema bootstrap passed during startup; actual document-validation calls were not executed.

**Exact values/bounds.** Parsing has root-at-1 depth 64, numeric-token length 128 and early 4 MiB UTF-8 input bounds, plus the specified canonical/designer/collection/text limits. Private exact numeric nodes preserve bounded coefficients and BigInteger exponents without floating-point conversion or exponent expansion. Structural validation substitutes ordinary integer nodes for already-validated identity values and an empty object for opaque designer contents; canonical source remains intact and is never rebuilt from the typed graph.

Static walkthroughs trace 1 / 1.0 / 10e-1 to canonical 1, 12.50 to 125e-1, zero spellings to 0, and retain string/array/null/absence/designer distinctions. These are source reasoning, **not executed examples**. Diagnostics cap accumulation and check entry/report bytes with an independent failure flag; oversized pointers are omitted whole. Full required provenance exceeding storage capacity fails explicitly. Context includes the validator/schema digest, schema/canonicalization versions, referenced catalog state and relevant sorted capability descriptors/absence. Provider absence fails closed; duplicate IDs, malformed data or multiple providers are configuration errors.

**Transactions/compatibility.** Public mutations are MANDATORY; reads use read-only REQUIRED. Every mutation checks expected managed versions and flushes real changes before returning actual tokens; no-op results reserve no state. Draft edits increment content revision and clear all seven validation fields together. Publication freshly checks content, catalog and host support; ordinary failed validation may commit as evidence, while technical errors roll back. Host authorization and durable management audit/commit remain required.

Publication acquires its catalog lock after loading/checking the definition version, refreshes under that lock and holds it through host transaction completion. Actual SQL Server lock/race behavior remains unverified. F004 explicitly supersedes F003's legacy field and read-only definition surface; its runtime mappings and original F002 catalog operations are otherwise preserved. Populated legacy versions still require a separately specified data-preserving conversion.

## 4. Verification

### Historical preparation

The documentation-only baseline was 4c67943. Preparation reported 74 relative links/anchors, 11 requirements/10 scenarios/10 tasks, inline normative JSON inspected as document content and whitespace PASS. It ran no build, startup, database operation or application test. Those observations are historical, not F004 implementation evidence.

### Package and source review — PASS

Explicit JAVA_HOME selected Corretto **21.0.13+10-LTS x64**; java -version and wrapper -version confirmed JDK 21/Maven **3.10.0**. Commands from the root:

```powershell
./mvnw.cmd '-Dmaven.test.skip=true' package
./mvnw.cmd '-Dmaven.test.skip=true' -pl workflow-core dependency:resolve
./mvnw.cmd '-Dmaven.test.skip=true' -pl workflow-core dependency:tree
```

Quoting preserves the prescribed property in PowerShell. All package attempts passed after initial implementation and subsequent source/dependency review; the final package finished **18:28:20 +02:00**. Production code compiled with release 17. Both modules explicitly skipped test compilation/execution; no automated tests were created or run. Logs: target/f004-build.log, target/f004-final-build.log, target/f004-dependencies.log and target/f004-dependency-tree.log.

Core JAR inspection found the new service/validator, schema and changeset; it remains an ordinary JAR, with only the host repackaged. Static review covered every operation/lifecycle branch, ingestion/canonicalization bounds, defensive copies, fixed query predicates/limit+1 continuation, scalar page projection, transaction/failure paths and F001 preservation. No harness, startup writer or temporary endpoint was introduced.

### Local migration/bootstrap/restart — PASS

Target: localhost:1433/apolo_workflow_dev, Windows native authentication and matching existing JDBC/auth DLL 12.8.1. No WORKFLOW/SPRING/SERVER_PORT/JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS overrides were present. Before startup, read-only SQL found zero definition-version rows and the expected VARCHAR(100) legacy column. The changeset independently enforces SQL Server, table/column existence and COUNT_BIG(*) = 0 with HALT. Its populated-data rejection path was reviewed statically, **not executed** against manufactured rows.

Captured metadata with the following SELECT-only SQL file:

```powershell
sqlcmd -S tcp:localhost,1433 -d apolo_workflow_dev -E -C -l 10 -b -W -s '|' -i target/f004-metadata.sql -o target/f004-before.txt
# Repeated to f004-after.txt, f004-restart.txt and f004-final-metadata.txt.
```

The ignored file selects database identity, Liquibase IDs/paths/checksums, all wf column metadata, FK actions/trust, CHECK-definition hashes/trust, index columns/order/filter/clustering and row counts. All nine workflow tables were empty before/after. No fixture/application-data DML was issued.

Hidden temporary processes used the documented JDK/native-library/JAR command with --server.port=0:

- **PID 38768**, created 18:22:44.935 +02:00, started at 18:22:55.463: exactly the F004 changeset applied (one run/two previously run). Nine explicit entity-table bindings and nine repositories; successful JPA validation, EntityManagerFactory, service/schema bootstrap and application startup.
- **PID 8228**, created 18:23:33.295, same-artifact restart at 18:23:42.726: database up to date, zero run/three previously run; post-migration/restart snapshots identical.
- **PID 20588**, created 18:27:08.879, reviewed Java artifact started at 18:27:18.244 after technical-error handling refinement: successful startup, no reapplication and metadata identical. This preceded only explicit declarations of already-resolved Jackson core/Spring beans dependencies; final packaging after that POM clarification passed with unchanged source/resolved versions.

Each PID/executable/command/creation time was saved and checked against live CIM data before stopping only that process. All three were stopped; a subsequent process check confirmed absence. Logs/identities: target/f004-host1.*, target/f004-host2.*, target/f004-final-host.* and target/f004-*process*.json.

The only before/after differences were the removed expression_policy column and new F004 tracking row. Existing IDs/author/logical paths/checksums remain unchanged:

- f001-001-create-wf-schema: `9:a759c434c85142e959b6b927a4949d90`.
- f001-002-create-initial-tables: `9:3333ca75046b0857a7a23556e03e0d52`.
- New f004-001-drop-legacy-expression-policy / workflow-engine / db/changelog/workflow/changes/002-definition-management.xml: `9:71ec11105b310c821a80ce7977a96b89`.

Catalog/runtime columns, constraints, CHECK hashes, FK actions, indexes and row counts were unchanged. This establishes local migration/bootstrap compatibility, not lifecycle/concurrency behavior.

### Acceptance disposition

- **AC01–AC03:** static ingestion/canonicalization/import/edit/no-op/revision review passed; behavioral import/reuse/conflict, exact-value round trips and rejected-input rollback **NOT RUN**.
- **AC04–AC05:** static minimal schema/graph/capability/evidence/publication-lock review and schema bootstrap passed; actual validation/publication/provider failures/catalog-disable races/lock SQL behavior **NOT RUN**.
- **AC06–AC09:** static immutability, expected-version/flush/audit handoff, failed-evidence and scalar page review passed; lifecycle CRUD, failed-validation commit, concurrent updates/no-ops, audit rollback, byte-boundary behavior and page results **NOT RUN**.
- **AC10:** empty-table migration, restart, checksum/schema comparison and cleanup **PASS**; populated-table blocked execution **NOT RUN**, because fixture/environment manipulation was not authorized.
- Automated tests **NOT RUN by owner instruction**. Final links/new-file/whitespace review is recorded below.

Final review **PASS**: all 80 relative file-link destinations across the four feature records and four navigation/source documents exist; new anchors reference the inspected headings. All 35 new files (33 Java files, the schema and migration) have no trailing whitespace. `git diff --check` passed; existing LF/CRLF normalization notices are not failures. F001, runtime mappings/repositories, host configuration and test sources have no diff. The staged diff is empty. PIDs 38768, 8228 and 20588 are absent. No extra process documents, fixtures or generated artifacts are included in the tracked changes.

### Review fixes — 2026-10-31

Both Bugbot findings were corrected within the existing F004 contract (T04/T06, R05/R10):

- **Initialization shape:** `minimal-v1.schema.json` now declares `startPolicy.initialize` as an object with `maxProperties: 0`, matching language design section 12.1.6. Omission and `{}` remain supported; `[]` is structurally invalid. `DefinitionValidationService` now recognizes nonempty initialization objects as `UNSUPPORTED_FEATURE`, alongside nonempty input mappings, instead of checking initialization as an array.
- **Persistence error translation:** `WorkflowDefinitionService.storage` checks that Hibernate's constraint name is non-null before looking it up in the immutable definition-key set. Null/unknown constraint names can now continue through cause-chain inspection and reach `HOST_CONFIGURATION` when no recognized conflict is present, retaining the original failure as the cause. Recognized definition-key violations, the existing SQL Server duplicate-key fallback and optimistic-lock conflicts still map to `CONFLICT`.

Verification results for these corrections:

- **PASS — static review:** inspected the schema's optional field/type/property limit, unsupported-feature detection before structural validation, and error translation for null, unknown and recognized constraint names. These are source walkthroughs, not executed behavioral cases. No specification or scope change was required.
- **PASS — compilation/package:** selected Corretto **21.0.13+10-LTS x64**, confirmed with `java -version`, and ran `./mvnw.cmd '-Dmaven.test.skip=true' package`. Both modules succeeded at **18:48:55 +02:00**, compiling production sources with release 17 and explicitly skipping test compilation/execution. The initial sandbox attempt could not open Maven's local-cache lock and stopped before compilation; the retry with cache access succeeded. Logs: `target/f004-review-fixes-build.log` and `target/f004-review-fixes-build-retry.log` (Git-ignored).
- **PASS — final source/document hygiene:** inspected all three corrected source/resource sections and this record; `git diff --check` and a trailing-whitespace scan of the three untracked implementation files passed.
- **NOT RUN — behavioral verification:** actual validation calls for omitted/empty/nonempty initialization, malformed array input, and persistence exception translation/rollback were not executed. No automated tests were added or run under the owner policy. No host was started and no database operation was performed for these corrections; the earlier migration/bootstrap evidence remains historical.

These corrections and this record remain unstaged and uncommitted.

## 5. Known Limitations

Only the minimal three-node/two-transition SINGLE subset can publish. Full-language validation, expressions, ALL, variables, nonempty inputs/results, service tasks, endpoints, F005 and APOLO adapters remain outside scope. Real capability declarations are host-supplied; none is seeded. Identity, authorization, durable management audit, commit handling and upload-envelope bounds remain host responsibilities.

The behavioral scenarios above require separately authorized evidence before production acceptance. Populated legacy version tables block migration until a data-preserving conversion is specified. Empty-schema startup/packaging proves none of those behaviors.

## 6. Remaining Work

No selected F004 implementation task remains; the explicit verification gaps are follow-on obligations. The four feature documents remain the only process records. Changes are unstaged/uncommitted; nothing was pushed. No owned temporary host remains.
