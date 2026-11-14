# Java package structure

**Agreed convention — 2026-10-25:** technical layers first, with features underneath. This governs new Java code and feature plans. It supersedes F002's earlier `core.catalog` / `core.persistence.catalog` placement and the discussed feature-first `api`/`internal` layout.

## Core layout

Root: `io.github.leontecristian.workflow.core`.

```text
core
├── WorkflowConfiguration
├── configuration
├── service
│   └── tasktype
│       ├── TaskTypeCatalogService
│       ├── TaskTypeValidation
│       └── TaskTypePersistenceErrors
├── dto
│   └── tasktype
│       ├── CreateTaskType
│       ├── UpdateTaskType
│       ├── TaskTypeQuery
│       ├── TaskTypeFilter
│       ├── TaskTypeView
│       ├── TaskTypeChange
│       └── TaskTypePage
├── entity
│   └── tasktype
│       └── TaskTypeEntity
├── repository
│   └── tasktype
│       └── TaskTypeRepository
├── mapper
│   └── tasktype
└── exception
    └── tasktype
        └── TaskTypeCatalogException
```

This is the target structure, not a claim that all packages exist. Create packages only when needed. A dedicated mapper is optional for trivial conversions. Use the same feature name beneath every relevant layer. F002 uses `tasktype`, with a concrete TaskTypeCatalogService.

## Concrete services by default

Use a concrete Spring service directly when there is one implementation and no actual substitution requirement. Do not automatically create a service interface, a matching implementation class or an `impl` package. Public service methods and their documented DTO/error contracts define the supported boundary; an interface is not required to establish it.

Introduce interfaces for a real extension/integration boundary (for example, behavior supplied by APOLO), multiple supported implementations or an explicit substitution need. State that reason in the feature plan. Dependency injection, transaction management or a hypothetical future implementation alone do not justify a mirrored service interface. Framework-required interfaces, such as Spring Data repositories, remain appropriate; this convention does not ban them.

F002 revision 3 consolidated TaskTypeCatalog and JpaTaskTypeCatalog into TaskTypeCatalogService under `service.tasktype`, with package-private helpers beside it and public contract documentation on the concrete service. The former interface and `impl` package were removed. Preserve the operation, validation, transaction and error semantics and Spring proxy compatibility.

Keep the existing root `WorkflowConfiguration` as the host import and scanning marker. Moving it under `configuration` without updating component/entity/repository scanning roots would narrow scanning incorrectly. New configuration classes belong under `configuration`, with a feature subpackage when needed. Changing the root entry point requires an explicit integration change.

## Responsibilities and supported boundary

The following table describes packages within `workflow-core`.

| Package pattern | Responsibility | Supported host dependency? |
| --- | --- | --- |
| `service.<feature>` | Concrete services and private helpers; justified extension interfaces when specified | Documented services/contracts only, not helpers |
| `dto.<feature>` | Immutable commands, queries, returned DTOs and related enums | Yes |
| `entity.<feature>` | JPA mappings and persistence state | No |
| `repository.<feature>` | Database access and persistence queries | No |
| `mapper.<feature>` | Entity/DTO conversion when a separate class is useful | No |
| `exception.<feature>` | Documented workflow-owned errors/categories | Yes |
| `configuration` | Internal wiring | Only the documented root entry point |

Use `dto`, matching APOLO terminology. Keep F002's commands, queries and results together under `dto.tasktype`; do not create parallel top-level `command`, `query`, `model`, `api` or `internal` hierarchies. Larger features may justify subdivisions beneath their layer/feature package; record the assignments in the feature plan first.

Hosts and other features call documented services with DTOs and documented exceptions. They must not depend on repositories, entities, mappers or private implementation helpers, even when Java/framework access requires those types to be public. A concrete service can be the supported entry point while its collaborators remain internal. Package names do not enforce these supported-dependency rules automatically; check them during review.

Core DTOs must not depend on entities or transport/provider types. Repositories must not call services. Cross-feature calls use documented service operations rather than another feature's repositories. Explain any necessary exception in the feature plan before implementation.

Reusable HTTP controllers, transport DTOs/mapping and scoped error handling belong in `workflow-web`, using its existing layer-first `controller.<feature>`, `dto.<feature>`, `service.<feature>` and `exception.<feature>` packages. Documented `configuration` entry points/options and `integration.<feature>` interfaces support explicit host activation; dependency inclusion or package scanning alone must not activate routes. See the [web adapter documentation](../workflow-web/README.md) for activation contracts.

Hosts retain business endpoints, authorization/audit adapter implementations, business orchestration and infrastructure. Apply layer-first grouping in the test host or APOLO where needed. Core remains independent of web and host implementation. Avoid generic `common`, `util` or `manager` packages without a defined responsibility.

## Exact F002 assignments

Destinations are relative to the core root. Revision 3 replaces the interface/implementation pair with the named concrete service; other class names remain unchanged. These assignments are implemented by F002; verification scope is recorded in its feature evidence.

| Type(s) | Destination |
| --- | --- |
| TaskTypeCatalogService (replaces TaskTypeCatalog and JpaTaskTypeCatalog) | `service.tasktype` |
| CreateTaskType, UpdateTaskType, TaskTypeQuery, TaskTypeFilter, TaskTypeView, TaskTypeChange, TaskTypePage | `dto.tasktype` |
| TaskTypeCatalogException and its nested category type | `exception.tasktype` |
| TaskTypeValidation, TaskTypePersistenceErrors | `service.tasktype` (package-private helpers) |
| TaskTypeEntity | `entity.tasktype` |
| TaskTypeRepository | `repository.tasktype` |

Keep helpers package-private where possible. Cross-package visibility adjustments do not make implementation packages supported APIs. Do not retain duplicate implementations or add compatibility facades for preliminary packages without a real consumer and an explicit migration requirement.

## Applying the convention

Feature plans must list exact packages before implementation. Update declarations, imports, examples/Javadoc and fully qualified references together when moving code. Preserve behavior, schema and Liquibase identities.

Verify component/entity/repository discovery proportionately to the actual package movement, reusing applicable evidence under the [focused verification rules](specification-driven-development.md#focused-verification-and-evidence-reuse). Follow [AGENTS.md](../AGENTS.md): isolated unit tests and guarded integration tests against `localhost:1433/apolo_workflow_test` are authorized; other database targets require task-specific authorization. Preserve historical outcomes. Documentation-only changes need content, link and whitespace checks, not builds or application startup; they do not validate relocated code.
