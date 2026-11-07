# Workflow UI and REST library integration

**Date:** 2026-11-07. **Status:** PROPOSED architecture, with Angular 19 alignment AGREED by the owner. **Scope:** documentation only; no implementation, dependency change, source move or F011 modification is authorized by this record.

## Purpose and decision status

Allow APOLO to embed reusable workflow pages, components and REST adapters within its existing Angular application and Java deployment. APOLO remains responsible for business behavior, access policy and infrastructure. The sandbox should eventually consume the same reusable functionality with sandbox-specific adapters.

| Decision | Status and meaning |
| --- | --- |
| Align the reusable workflow UI with Angular 19 | AGREED on 2026-11-07. Target APOLO's declared 19.2.x line, including compatible Material/CDK dependencies. Exact patch versions and toolchain remain verification work. |
| Extract `workflow-ui` as an Angular library | PROPOSED. Full feature pages and embeddable components; no independent production bootstrap or login. |
| Extract `workflow-web` as an optional Spring MVC library JAR | PROPOSED. Reusable HTTP mapping, requiring host integration and explicit API activation. |
| Retain a sandbox application shell | PROPOSED. Evolve the existing console to consume the UI library; a second playground application is not required. |
| Introduce a third Maven module | PROPOSED architectural amendment, not an exception already in force. Current C03/CON-02 still require exactly two modules. |
| Replace the later standalone production designer with an embeddable library feature | PROPOSED. A development shell remains useful; designer implementation still follows first APOLO integration. |

This is an architecture discussion record, not a READY implementation feature. Later extraction/alignment work needs its own four-document feature specification. Do not interpret it as a request to downgrade the existing console, alter F011 or change its acceptance baseline.

Authority: [constitution](../constitution.md) C03–C10; [constraints](../constraints.md) CON-01–04/06–12; [assumptions](../assumptions.md) ASM-01–06/09; [source precedence](../design-sources.md). Engine language, publication, pinned versions, concurrency, exact JSON values and successful-command replay remain governed by existing feature contracts.

## Evidence from the supplied APOLO references

The owner supplied reference copies on 2026-11-07 in the separate `apolo-docs` repository, under `workflow-engine/reference`. These are inspected source/configuration snapshots, not an executed build or verified deployment. They are not copied into this repository; the filenames below identify the inputs without requiring a machine-specific checkout path.

| Reference | Observation and consequence |
| --- | --- |
| `package.json` | Angular `^19.2.5`, Material/CDK `^19.2.8`, CLI `^19.2.6`, TypeScript `~5.8.3`, RxJS 7.8.1, keycloak-angular `~19.0.2`, keycloak-js 25.0.1 and ngx-translate are declared. Material already coexists with Syncfusion; reuse need not introduce a new widget framework. Ranges do not identify installed patch versions. |
| `app.module.ts` | NgModule bootstrap owns Keycloak/user initialization, translations, date adapter, currency and Material defaults. The library must integrate with those providers without overriding host-wide settings. |
| `app-routing.module.ts`, `administration-routing.module.ts`, `administration.module.ts` | APOLO composes feature modules and routes, including GlobalRulesResolver on administration routes. Host route placement, resolver usage and access policy remain APOLO decisions. The root wildcard must not shadow added routes. |
| `apolo-frontend-pom.xml` | Maven installs Node 18.19.1/npm 10.2.4, runs the Angular build and copies `dist` to `target/classes/static` in the frontend JAR. This is supplied build configuration, not a selected future toolchain or a successful compatibility check. |
| `apolo-root-pom.xml`, `apolo-backend-pom.xml` | Spring Boot 3.4.5, Java 17 compiler settings, backend WAR and a runtime dependency on the frontend artifact. The supplied root excerpt omits profiles. Backend directly declares Hibernate 6.6.8.Final through a property and substantial host infrastructure; effective dependencies must be checked before integration. |
| `ProjectController.java` | Controllers delegate to business services. Selected operations use `@auth.hasAuthorization(principal, resource, scope, projectId)` through `@PreAuthorize`. Missing method annotations do not establish absent security; other layers were not supplied. Lists use host paging conventions. |
| `APOLO_Technical_Documentation_v1.0.docx`, sections 7–9 | Contextual permissions, shared application deployment and non-persistent Artemis messaging are documented. Standalone Keycloak remains migration history rather than confirmed production topology. Broker presence alone does not supply durable workflow handoff. |

