# F029 — Administration host simplification

Revision 6 — 2026-11-16. Status: IMPLEMENTED; design approved by the owner on 2026-11-16. Implementation authorized and delivered on 2026-11-16; scoped evidence is in implementation.md. Owner: workflow maintainer. Constitution: 6.6.1.

## Approved scope and design

Definition/catalog administration currently requires every host to forward each command through `WorkflowAdministrationHost`. Shared web shall own this generic coordination and call the existing core services. Hosts supply only trusted identity/time, administration authorization and transactional audit integration, in addition to their existing infrastructure and HTTP security.

This approved design supersedes F014 revision 3's host mutation delegation and F023 revision 3's additional host command methods for the authorized F029 implementation. Sandbox and shared web now use the replacement contracts. Core operations, HTTP routes/payloads, UI, schema, runtime commands and business-start orchestration are outside the change. CON-01/02/03/08/10/12 remain applicable. ASM-01/02 apply to host dependency and shared transaction compatibility; sandbox verification does not establish APOLO production integration.

## Module responsibilities and dependency rules

The owner approved these boundaries and the contracts below on 2026-11-16. The owner separately authorized implementation on 2026-11-16. Existing constitutional dependency rules continue to apply.

Dependency arrows mean compile-time dependency, except the explicitly labelled HTTP connection:

```text
APOLO backend / workflow-test-host
    +--> workflow-web --> workflow-core
    +------------------> workflow-core

APOLO frontend / sandbox frontend
    +--> workflow-ui --HTTP--> host-approved backend endpoints
```

- **workflow-core:** owns workflow definitions, task types, execution, domain validation and persistence. Concrete core services implement each generic operation once. Core must not depend on web, UI, test-host or APOLO implementation classes.
- **workflow-web:** depends on core and owns HTTP request/response mapping, scoped error handling and reusable web coordination. For this approved administration slice, a shared service calls core directly and coordinates mutation plus host audit within one transaction. It does not implement host authorization policy or business rules.
- **APOLO backend:** depends on core and optionally web. It supplies trusted identity/time, authorization policy, audit persistence, endpoint activation and infrastructure, including datasource, transaction manager and HTTP security. It owns business orchestration, such as updating a booking and starting its workflow together. Production code must not depend on test-host.
- **workflow-test-host:** consumes the same core/web libraries and supplies sandbox policy, audit and infrastructure adapters. Reusable workflow behavior belongs in core or web according to its responsibility; it must not become a sandbox implementation that each production host must reproduce.
- **workflow-ui:** supplies reusable workflow components consumed by the host frontend. It communicates through host-approved HTTP APIs and has no Java-service or persistence dependency. Host navigation, business forms and security remain host-owned.

Host integration interfaces are justified by host-specific information or behavior: identity, permission decisions and audit recording. They must not require the host to forward generic operations already implemented by core, such as `createTaskType()`. The library declares the integration contract and the host implements it; runtime callbacks do not introduce a library dependency on host implementation classes. Public contracts use standard Java or workflow-owned types, not host entities or security-provider objects.

Owning transaction infrastructure does not require the host to own every transactional service method. Shared administration coordination may use the host transaction manager while preserving atomic core mutation and host audit. Actual business orchestration remains in the host. This distinction does not authorize migration of runtime command orchestration as part of F029.

The approved administration flow is: shared controller → shared administration service → core service and host audit adapter. On 2026-11-16 the owner selected three separate interfaces, a host-provided Clock, the simple audit envelope below and `@Transactional` with REQUIRED propagation for consistency. Implementation of these decisions was authorized on 2026-11-16.

## Requirements

