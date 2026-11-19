# APOLO technical summary

**Architecture adoption — 2026-11-07:** owner-supplied frontend/build/controller snapshots establish declared Angular/Material 19.2.x ranges, NgModule routing/bootstrap, host authentication/localization providers, Maven frontend JAR packaging and contextual controller authorization. These are source observations, not effective-build or deployment verification. The owner adopted Angular 19 workflow-ui and optional host-consumed workflow-web under constitution 6.0.0; the [integration record](../../architecture/workflow-ui-web-integration.md) retains evidence and open contracts. No dependencies, POMs or F011 behavior change in this documentation task. Exact compatibility and runtime integration still require verification.

> **Updated 2026-10-24.** This document contains the current confirmed platform context and integration direction. Earlier reference material is preserved in the [architecture discussion archive](../../archive/apolo-architecture-discussion.md) and does not override the [consolidated design](../../architecture/workflow-engine-design.md).

## Confirmed Java baseline

- **Compilation target: Java 17.** The project owner confirmed that APOLO's Java 17 POM settings are current and correct.
- **Runtime: JDK 21.** Confirmed by the project owner.
- **Compiler JDK/toolchain: OPEN verification.** This is distinct from both the target version and the production runtime.

The workflow library and sandbox must target Java 17 compatibility and be tested on JDK 21. Do not change the target to Java 21 or describe the Java 17 POM settings as obsolete.

## Build evidence and remaining verification

The parent POM excerpt preserved in the [architecture discussion archive](../../archive/apolo-architecture-discussion.md) describes the frontend, backend and configuration modules and Java 17 source/target settings. The separate [apolo-backend-pom.xml](apolo-backend-pom.xml) file in this directory is the APOLO backend WAR POM, inheriting from the APOLO parent; it is not the parent aggregator or a new workflow build.

The notes record Spring Boot 3.4.5 and specific dependency versions. These remain reference values until verified against the application's effective build. Current Spring Framework, Hibernate/JPA, JSON, Liquibase, driver, Angular and SQL Server compatibility versions are OPEN verification items. This update does not modify the reference POM.

## Agreed workflow integration direction

### APOLO source findings (2026-11-19)

APOLO revision `0a4ce46f7` declares Spring Boot 3.4.5 and Java 17. The backend WAR consumes the frontend JAR. The Angular 19 NgModule shell owns routing and Keycloak session handling. Its schedule route uses the optional ID as a project or people filter. A shared Task page needs a host route and an authorized link to the booking. The combined WAR dependency graph, including Jackson and Hibernate, and the Angular package build remain unverified.

`LiquibaseConfig` runs the host master against its injected datasource when enabled. The integration still needs to verify discovery of both sets of entities and repositories and rollback of booking and workflow writes in one transaction. Existing booking mutations publish JMS through `BookingEventHandler`. The checked-in `ArtemisJmsConfig` disables embedded broker persistence, so this source path alone does not establish durable workflow handoff.

Calendar edits, transfers, recurrence, planning updates and imports can write bookings through different services. For example, `BookingTransferService` checks project permissions and calls a lower-level booking update, while `LeaveImportBookingV2Service` has dedicated update and delete paths. A calendar-only check would leave other writers outside the [proposed booking edit rule](booking-business-analysis.md#booking-edits-during-cross-team-approval).

### Library integration

The [standalone Maven reactor](../../../pom.xml) contains three modules. `workflow-core` supplies the Spring/JPA engine, `workflow-web` supplies the optional HTTP adapter, and `workflow-test-host` is the executable sandbox. The Angular 19 [workflow-ui library](../../../workflow-ui/README.md) builds separately. APOLO will consume web/core in backend and UI in its existing frontend, with business orchestration and contextual authorization owned by APOLO. Core's eventual source integration remains agreed; exact locations/coordinates for the added libraries remain OPEN. Internal packages still replace separate expression, persistence and starter artifacts.

The supplied backend POM represents a large host application with many infrastructure dependencies; it must not become core's parent or dependency. Future inheritance from APOLO's root parent is intended, subject to reviewing its effective dependency/plugin configuration. Core declares its own required libraries, has no backend implementation or test-host dependency, and keeps host integration contracts free of JPA entities and security/messaging-provider types. Share versions through root dependency management without pulling backend-only infrastructure into core. APOLO supplies runtime infrastructure and business extensions.

Core owns `db/changelog/workflow/workflow-master.xml`. The test host includes it from its own master to migrate a disposable local SQL Server database. APOLO includes the same resource from its existing Liquibase master and remains the single production migration owner. Core must not start a separate migration process.

Source integration must preserve changelog resource paths and applied changeset identities, and extend APOLO's existing entity/repository registration to include workflow packages in the shared persistence context and transaction manager. Simply moving a directory or listing it in `<modules>` does not perform parent-POM inheritance or persistence integration.

APOLO retains business orchestration, booking-specific endpoints, identity/authorization policies, transaction infrastructure, database configuration, migrations and external delivery/operations. The workflow library owns reusable execution, task and expression-routing contracts and participates in the host transaction. A standalone business decision engine is optional and is not implemented in the first stage.

The agreed phases are separate development now and an APOLO sibling module later. Core does not become a package inside backend. A permanently separate repository or independently published product is not required. Move timing, final coordinates and the test host's future location remain open; the current standalone build stays in place until that move.

## Historical background

The [archived application reference and architecture discussion](../../archive/apolo-architecture-discussion.md) contains the earlier parent POM excerpt, dependency notes and module trade-offs. Those alternatives do not override the current separate-development-to-APOLO-module direction.