The current [console package](../../workflow-test-host/frontend/package.json) uses Angular 21.2.x. It remains unchanged. Alignment will require a deliberate audit of Angular/Material APIs, providers, templates, build configuration and tests; changing version strings alone is not an implementation plan.

## Proposed dependency and deployment boundaries

```text
APOLO frontend ──consumes──> workflow-ui (Angular library)
APOLO backend  ──depends───> workflow-web ──depends──> workflow-core
APOLO backend  ──depends───────────────────────────> workflow-core

Sandbox Angular shell ────> workflow-ui
workflow-test-host ────────> workflow-web and workflow-core
```

APOLO declares core directly when its business services use core APIs. Core depends on neither web, UI nor a host. The proposed web module is an ordinary library JAR, with no server bootstrap. The UI package is an Angular build artifact, not a third Java runtime layer or a required Maven module.

Workflow UI is compiled into APOLO's frontend resources; workflow web/core run inside APOLO's backend deployment. Existing Maven frontend packaging can continue to assemble the resources into the application. No separate workflow production service, iframe, microfrontend runtime or additional authentication system is selected.

The source-integration direction remains one authoritative implementation, eventually participating in APOLO's build/release lifecycle. Final locations, Maven/npm coordinates and distribution mechanism remain OPEN. A local built package or private registry can support interim integration; a permanent independent publishing lifecycle is not required.

## Angular 19 alignment and UI responsibilities

Use Angular 19-compatible library compilation and Angular/Material/CDK peer dependencies appropriate to the selected supported versions. Verify against APOLO's installed dependency set before fixing exact versions. Do not bundle a second Angular runtime. Standalone library components can be consumed by an NgModule application; APOLO need not undergo an application-wide standalone migration. See [Angular library guidance](https://angular.dev/tools/libraries/creating-libraries) and [standalone interoperability](https://v17.angular.io/guide/standalone-components).

The proposed library supplies generic definition administration, task metadata/list presentation, instance views, history and graph components. The later designer may become another entry point. Runtime, management and designer entry points are possible boundaries, not finalized package names or an instruction to create all three immediately.

APOLO supplies route prefixes, navigation/menu integration, login/session handling, authorized API access, business-object navigation, booking review material and business-specific forms. Proposed UI adapters distinguish API access from business command submission. A booking page may embed workflow controls and submit through APOLO's booking endpoint; reusable components must not force every completion through a generic endpoint.

Respect host HTTP interceptors, date/currency configuration, localization and theme choices. Specify translation loading/namespacing and component styles during extraction; embedding does not automatically guarantee visual compatibility. Avoid importing APOLO's SharedModule, SecurityService or environment files into the library. Do not register duplicate root authentication/translation services or override host providers globally. Frontend permissions and available actions remain advisory; the backend enforces access and business rules.

Preserve the existing engine wire semantics: exact BIGINT identifiers/versions, exact JSON/decimal representation, bounded reads and frozen command recovery. UI adaptation must not coerce opaque IDs or JSON values through lossy JavaScript numbers or silently replace a saved command with current task state.

## REST adapter responsibilities and activation