- **R01 — Separate identity, authorization and audit contracts.** Replace `WorkflowAdministrationHost` with these required interfaces:
  - `WorkflowAdministrationIdentity.requireActorRef()` returns a stable, nonblank `String` actor reference from trusted authentication context. Unauthenticated requests receive the existing 401 response. Identity grants no permissions and exposes no security-provider objects. An adapter returning an invalid actor, including null or blank, is a host configuration failure: return `500 HOST_FAILURE` before core or audit calls, rather than treating it as unauthenticated or invalid client input.
  - `WorkflowAdministrationAuthorization.requireAdministrationAccess(String actorRef)` checks the same actor and denies with the existing 403 response. One permission covers definition/task-type administration reads and writes. No resource-specific or per-operation grants are introduced.
  - `WorkflowAdministrationAuditRecorder.record(WorkflowAdministrationAudit audit)` synchronously persists evidence supplied by the shared service in the mutation transaction and throws on failure. The shared service owns change detection and constructs evidence from core results; hosts own audit storage.

  A host may implement all three interfaces in one adapter class, but they remain separate dependencies. No permissive authorization default or silent no-op audit recorder is supplied. Audit contains operation, entity kind/ID, before/after summaries, captured actor/time and trusted correlation. These interfaces contain no generic workflow command forwarding methods.
- **R01a — Host-provided time.** Inject a required `java.time.Clock` separately from identity. Shared mutation coordination captures time once inside the transaction, truncates it to 100-nanosecond precision (without rounding), and constructs the existing `DefinitionManagementContext(actorRef, occurredAt)` using the already authenticated/authorized actor. Definition operations and audit use that same actor/time. Task-type core operations retain their existing context-free signatures; their captured actor/time is supplied to audit only. The audit adapter must not independently obtain identity or time. No fallback system Clock is installed by the library; request fields never supply trusted actor/time.
- **R01b — Trusted correlation.** Mutations require a nonblank String correlation from the configured trusted request attribute. Missing, non-string or blank correlation produces `500 HOST_FAILURE` before calling core, including for commands that might otherwise be no-ops. Do not fall back to a client header or generate a replacement. Preserve the trusted value; UUID formatting remains a sandbox-specific requirement. Read correlation behavior is unchanged.
- **R02 — Shared execution.** A concrete web administration service calls `WorkflowDefinitionService` and `TaskTypeCatalogService` directly using their existing DTOs. Existing controllers retain authorization once before parsing, existing bounded request mapping, response/error shapes and exact tokens/documents. Reads retain their direct core calls.

  The service is internal coordination for shared web controllers, not a new general host command API. Its Java-public methods support controller calls and Spring proxying; they accept an actor already obtained and authorized at the trusted web boundary. Do not duplicate authorization inside the coordinator or add an authorized-actor wrapper. Hosts performing their own business orchestration continue to use core services under their own trusted authorization and transaction boundary.
- **R03 — Atomic mutation/audit.** Public mutation methods on the concrete shared administration service use `@Transactional(propagation = Propagation.REQUIRED)` with the host-provided `PlatformTransactionManager`. They start a transaction when none exists or join the existing transaction. Core mutation and required synchronous audit participate in that same transaction. Audit failures propagate as unchecked exceptions so Spring rolls back a service-owned transaction or marks a joined transaction rollback-only. Do not swallow failures, add automatic retries, or use independent/asynchronous audit recording.
- **R03a — Host transaction configuration.** The host enables annotation-driven transaction management through its existing Spring configuration (for example, `@EnableTransactionManagement` or equivalent Boot configuration) and supplies the transaction manager governing both core persistence and audit resources. Hosts with multiple managers must configure unambiguous selection of the same applicable manager for these operations. Shared web creates no transaction manager, enables no global transaction configuration and uses no TransactionTemplate. Controllers and direct callers invoke the Spring-managed service proxy; manually constructed instances and self-invocation must not be relied on to establish a transaction. Host defaults govern isolation and timeout.

  Explicit administration configuration declares a required transaction-manager dependency so a missing manager fails activation. This dependency does not itself prove interception is enabled or select a different manager for annotations. Effective annotation interception is a host integration prerequisite verified by focused tests; no custom proxy-inspection framework or automatic transaction-configuration repair is required.
- **R03b — Commit ownership.** When the service starts the transaction, its proxy completes commit before returning success to the controller; commit failures propagate. When it joins an outer host transaction, the outer caller owns commit/rollback. A successful service return in that case is provisional and does not establish durable success. The normal shared HTTP flow calls the service without an enclosing transaction; a host adding an outer transaction must complete it before reporting committed success. REQUIRED must not be replaced with REQUIRES_NEW to force an early commit.
- **R04 — Preserve evidence.** Audit only changed results, including changes returned by validation/publication. Preserve operation names and entity kinds from F014/F023. Deletion records the removed summary as before and null after. No-op writes create no audit. Sandbox retains its schemaVersion 1 envelope, 512 KiB UTF-16 bound, UUID correlation, actor/time and failure injection.
- **R05 — Explicit migration.** Administration activation requires all three host interfaces, the host Clock, existing host transaction manager and effective host-enabled transaction interception under R03a. Replace the sandbox command wrappers with implementations of these contracts; do not keep a second command implementation. Document this Java integration break and the transaction configuration prerequisites. Other independently activated web groups remain unchanged.

