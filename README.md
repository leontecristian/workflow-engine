# Workflow engine

**Shared assignment — F016:** import the [Finance GROUP](docs/features/F016-shared-group-role-task-assignment/finance-group-review.json) or [approver ROLE](docs/features/F016-shared-group-role-task-assignment/approver-role-review.json) sample with sample capabilities and selectable SANDBOX_REVIEW. Both sandbox reviewers share one task and complete directly; the second account defaults to the reviewer username plus `2`, with the same password (both are configurable). Membership fixtures remain host-owned without a management endpoint. See the [F016 handover](docs/features/F016-shared-group-role-task-assignment/implementation.md) for verification and deployment requirements.

**Runtime extraction — 2026-11-07:** [F015](docs/features/F015-runtime-task-instance-extraction/implementation.md) shares runtime reads, the complete task form and frozen-command recovery through workflow-web/workflow-ui. Runtime read and completion HTTP groups activate independently; read-only UI needs no command provider. Sandbox identity, policy, transactions and business starts remain host-owned. See the handover for focused verification and scope.

**Definition/catalog extraction — 2026-11-07:** [F014](docs/features/F014-definition-catalog-administration/implementation.md) moves existing administration routes and pages into workflow-web and workflow-ui, with thin sandbox adapters/wrappers. One host administration check protects the explicitly activated group; transaction/audit orchestration remains host-owned. Focused isolated, guarded write/audit, package and browser checks pass. Engine behavior, migrations and APOLO sources are unchanged.
**Shared viewer extraction � 2026-11-07:** [F013](docs/features/F013-shared-instance-viewer/implementation.md) implements three Maven modules (`workflow-core`, ordinary `workflow-web`, executable `workflow-test-host`) plus the Angular 19.2 `workflow-ui` library. The sandbox consumes shared read-only instance metadata/history/graph; variables, tasks, commands and business policy stay host-owned. Unit/package, NgModule, guarded test-database and backend-served production-console browser checks pass; F013 scoped acceptance is complete. APOLO integration is separate. See the [library entry points](workflow-ui/README.md) and [web activation](workflow-web/README.md).

[F011 — Sequential human stages](docs/features/F011-sequential-human-stages/spec.md) adds successive and conditional SINGLE reviews in one pinned instance. Import the [two-stage](docs/features/F011-sequential-human-stages/sequential-review.json), [optional-stage](docs/features/F011-sequential-human-stages/optional-stage-review.json) or [three-stage](docs/features/F011-sequential-human-stages/three-stage-review.json) sample with SANDBOX_REVIEW selectable. Completion results identify the submitted task; the console separately refreshes authorized current work. See the [handover](docs/features/F011-sequential-human-stages/implementation.md) for verification and the remaining interactive browser gap.

Sandbox requests may include optional JSON data at creation. The console keeps the exact numeric values and automatically sends the saved object/array as the `requestData` start input. Leave the editor blank to omit it; other inputs remain in the start form. The workflow must declare a JSON input and explicitly initialize its variable. The updated [request status sample](docs/features/F006-test-host-definition-console/request-status-review.json) does this with an optional `{}` default. Import a new draft/version to use the updated sample; published versions stay unchanged. Request data is read-only after creation, and the instance stores its own snapshot. Restart the backend to apply the additive host migration before using this field.

To exercise a host business status, import [Sandbox request status review](docs/features/F006-test-host-definition-console/request-status-review.json) with sample capabilities enabled and the selectable SANDBOX_REVIEW task type. Create a request (DRAFT), start with inputs `{}` (IN_REVIEW), then Approve or Reject as its assigned reviewer (APPROVED/REJECTED). Explicit service nodes call `sandbox.setRequestStatus`; the host verifies the exact request-instance link and permits DRAFT → IN_REVIEW → APPROVED/REJECTED, with same-status no-ops. Request versions increment only on actual changes. Status changes share the workflow transaction, history and replay guarantees; workflow COMPLETED is separate from the request's business status. Older published definitions do not acquire these actions automatically. See the [sandbox handover](docs/features/F006-test-host-definition-console/implementation.md) for verification and restart requirements.

