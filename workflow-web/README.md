# Workflow viewer web adapter

Ordinary Java 17 library JAR; built with JDK 21. It depends on core's public read contracts and supplies only four GET operations. It installs no Boot auto-configuration, security, datasource, migration runner, transaction manager, global codec or advice.

Explicitly `@Import(WorkflowViewerConfiguration.class)` in the host and supply:

- `WorkflowViewerOptions("/your/api", "trusted.correlation.attribute")`; correlation attribute may be null. The base path must be nonempty, non-root literal path segments.
- `WorkflowViewerAccess`, using trusted request identity. `requireInstanceRead` applies to every read; `requireHistoryRead` additionally grants full retained evidence for history summaries/exact events. Throw `WorkflowViewerException` for unauthenticated (401), concealed missing/denied (404) or bounded failure (422). No permissive adapter is provided.
- Core's managed `WorkflowRuntimeQueryService` and `WorkflowDefinitionService`, with the host's existing transaction infrastructure.

`readableLinks(instanceId, candidates)` returns independent activity grants (`taskId=null`) and exact-task grants. Each returned grant must match a supplied candidate's activity and optional task. Missing grants hide links; adapter exceptions fail the read. Graph construction uses internal history summaries after INSTANCE authorization, without disclosing raw evidence or requiring HISTORY.

Routes relative to the configured mount are `/instances/{id}`, `/instances/{id}/graph`, `/instances/{id}/history` and `/instances/{id}/history/{sequence}`. Serialization and error handling are scoped to this controller. Plain configuration/controller classes and a dedicated viewer-only mapping make dependency inclusion or package scanning inert. Only one mount per application is supported.

History defaults to cursor zero and 50 rows, maximum 100. Summary pages preserve complete rows within dual 1 MiB limits. Exact events preserve stored details within dual 4 MiB and final UTF-8 envelope within 8 MiB; they are never truncated. Numeric Java longs serialize as strings. Graph pin, contiguous-prefix watermark and existing size/shape checks remain in the projection service.

The sandbox's gated configuration and `ConsoleViewerAccess` illustrate composition. APOLO integration and production security/dependency acceptance are separate work.
