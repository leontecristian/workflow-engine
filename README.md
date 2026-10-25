# Workflow engine

Two Maven modules, targeting Java 17 and intended to run on JDK 21:

- `workflow-core`: ordinary library JAR. Owns workflow entities, repositories, services, restricted expressions, integration interfaces/configuration and workflow changelog resources as they are implemented.
- `workflow-test-host`: executable Spring Boot development host. Supplies web endpoints, datasource/transaction infrastructure, JDBC driver and Liquibase execution.

The root `pom.xml` is the parent/aggregator. It manages dependency versions without adding host dependencies to core. Spring Boot 3.4.5 is the current build baseline; compatibility with APOLO's effective dependency versions remains to be verified.

This separate project is the current development phase. Eventually `workflow-core` will move into APOLO's source tree as a sibling Maven module of backend, frontend and configuration, joining APOLO's build/release lifecycle. Backend will depend on core; core will retain its neutral integration contracts and have no dependency on backend implementation classes. A permanently separate repository or publishing process is not required.

At that move, review APOLO's effective root parent, reparent core and align its coordinates/shared dependency versions while preserving Java 17 compilation and ordinary JAR packaging. Keep one authoritative core implementation. Move timing, final coordinates and whether `workflow-test-host` moves into APOLO as an optional development module remain open. The host is always outside backend's production dependency graph. The standalone POMs remain the current build configuration.

The initial nine-table workflow schema is implemented by [F001](docs/features/F001-initial-database-schema/spec.md). [F002](docs/features/F002-task-type-catalog/spec.md) adds the core task-type catalog service and JPA persistence. Its build, entity/repository discovery and schema-validation startup are verified locally; CRUD, concurrency and audit rollback execution remain NOT RUN. Workflow execution services are not implemented yet.

## Project constitution and specifications

Start with the [documentation guide](docs/README.md), [project constitution](docs/constitution.md) and [specification-driven development workflow](docs/specification-driven-development.md). They capture the agreed architecture, specification and evidence requirements, current no-tests policy and remaining decisions traced to the [design sources](docs/design-sources.md).

Project-wide guidance lives in `docs/`; individual feature specifications, plans, tasks and evidence belong in [docs/features/](docs/features/README.md).

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