`workflow-web` would contain reusable controllers, transport DTO mapping, bounded request validation and workflow-scoped exception handling. Core remains usable directly from Java without Spring MVC dependencies. Use the existing root WorkflowConfiguration marker for core integration; a proposed separate web configuration would explicitly import its controllers. Explicit configuration imports are supported by [Spring](https://docs.spring.io/spring-framework/reference/core/beans/java/composing-configuration-classes.html).

Proposed API groups are runtime queries, definition/catalog administration and optional generic runtime commands. Hosts choose which groups to activate. Group activation is not authorization: every enabled operation still needs trusted actor/access integration. Missing required host adapters must fail configuration rather than fall back to permissive access. Exact configuration classes, URL prefixes, DTOs, adapter signatures and failure responses remain feature specification work. Boot auto-configuration is deferred; adding a dependency should not silently expose endpoints.

APOLO's adapter translates its identity into workflow-owned context and maps workflow resources to authorized business context. A workflow instance ID is not a project ID; optional business correlation is neither membership nor access authority. Historical task access, exact submission/history visibility, receipt replay and paginated list filtering each need explicit policies. Specify authorization-aware query/pagination semantics rather than fetching a generic page and dropping unauthorized rows afterward.

Keep APOLO's `auth` bean, permission names, project associations and Keycloak objects outside core and reusable policy assumptions. A web adapter may integrate with the host's Spring security context, but must not add a competing security chain, login, datasource, transaction manager or global JSON configuration. Error advice must be scoped to workflow controllers. Existing sandbox administrator/reviewer rules are sample policies, not production defaults.

## Business commands and transaction guarantees

The proposed booking execution path is:

```text
Reusable controls in an APOLO business page
  → APOLO booking endpoint/application service
  → host transaction, authorized replay and business validation
  → core execution and explicit registered business actions
  → workflow/business changes, history, receipt and durable handoff commit together
```

Preserve receipt-before-state-validation behavior, the early aggregate claim, actual final managed version and exact immutable submissions. Host validation must protect the approval basis and membership through commit, following the [booking policy](../integrations/apolo/booking-business-analysis.md). No business effect may be duplicated between host orchestration and an explicit service node.

Generic start/completion endpoints are optional and may be enabled only under a specified host contract covering their applicable business rules. Reusing a controller does not authorize bypassing APOLO booking orchestration. Required audit and durable recording must participate in the same transaction; core must not commit independently. Existing non-persistent messaging cannot be the sole durable handoff. The production delivery mechanism remains APOLO integration work.

Core continues owning `db/changelog/workflow/workflow-master.xml`; APOLO runs it through its existing migration lifecycle. Preserve applied identities and paths. Web/UI extraction selects no new schema or migration owner.

## Relationship to existing architecture and F011

The current two-module architecture is still authoritative for implementation. Adopting `workflow-web` would require an explicit amendment to C03/CON-02 and corresponding host-endpoint descriptions, while preserving C04–C10 guarantees. The proposed designer packaging also needs reconciliation with the roadmap's standalone-application wording. This proposal records those changes for review; it does not silently enact them.

Angular 19 is the selected target for future UI alignment, not a claim that the current console has changed. F011 code, fixtures, tasks, dependency versions and acceptance evidence are outside this work. Do not modify or rerun F011 to satisfy this proposal. Coordinate extraction after its active work is complete, retaining its behavior and evidence as the starting baseline.

## Future specification and verification gates

1. Specify Angular 19 alignment against the supplied APOLO reference baseline, selecting exact compatible framework/Material/compiler/tool versions and auditing current console APIs. Check package consumption in an NgModule host, translations, theme/overlays, routing, auth and exact data/recovery behavior.
2. Resolve the proposed module/designer architecture amendments and specify the web/UI public contracts before extraction. Preserve the supported core boundary; use technical-layer-first packages in later Java plans. No feature ID or implementation date is assigned here.
3. Extract one bounded shared slice, such as authorized instance history/graph views and their API, and demonstrate it in the sandbox and APOLO. Library builds alone do not establish host integration.
4. Add management/task execution slices only with defined authorization, business-command, audit and replay contracts. Verify denied operations, historical reads, pagination, shared-transaction rollback, concurrency and durable recovery in the actual host arrangement.

Remaining OPEN items are exact installed frontend/effective backend versions, complete build profiles, API/wire compatibility policy, adapter signatures, theme/translation contracts, production identity topology, shared persistence wiring and durable delivery. These do not prevent documenting the proposal. No additional files are required from the owner for this discussion; request focused evidence when the relevant implementation decision is reached.

## Documentation handover

This record and navigation/status notes document the 2026-11-07 owner request. Only Angular 19 alignment is newly AGREED; module extraction remains PROPOSED. No feature implementation, package installation, build, test, host startup, database access, commit or push is part of this task. Static content, local-link and whitespace checks are the applicable verification; they establish document consistency, not runtime compatibility.

Preparation verification on 2026-11-07: PASS — reviewed the documentation changes and decision-status distinctions; checked 103 local file links across six changed/new documents, including added-file whitespace; `git diff --check` passed. Builds, tests and runtime compatibility checks: NOT RUN (documentation only). No process was started or stopped. F011 feature files and application sources were not changed.