[F010 — Human-task outcomes](docs/features/F010-human-task-outcomes/spec.md) implements 1–16 choices within one SINGLE human stage, per-outcome comments and exact outcome routes. The console renders actions from the pinned contract. Import [approve/reject](docs/features/F010-human-task-outcomes/approve-reject.json) for a minimal branch without a gateway, or [booking outcomes](docs/features/F010-human-task-outcomes/booking-outcomes.json) with the booking inputs below for branch-specific JSON services. The sample SANDBOX_REVIEW capability accepts COMPLETE, APPROVE and REJECT. See the [handover](docs/features/F010-human-task-outcomes/implementation.md) for automated/HTTP verification and the completed Chrome walkthrough.

[F009 — JSON execution variables](docs/features/F009-json-execution-variables/spec.md) is IMPLEMENTED at revision 3: immutable bounded JSON snapshots, explicit start/service replacements, read-only nested SpEL navigation, exact decimal scale and retained path-aware history. `WorkflowValue`/`WorkflowLiteral` support both scalars and JSON; `WorkflowJsonValue.parse(text)` creates the immutable JSON token. Core and test-host callers use the new baseline together, without pre-F009 readers or conversion layers. See the [handover](docs/features/F009-json-execution-variables/implementation.md) for automated verification and remaining browser/APOLO gaps.

Import the [complete booking-review example](docs/features/F009-json-execution-variables/booking-review.json) into the enabled sample console after creating/selecting `SANDBOX_REVIEW`. Start with raw inputs `{"booking":{"amount":12500.00,"department":{"code":"SALES"},"obsolete":true}}`. The sample `sandbox.refreshBooking` action returns a complete replacement after review and records its effect in the host transaction. Variable pages and history provide exact JSON text and downloads. Existing test data is disposable, but no reset or old-data conversion is performed.

See the [project roadmap](docs/roadmap.md) for current priorities and the path from the F006 sandbox to APOLO integration and the designer. The [feature index](docs/features/README.md) tracks delivery status and links to acceptance evidence.

Three Maven modules, targeting Java 17 and intended to run on JDK 21:

- `workflow-core`: ordinary library JAR. Owns workflow entities, repositories, services, restricted expressions, integration interfaces/configuration and workflow changelog resources as they are implemented.
- `workflow-web`: optional ordinary Spring MVC adapter JAR; explicitly activated viewer and administration groups with required host adapters.
- `workflow-test-host`: executable Spring Boot development host. Supplies web endpoints, datasource/transaction infrastructure, JDBC driver and Liquibase execution.

The root `pom.xml` is the parent/aggregator. It manages dependency versions without adding host dependencies to core. Spring Boot 3.4.5 is the current build baseline; compatibility with APOLO's effective dependency versions remains to be verified.

This separate project is the current development phase. Eventually `workflow-core` will move into APOLO's source tree as a sibling Maven module of backend, frontend and configuration, joining APOLO's build/release lifecycle. Backend will depend on core; core will retain its neutral integration contracts and have no dependency on backend implementation classes. A permanently separate repository or publishing process is not required.

At that move, review APOLO's effective root parent, reparent core and align its coordinates/shared dependency versions while preserving Java 17 compilation and ordinary JAR packaging. Keep one authoritative core implementation. Move timing, final coordinates and whether `workflow-test-host` moves into APOLO as an optional development module remain open. The host is always outside backend's production dependency graph. The standalone POMs remain the current build configuration.

The initial nine-table workflow schema is implemented by [F001](docs/features/F001-initial-database-schema/spec.md). [F002](docs/features/F002-task-type-catalog/spec.md) adds the core task-type catalog service and JPA persistence. Its build, entity/repository discovery and schema-validation startup are verified locally; CRUD, concurrency and audit rollback execution remain NOT RUN. F005 now supplies the minimal core execution slice; its manual acceptance gate remains open.

