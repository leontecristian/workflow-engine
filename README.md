# Workflow engine

Two Maven modules, targeting Java 17 and intended to run on JDK 21:

- `workflow-core`: ordinary library JAR. Owns workflow entities, repositories, services, restricted expressions, integration interfaces/configuration and workflow changelog resources as they are implemented.
- `workflow-test-host`: executable Spring Boot development host. Supplies web endpoints, datasource/transaction infrastructure, JDBC driver and Liquibase execution.

The root `pom.xml` is the parent/aggregator. It manages dependency versions without adding host dependencies to core. Spring Boot 3.4.5 is the current build baseline; compatibility with APOLO's effective dependency versions remains to be verified.

This separate project is the current development phase. Eventually `workflow-core` will move into APOLO's source tree as a sibling Maven module of backend, frontend and configuration, joining APOLO's build/release lifecycle. Backend will depend on core; core will retain its neutral integration contracts and have no dependency on backend implementation classes. A permanently separate repository or publishing process is not required.

At that move, review APOLO's effective root parent, reparent core and align its coordinates/shared dependency versions while preserving Java 17 compilation and ordinary JAR packaging. Keep one authoritative core implementation. Move timing, final coordinates and whether `workflow-test-host` moves into APOLO as an optional development module remain open. The host is always outside backend's production dependency graph. The standalone POMs remain the current build configuration.

The initial nine-table workflow schema is implemented by [F001](docs/features/F001-initial-database-schema/spec.md). [F002](docs/features/F002-task-type-catalog/spec.md) adds the core task-type catalog service and JPA persistence. Its build, entity/repository discovery and schema-validation startup are verified locally; CRUD, concurrency and audit rollback execution remain NOT RUN. Workflow execution services are not implemented yet.

[F003 — Core persistence model](docs/features/F003-core-persistence-model/spec.md) is IMPLEMENTED at revision 1; see its [evidence](docs/features/F003-core-persistence-model/implementation.md#4-verification) for static/build/startup results and unperformed behavioral checks. Its [plan](docs/features/F003-core-persistence-model/plan.md) and [tasks](docs/features/F003-core-persistence-model/tasks.md) cover the eight remaining entity mappings, established value types and narrow internal read repositories before definition management and runtime execution. F003 adds eight entity mappings, nine enums, two composite IDs and eight internal read repositories; it introduces no schema change or generic CRUD service.

[F004 — Definition management and validation](docs/features/F004-definition-management-validation/spec.md) is IMPLEMENTED at revision 1; see its [implementation record](docs/features/F004-definition-management-validation/implementation.md) for package, local migration/restart/bootstrap results and behavioral gaps. Its four documents specify draft/import/edit/read operations, minimal SINGLE publication validation, immutable publication/retirement and a gated legacy-column migration. F004 is implemented within that subset; lifecycle/validation/concurrency behavior remains NOT RUN, and F005 runtime execution remains later work.

## Project constitution and specifications

[F005 — Minimal SINGLE workflow execution](docs/features/F005-minimal-single-workflow-execution/spec.md) is READY for implementation. Its four documents specify exact-version start, one directly assigned task, completion to END, concurrency, command replay, history and durable host event handoff. Runtime execution is not implemented yet.

[F006 — Test-host definition console](docs/features/F006-test-host-definition-console/spec.md) is READY for implementation. Its four documents specify a small Angular application inside the test host, authenticated management endpoints, host audit and manual F002/F004 verification. The console is not implemented yet; current build/start commands below remain unchanged. Runtime UI/endpoints are outside its current scope.

Start with the [documentation guide](docs/README.md), [project constitution](docs/constitution.md) and [specification-driven development workflow](docs/specification-driven-development.md). They capture the agreed architecture, specification and evidence requirements, current no-tests policy and remaining decisions traced to the [design sources](docs/design-sources.md).

Project-wide guidance lives in `docs/`; individual feature specifications, plans, tasks and implementation/verification records belong in [docs/features/](docs/features/README.md).

## Build

Run from the repository root with `JAVA_HOME` pointing to JDK 21:

```powershell
./mvnw.cmd -Dmaven.test.skip=true package
```

This compiles and packages production code without compiling or running tests. Outputs:

- `workflow-core/target/workflow-core-0.0.1-SNAPSHOT.jar`
- `workflow-test-host/target/workflow-test-host-0.0.1-SNAPSHOT.jar`

Only the host JAR is executable. The pre-existing context test was relocated with the host; no new tests were added.

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