## Audit envelope — selected design

Use one record in `io.github.leontecristian.workflow.web.dto.administration`:

```java
public record WorkflowAdministrationAudit(
    String operation,
    String entityKind,
    long entityId,
    DefinitionManagementContext context,
    String correlationId,
    Object before,
    Object after
) {}
```

`operation` and `entityKind` retain existing audit strings, including `CREATE_TASK_TYPE`, `DELETE_DRAFT`, `TASK_TYPE`, `DEFINITION` and `DEFINITION_VERSION`. `entityId` identifies the affected entity, including the removed entity for deletion. `context` is the existing core `DefinitionManagementContext`, carrying the trusted actor and captured time from R01a. `correlationId` is the trusted host correlation string; the sandbox's UUID conversion remains a host storage concern rather than a universal UUID type requirement.

The shared service constructs `before` and `after` from existing core summary DTOs: `TaskTypeView` for task types, `DefinitionView` for definition metadata and `DefinitionVersionSummary` for versions. Creation has null before; deletion has null after; updates retain the before/after summaries supplied by core. No raw definition documents, persistence entities or arbitrary host objects are supplied despite the fields' `Object` type.

No new enums, sealed snapshot wrappers or validation framework are introduced. Supported payload types are documented by this contract and constructed by shared code. There is no `changed` field because only changed results produce audit records. Hosts own serialization, storage schema/version and audit-row IDs; the sandbox retains its existing evidence JSON shape and limits under R04. The envelope does not define a new HTTP payload or storage schema.

## Request sequence

1. Obtain the trusted actor once and authorize that actor once before parsing the request.
2. Parse and validate the HTTP request using existing transport rules; require valid trusted mutation correlation before invoking core.
3. For mutations, invoke the Spring-managed service proxy, starting or joining a transaction with REQUIRED propagation; capture Clock time and construct the management context inside it.
4. Call core, then record audit only for changed state using the same captured actor/time and trusted correlation.
5. In the normal HTTP flow, the service proxy commits its transaction before returning to the controller. If a host outer transaction exists, its caller owns completion and must not treat service return as committed success (R03b). Reads continue directly to core after identity and authorization checks.

The coordinator accepts only the actor already obtained and authorized by shared web; an arbitrary client actor string is not authentication. It is not an additional supported command entry point for hosts (R02).

## Acceptance scenarios

- **A01:** An authorized administration request invokes the expected core command with the exact loaded token/raw document. Definition operations and audit receive the same authorized actor and single Clock timestamp truncated to 100-nanosecond precision. Task-type core signatures remain unchanged and their actor/time is supplied to audit only. The host implements no command forwarding.
- **A02:** Unauthenticated/forbidden requests and failed CSRF checks never reach mutation/audit. Authorization still precedes body parsing.
- **A02a:** Invalid identity adapter results and missing/non-string/blank trusted mutation correlation return `500 HOST_FAILURE` without core/audit calls; unauthenticated identity remains 401. No client-header or generated correlation fallback is used.
- **A03:** Changed create/update/version commands emit the existing audit evidence; unchanged results emit none; deletion has null after.
- **A04:** A real audit insertion failure rolls back draft creation/allocation and deletion. A successful task-type HTTP write commits its host audit with trusted actor/correlation.
- **A05:** Explicit activation fails without any required identity, authorization or audit interface, Clock or transaction manager; dependency/scanning/viewer-only activation remains inert. Existing HTTP/error contracts are retained.
- **A06:** With host-enabled transaction interception, a normal proxy call commits mutation and audit together. Inside an outer transaction, service return does not commit either; outer rollback removes both, and audit failure marks the joined transaction rollback-only. Verify effective interception through the managed service, rather than treating the annotation's presence as evidence of transaction behavior.
