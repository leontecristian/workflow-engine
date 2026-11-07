# Workflow web adapter

Ordinary Java 17 library JAR; built with JDK 21. It depends on core's public contracts and supplies independently enabled viewer, administration, runtime-read and optional completion-command groups. It installs no Boot auto-configuration, security, datasource, migration runner, transaction manager, global codec or advice.

Explicitly `@Import(WorkflowViewerConfiguration.class)` in the host and supply:

- `WorkflowViewerOptions("/your/api", "trusted.correlation.attribute")`; correlation attribute may be null. The base path must be nonempty, non-root literal path segments.
- `WorkflowViewerAccess`, using trusted request identity. `requireInstanceRead` applies to every read; `requireHistoryRead` additionally grants full retained evidence for history summaries/exact events. Throw `WorkflowViewerException` for unauthenticated (401), concealed missing/denied (404) or bounded failure (422). No permissive adapter is provided.
- Core's managed `WorkflowRuntimeQueryService` and `WorkflowDefinitionService`, with the host's existing transaction infrastructure.

`readableLinks(instanceId, candidates)` returns independent activity grants (`taskId=null`) and exact-task grants. Each returned grant must match a supplied candidate's activity and optional task. Missing grants hide links; adapter exceptions fail the read. Graph construction uses internal history summaries after INSTANCE authorization, without disclosing raw evidence or requiring HISTORY.

Routes relative to the configured mount are `/instances/{id}`, `/instances/{id}/graph`, `/instances/{id}/history` and `/instances/{id}/history/{sequence}`. Serialization and error handling are scoped to this controller. Plain configuration/controller classes and a dedicated viewer-only mapping make dependency inclusion or package scanning inert. Only one mount per application is supported.

History defaults to cursor zero and 50 rows, maximum 100. Summary pages preserve complete rows within dual 1 MiB limits. Exact events preserve stored details within dual 4 MiB and final UTF-8 envelope within 8 MiB; they are never truncated. Numeric Java longs serialize as strings. Graph pin, contiguous-prefix watermark and existing size/shape checks remain in the projection service.

The sandbox's gated configuration and `ConsoleViewerAccess` illustrate composition. APOLO integration and production security/dependency acceptance are separate work.

## Definition and catalog administration

Explicitly import `WorkflowAdministrationConfiguration` and provide `WorkflowAdministrationOptions(basePath, correlationAttribute)` plus `WorkflowAdministrationHost`. The trusted correlation attribute must be nonblank and populated by the host; the sandbox uses `console.correlation`. Viewer activation and package scanning do not enable administration. Missing required adapters fail configuration.

Every administration operation invokes `requireAdministrationAccess()` once before reading or parsing its request. This single grant covers the complete definition/catalog collection. The host supplies authentication and mutation CSRF protection; throw `WorkflowAdministrationException` for 401/403 denial. UI visibility never grants access.

Reads use core's `WorkflowDefinitionService` and `TaskTypeCatalogService`. Typed writes delegate to the host adapter, which must return only after its transaction commits. Preserve trusted actor/time and same-transaction audit behind that adapter; the library installs neither. The sandbox's `ConsoleAdministrationHost` delegates to the existing `ConsoleManagement` transaction proxy.

Routes are the existing `/task-types`, `/definitions` and `/versions` operations listed in [F014](../docs/features/F014-definition-catalog-administration/spec.md). IDs/tokens remain decimal strings; raw document import/replacement retains its 4 MiB UTF-8 limit, ordinary bodies 16 KiB, and exports return stored text. Local handlers retain management error categories and diagnostics, including 403 rather than viewer concealment. No task-type deletion, new authoring semantics or automatic write retries are supplied.

## Runtime reads and optional completion mapping

F015 adds two independent explicit imports: `WorkflowRuntimeReadConfiguration` and `WorkflowRuntimeCommandConfiguration`. Supply `WorkflowRuntimeOptions(basePath, correlationAttribute)` and the corresponding required `WorkflowRuntimeReadHost` / `WorkflowRuntimeCommandHost`. Neither import activates the other; broad package scanning remains inert. The read group additionally uses core query/definition services. The host owns authentication, CSRF, access policy and transaction infrastructure.

Read routes cover monitor, variables, assigned/instance tasks, exact task/contract/submission and activity/submissions, as inventoried in [F015](../docs/features/F015-runtime-task-instance-extraction/spec.md). The host supplies authorized pages and exact-resource access. `requireMonitorAccess()` runs before monitor query parsing. A filtered page may be empty with continuation; consumers must retain it. IDs/tokens remain decimal strings and pinned contracts retain existing fields.

The optional command group maps only completion and completion replay. `complete(CompleteWorkflowTask, correlation)` delegates without a preliminary exact-read check; hosts must preserve receipt-before-current-state validation. `replayComplete` returns an optional original result and must never execute on absence. The host creates trusted context and owns business validation, assignment, transaction, audit, durable handoff and post-rollback receipt lookup. Sandbox start/replay routes and request linking remain host-owned. See the sandbox runtime adapters for the existing coordinator/transaction proxy delegation.