[F003 — Core persistence model](docs/features/F003-core-persistence-model/spec.md) is IMPLEMENTED at revision 1; see its [evidence](docs/features/F003-core-persistence-model/implementation.md#4-verification) for static/build/startup results and unperformed behavioral checks. Its [plan](docs/features/F003-core-persistence-model/plan.md) and [tasks](docs/features/F003-core-persistence-model/tasks.md) cover the eight remaining entity mappings, established value types and narrow internal read repositories before definition management and runtime execution. F003 adds eight entity mappings, nine enums, two composite IDs and eight internal read repositories; it introduces no schema change or generic CRUD service.

[F004 — Definition management and validation](docs/features/F004-definition-management-validation/spec.md) is IMPLEMENTED at revision 1; see its [implementation record](docs/features/F004-definition-management-validation/implementation.md) for package, local migration/restart/bootstrap results and behavioral gaps. Its four documents specify draft/import/edit/read operations, minimal SINGLE publication validation, immutable publication/retirement and a gated legacy-column migration. F004 is implemented within that subset; lifecycle/validation/concurrency behavior remains NOT RUN, and F005 core execution is implemented with manual acceptance pending.

## Typed bindings and service tasks

The following F007 delivery notes describe its historical baseline. F009 governs the current value APIs, history schema and receipt/fingerprint codecs; pre-F009 replay readers are not retained.

[F007](docs/features/F007-typed-bindings-service-tasks/spec.md) adds exact scalar start inputs/variables and synchronous registered service tasks around the existing SINGLE human task. Existing definitions and command APIs remain supported. New nonempty initializer/output mapping operations emit one VARIABLES_WRITTEN history/event batch, retaining per-variable evidence in authorized history and only nodeKey/writeCount in delivery. Legacy VARIABLE_WRITTEN history and command receipts remain readable/replayable. The existing 128 KiB evidence bound applies to the complete batch; overflow rolls back the command. See the [handover](docs/features/F007-typed-bindings-service-tasks/implementation.md) for isolated tests/builds, T09 owner acceptance and remaining unverified follow-up coverage, and the complete G04 fixture in the specification for manual setup.

With the console enabled and capabilities=sample, sandbox.recordQuantity records a participating host effect before returning recordedQuantity. The host master includes the new append-only service_effect migration; no core migration is added. Administrator instance details expose effect pages; authorized reviewers can inspect variables/history. Failure settings workflow.console.fail-service-node and workflow.console.invalid-service-output-node take an exact node key at startup, default empty, and require the enabled console. Restart with empty settings to restore normal behavior. They are never client command fields; existing workflow.console.fail-event-recording can exercise recorder failure independently. Do not use startup failure settings outside the development sandbox.

## Project constitution and specifications

[F008](docs/features/F008-restricted-expressions-routing/spec.md) adds restricted SpEL conditions and acyclic exclusive branching around one mandatory human stage. Conditional transitions run in their array order; the first true wins, and a separate default is used only after all conditions are false. Expressions read declared scalar `#vars` populated by explicit bindings. Evaluated uninitialized reads, errors and exhausted budgets fail and roll back the command.

Arithmetic uses native SpEL promotion, rounding, floating-point behavior and integral overflow. DECIMAL variables enter evaluation as BigDecimal from exact stored text; decimal literals keep their native SpEL types. Thus integer `5 / 2` is `2`, DECIMAL variables `1 / 2` yield `0` at native scale, and literal `0.1 + 0.2 == 0.3` is false. Native errors and exhausted resource limits fail the command. Authorized history includes ordered `GATEWAY_ROUTED` evaluations and actual scalar reads; its detail page presents the selected route. Delivery payloads omit scalar values. See the F008 handover for actual tests and remaining browser acceptance.

[F005 — Minimal SINGLE workflow execution](docs/features/F005-minimal-single-workflow-execution/spec.md) has its core implementation in place: exact-version start, one directly assigned task, completion to END, early instance claims, command replay, history, bounded reads and required durable host event handoff. F006 now supplies real sandbox adapters and local behavioral observations. Original T11 acceptance remains open pending explicit evidence mapping and remaining checks; see the [F005 handover](docs/features/F005-minimal-single-workflow-execution/implementation.md) and [roadmap](docs/roadmap.md).

[F006 — Test-host workflow console](docs/features/F006-test-host-definition-console/spec.md) adds an opt-in Angular console for catalog/definition management and F005 SINGLE execution, with separate administrator/reviewer accounts, persisted sandbox requests, transactional audit/outbox and explicit local inbox delivery. See its [handover](docs/features/F006-test-host-definition-console/implementation.md) for actual acceptance results and remaining gaps.

Start with the [documentation guide](docs/README.md), [project constitution](docs/constitution.md) and [specification-driven development workflow](docs/specification-driven-development.md). They capture the agreed architecture, specification and evidence requirements, current verification policy and remaining decisions traced to the [design sources](docs/design-sources.md). Isolated unit tests and database integration tests against the dedicated local `apolo_workflow_test` database are authorized under [AGENTS.md](AGENTS.md). Other database targets retain task-specific authorization requirements.

Project-wide guidance lives in `docs/`; individual feature specifications, plans, tasks and implementation/verification records belong in [docs/features/](docs/features/README.md).

## Build

Run from the repository root with `JAVA_HOME` pointing to JDK 21:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

This compiles and packages production code without compiling or running tests. Outputs:

- `workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar`
- `workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar`

Only the host JAR is executable. The host also contains an opt-in, guarded SQL Server integration suite; see [database integration testing](docs/testing/database-integration-tests.md).

## Run the development host

The host defaults to the existing SQL Server Express database `apolo_workflow_dev` at `localhost:1433`, using Windows Integrated Authentication as the user running Java. No database username/password variables are required. JPA and Liquibase share this datasource. Set `WORKFLOW_TEST_DB_URL` only to override the complete connection URL.

The local URL uses encryption with `trustServerCertificate=true` for the development server's certificate. Production TLS and authentication remain APOLO-owned configuration. The host does not provision a database or start Docker.

On Windows with an x64 JDK, the automatically activated `windows-native-auth` Maven profile downloads Microsoft's matching authentication DLL from Maven Central into `workflow-test-host/target/native/` during `generate-resources`. The host's `sqlserver-jdbc.version` property keeps the JDBC JAR and DLL at the same release, currently 12.8.1. The DLL is a local build artifact, outside the application JAR and Git. Rebuild after `clean` to restore it; there is no system PATH or JDK installation change.

Build with the command above, then run from the repository root:

```powershell
java '-Djava.library.path=workflow-test-host/target/native' -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar
```

In IntelliJ, reload the Maven project and select the shared **Workflow test host (Windows)** run configuration. It supplies `java.library.path` and uses the project's JDK; use the configured x64 JDK 21. Run the Maven package command once before launching from IntelliJ, and again if `target/native` has been removed. Adding an ordinary Java run configuration requires the same VM option pointing to the DLL directory.

Alternatively, install core and the parent locally, then run the host through Maven. The Windows profile supplies the DLL path to the forked application JVM:

```powershell
./mvnw.cmd -Dmaven.test.skip=true install
./mvnw.cmd -Dmaven.test.skip=true -f workflow-test-host/pom.xml spring-boot:run
```

Starting the host runs Liquibase against `apolo_workflow_dev`, including its tracking tables. Compilation/packaging alone does not connect to or modify the database. See Microsoft's [Windows integrated authentication documentation](https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url#connecting-with-integrated-authentication-on-windows) for the native DLL requirements.

The host's Liquibase master includes `db/changelog/workflow/workflow-master.xml` from the core JAR. JPA uses `ddl-auto=validate` and does not create or update the schema. Keep sample-domain migrations in the host. Once workflow schema changes are specified, add ordered changesets under core's `db/changelog/workflow/` directory and preserve applied changeset identities and paths.

## Core packages and host integration

### Task type catalog

F002 uses the concrete TaskTypeCatalogService directly, following the [concrete-service convention](docs/package-structure.md#concrete-services-by-default).

Inject the managed `TaskTypeCatalogService` bean from `io.github.leontecristian.workflow.core.service.tasktype`, with commands/queries/results from `io.github.leontecristian.workflow.core.dto.tasktype` and errors from `io.github.leontecristian.workflow.core.exception.tasktype`. It provides `create`, exact `findById`/`findByCode`, bounded cursor `list` and version-checked `update`, using immutable records. Codes are permanent and case-sensitive; disabling selectability preserves exact lookup. Entities, repositories and private service helpers remain unsupported host dependencies even where cross-package access requires public visibility. See the [F002 contract](docs/features/F002-task-type-catalog/spec.md) and [package guide](docs/package-structure.md) for the supported boundary.

The host must authenticate and authorize before exposing data or calling the service. A conceptual administration flow is:

```text
Authenticate and authorize the trusted administrator
Begin the host transaction
  Call catalog.create(command) or catalog.update(command) through the Spring bean
  If change.changed(): persist durable host management audit with trusted actor/time,
    operation, stable code/ID and complete before/after snapshots including versions
Commit the host transaction
Report success to the external caller
```

Writes require an existing transaction; core never commits independently. Results remain provisional until host commit. Audit failure or a catalog error must roll back the transaction; never catch a flush failure and continue it. A no-op has equal snapshots without a forced version increment and need not create a change audit; any attempt audit follows host policy. Reads join a current transaction or open a read-only one. This example defines the integration shape, not an implemented audit/security adapter. Management audit belongs to the host, outside workflow history and command receipts.

### Definition administration

Inject `WorkflowDefinitionService` from `service.definition`, using immutable contracts from `dto.definition` and errors from `exception.definition`. It supplies draft import/replacement, family metadata updates, validation, publication/retirement, exact reads/export and bounded version-summary pages. Versions are supplied explicitly in JSON; publication supports only the minimal START → HUMAN_TASK (SINGLE) → END subset. See the [F004 contract](docs/features/F004-definition-management-validation/spec.md) and [verification gaps](docs/features/F004-definition-management-validation/implementation.md#4-verification).

The host authenticates/authorizes each operation, constructs the trusted `DefinitionManagementContext`, opens the transaction for mutations, persists durable audit from changed before/after summaries and commits before reporting success. Audit failure rolls back the change. A normal failed-validation report can be committed as authoring evidence; a technical failure aborts the transaction. Publication requires one real host `DefinitionCapabilityProvider`; absence produces unavailable-capability diagnostics, with no permissive fallback or seeded registration. Capability declarations never execute business functions during validation.

The F004 forward migration requires an empty legacy definition-version table and removes only `expression_policy`; populated environments halt pending a separately specified conversion. It supersedes F003's legacy field and read-only definition surface. No workflow execution is provided.

### Minimal runtime execution

Use the managed `WorkflowExecutionService` and `WorkflowRuntimeQueryService` in `service.runtime`, with commands/context/results from `dto.runtime` and categorized errors from `exception.runtime`. The host must authorize commands, replay and reads before calling. Mutations require an existing host transaction; results remain provisional until its commit.

Install real `SingleTaskAssignmentResolver` implementations, matching task capability declarations and one transactional `WorkflowEventRecorder`. Optional per-call `StartLinkAction` and its trusted `HostStartIntent` establish host links after identity allocation and before assignment. No adapter is synthesized when support is missing. The current management-only host can bootstrap without these adapters, but cannot execute new workflow commands until they are supplied.

After STALE_VERSION or CONCURRENT_COMMAND, finish rollback and reauthorize before using `findReplay` with the original command/token in a fresh transaction. Do not retry mutation logic or query a failed transaction. Replay returns the original snapshot without callbacks or new events. Host recorders own durable storage/dispatch; this feature provides no outbox table, endpoint or UI. See [F005 handover and acceptance gaps](docs/features/F005-minimal-single-workflow-execution/implementation.md).

### Persistence and module integration

Core code belongs under `io.github.leontecristian.workflow.core`, following the [layer-first package structure](docs/package-structure.md), with features beneath technical layers. `WorkflowConfiguration` remains at the core root as the explicit Spring configuration entry point and scanning marker. It does not create a datasource, transaction manager, web server or migration runner.

The test host imports that configuration and registers core entity/repository packages against its Boot-managed persistence context. APOLO must register the same packages against its existing shared persistence context and transaction manager, accounting for its existing JPA configuration. Workflow service calls must join the host transaction.

For interim integration while development remains separate, APOLO can consume the library using the current development coordinates:

```xml
<dependency>
    <groupId>io.github.leontecristian</groupId>
    <artifactId>workflow-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

After source integration, backend's dependency must match core's coordinates and version in the APOLO reactor; the current personal group and standalone version are not a decision about the final APOLO coordinates.

Add this include to APOLO's existing Liquibase master at the appropriate position:

```xml
<include file="db/changelog/workflow/workflow-master.xml" relativeToChangelogFile="false"/>
```

APOLO remains the single production migration owner. Core ships no global application properties, Liquibase runner or Boot executable. Its public integration interfaces must use workflow-owned or standard Java types, without exposing APOLO entities or security/messaging-provider types.

Preserve the workflow changelog classpath paths and already-applied changeset identities when moving the source module. Retain APOLO's existing entity/repository packages when adding workflow registration to its shared persistence context.

## Local workflow console (F006)

The console is a development tool. Its writes persist in the configured database; it has no reset/delete endpoint. Startup applies the five host-only F006 changesets even when the console is disabled, but creates no catalog, accounts or sandbox records. Never include the test-host master in APOLO production migrations.

Toolchain: JDK 21 compiling Java 17 bytecode; Angular 19.2.25, Material/CDK 19.2.19, CLI/build 19.2.27, TypeScript 5.8.3, Zone.js 0.15.1, RxJS 7.8.2, Node 22.22.0, npm 10.9.4. See the [official Angular compatibility table](https://angular.dev/reference/versions). The profile installs pinned Node/npm locally under `workflow-test-host/target/frontend`; global Node is unnecessary for packaging.

[F012 sandbox acceptance is complete](docs/features/F012-angular-19-frontend-alignment/implementation.md), including production assets under host CSP, exact downloads, command recovery and read-failure behavior. This does not establish APOLO library/runtime integration.

From the repository root, with JAVA_HOME pointing to an installed JDK 21:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd '-Dmaven.test.skip=true' package
./mvnw.cmd '-Dmaven.test.skip=true' -Pconsole-ui package
```

The first command builds the ordinary backend without Node and removes any stale packaged console assets. The opt-in profile first installs/builds `workflow-ui`, then installs its built package into the sandbox, runs the sandbox production build and packages `/console/` assets. Stop a running frontend dev server before reinstalling its dependencies on Windows; its esbuild executable may be locked. Generated assets/dependencies stay ignored. Rebuild with the profile before launching the UI after an ordinary package build.

The UI uses Angular Material tables, forms and responsive navigation with a local theme. The production build checks that CSS loads through an ordinary stylesheet link under the existing CSP. Critical-CSS inlining is intentionally disabled because its generated inline `onload` handler would be blocked. Hard-refresh the console after deploying new assets.

Configure two distinct stable actor references and passwords externally. There are no defaults. This PowerShell example prompts for passwords rather than putting them in shell history:

```powershell
$env:WORKFLOW_CONSOLE_ENABLED = 'true'
$env:WORKFLOW_CONSOLE_ADMIN_USERNAME = Read-Host 'Administrator username'
$env:WORKFLOW_CONSOLE_ADMIN_PASSWORD = (New-Object PSCredential('console', (Read-Host 'Administrator password' -AsSecureString))).GetNetworkCredential().Password
$env:WORKFLOW_CONSOLE_REVIEWER_USERNAME = Read-Host 'Reviewer username'
$env:WORKFLOW_CONSOLE_REVIEWER_PASSWORD = (New-Object PSCredential('console', (Read-Host 'Reviewer password' -AsSecureString))).GetNetworkCredential().Password
& "$env:JAVA_HOME/bin/java.exe" '-Djava.library.path=workflow-test-host/target/native' -jar workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar --server.port=8080
```

Open `http://127.0.0.1:8080/console/`. Enabled mode binds loopback and rejects a non-loopback server.address. Use separate browser profiles/private sessions for the two accounts. Session login/logout and CSRF protect mutations; usernames/time/roles come from the server. Clear the password environment variables after stopping the host. Default disabled mode returns 404 for all console routes/assets and registers no sandbox adapters.

For IntelliJ, select **Workflow console (local)** from the shared `.run` configurations. It runs the host main class using `corretto-21`, the repository working directory and the native DLL directory. It loads `.local/workflow-console.properties`, which is ignored and must contain your local console/account settings; passwords never belong in the shared run configuration. Its before-launch Make step compiles Java only, including the dependent workflow-web module; it does not build or install frontend packages. Run Maven packaging once after checkout or clean to prepare the native DLL, as described above. IntelliJ's Run/Stop controls manage the host process directly.

### Fast frontend development

Start **Workflow console (local)** for the backend on port 8080, then **Workflow console frontend** for the development UI. The frontend configuration runs `npm run start:console`: it builds workflow-ui, refreshes only the installed @workflow-engine/ui package copy, then starts ng serve. It does not install dependencies or build production console assets. Backend restarts do not rebuild the UI; restart the frontend after shared-library changes. Stop the frontend before refreshing its package copy. One-time setup, and setup after dependency/lockfile changes, remains manual: run `npm ci` and `npm run build` in workflow-ui, then `npm ci` in workflow-test-host/frontend (see [workflow-ui](workflow-ui/README.md)). From a terminal, use `npm run start:console` in workflow-test-host/frontend for the same build-and-start behavior; plain `npm start` serves the existing installed library. Use the pinned Node/npm versions above. Open **http://127.0.0.1:4200/console/**, or **http://127.0.0.1:4200/console/login** to sign in. Production packaging remains the separate manual root command `./mvnw.cmd -Pconsole-ui '-Dmaven.test.skip=true' package`.

The Angular development server binds only to `127.0.0.1:4200`, watches source changes and reloads the browser. It serves frontend assets locally and proxies `/console/api/**` (including exports), `/console/login`, `/console/logout` and `/error` to `http://127.0.0.1:8080` without changing paths. Use the 4200 URL throughout login and application use so session cookies, redirects and CSRF requests stay on the browser's same origin. Backend credentials, authorization and CSRF checks still apply. No CORS or backend security changes are needed. Restart npm after changing proxy configuration. Stop the npm configuration or press Ctrl+C when finished.

The port-8080 packaged UI remains available after a `-Pconsole-ui` package build, but it does not update with source edits. Use the port-4200 console during frontend development; Maven packaging remains the production-asset check and is no longer required for each UI edit. The development build skips production optimization and uses source maps; the existing production build/CSP check is unchanged.

Administrator walkthrough:
The instance detail page includes a read-only **Workflow graph** from its pinned nodes/transitions, including service tasks, branches, merges and loops. This visualization does not enable new execution features. Blue marks the current active position, green marks recorded departures/completed END, and gray marks unvisited routes; repeated visits retain the current position in blue. Use zoom/reset and horizontal/vertical scrolling for larger graphs. Node links open the latest recorded task, a multi-task activity, or ordered history. The viewer bounds topology at 200 nodes/500 transitions and pages history through the initial snapshot up to 12,000 events; missing/inconsistent evidence fails visibly. Administrator and stored-assignment-authorized reviewer access share the same policy. **Refresh current state** reloads metadata and graph together; graph failures preserve ordinary details/tasks/history. Retired pinned versions remain inspectable. Cancelled-instance projection remains unavailable until its evidence contract is specified. Restart the backend and refresh the frontend after updating; see [F006 revision 6](docs/features/F006-test-host-definition-console/spec.md#revision-6---bounded-general-graph-viewer-owner-request-2026-11-01).

Open **Instances** (administrator only), or `/console/#/instances`, to monitor persisted workflows. It defaults to **Running (ACTIVE)**, including workflows waiting for a human task. Filter Completed, Cancelled or All statuses; each row shows the pinned workflow/version, current step, start time/actor and result. Inspect an instance or open its Tasks/History directly. **Refresh instances** reloads the first page using the selected filter; refresh is manual. Page controls fetch bounded results without claiming a global total. Cancelled is a read filter, not a cancellation command.

1. Open Task types and explicitly create the prefilled `SANDBOX_REVIEW` entry as selectable. Capabilities and catalog selectability are separate prerequisites; no startup seed supplies the entry.
2. Import the [F004 example](docs/features/F004-definition-management-validation/spec.md#supported-publication-contract), choosing a fresh key and explicit version. Browse the family/version, edit raw text, save, validate and publish. Export downloads exact canonical JSON. Validation does not save unsaved edits. Published/retired content is read-only; family metadata is separate.
3. Create a named sandbox request. Enter its definition family ID, load published versions and choose the exact version; use the unlinked request ID/token, then start. The host links the allocated instance before resolving its task to the configured reviewer. Inspect the instance, tasks, history, audit and pending events.
4. In the reviewer session, open My tasks, inspect the pinned outcome/comment requirements, and complete. Inspect the completed instance and authoritative submission. An administrator cannot bypass the stored assignment.
5. Command recovery retains the original UUID/body/token in memory. Retry submits that exact command; Find result only looks up its receipt. Original replay snapshots are distinct from current instance state. Copy/download the envelope before a full reload and paste it to recover; it may contain a private comment. Logout/account changes clear the buffer. Nothing is stored in localStorage.
6. As administrator, dispatch pending events to the durable local inbox and deliberately redeliver one. Inbox duplicate suppression is a local sink guarantee, not distributed exactly-once messaging. There is no scheduled dispatcher.

Startup-only settings, all under `workflow.console`: `capabilities=sample` (default) provides the real sandbox resolver/task support; `none` removes that support while retaining durable recording. `fail-audit`, `fail-start-link`, `fail-event-recording`, and `fail-runtime-after-result` default false and require enabled local mode. They deliberately abort their specified transactions; the capabilities page displays the active settings. Change them only by restarting, and restore normal settings after verification. Changing a reviewer username never reassigns stored tasks. Disabling catalog selectability does not disable published execution; retiring a version prevents new starts while pinned instances and successful receipt replay remain usable.

Isolated tests, separately from packaging:

```powershell
./mvnw.cmd '-Dtest=*UnitTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
# With Node 22.22.0/npm 10.9.4 selected:
cd workflow-test-host/frontend
npm ci
npm run test:unit
```

Surefire includes only `*UnitTest.java` in both modules. The preserved `WorkflowTestHostApplicationTests` now uses the guarded test database configuration and runs only in the opt-in Failsafe suite alongside `*IT.java`. Frontend tests use Node's built-in runner with no host/browser/database. Manual acceptance and retained development records are documented in the [F006 handover](docs/features/F006-test-host-definition-console/implementation.md); unit/build success does not establish transaction or concurrency acceptance.

## Database integration tests

The dedicated SQL Server database is `localhost:1433/apolo_workflow_test`, using Windows authentication. It is separate from the normal host's `apolo_workflow_dev`. With JDK 21 selected, run from the repository root:

```powershell
./mvnw.cmd -B -ntp -Pintegration-tests verify
```

This runs unit tests, applies real migrations to the test database, validates JPA mappings and runs the host integration suite without opening an HTTP port. The profile supplies the native authentication DLL path. Default `test`/`verify` runs remain unit-only. Reports are in `workflow-test-host/target/failsafe-reports`.

The test base refuses alternate datasource/migration URLs before startup and checks the database catalog on each connection. Test fixtures use unique identifiers and clean up only their own rows. Do not run builds in another agent's active checkout or run competing suites against the same test database. See [setup, policy and recorded evidence](docs/testing/database-integration-tests.md).
