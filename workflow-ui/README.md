# Workflow UI library

Follow the [frontend style guide](../docs/frontend-style-guide.md) for the agreed Material 3, icon, layout and visual-review conventions. This README describes the library's consumer setup and contracts.

`@workflow-engine/ui` is an Angular 19.2 partial-compiled library. It owns read-only instance metadata, pinned graphs, ordered history, exact events, definition/catalog administration, runtime inspection and optional task/recovery components. Hosts control application bootstrap, authentication, HTTP configuration, routes, translation and theme. Angular (including Forms), Router, Material, CDK, RxJS and jsonc-parser are peers.

The built components include their layout and presentation, including workflow-owned dialog content. Import the components normally and provide your supported Angular Material theme and the icon setup below. Component styles use host typography and semantic Material colors where available, with readable fallbacks. Hosts own theme, global typography, density, shell, tabs and navigation. An existing host such as APOLO needs no new CSS configuration to adopt these component styles. The sandbox stylesheet is specific to the sandbox application.

Icons use direct native `<mat-icon>refresh</mat-icon>` markup and Google Material Symbols names, including page metadata/collection icon inputs. The former `ConsoleIcon` wrapper, semantic aliases and custom SVG registry are removed. The library packages an opt-in local Material Symbols Outlined font stylesheet, selected at weight 400, fill 0, grade 0 and optical size 24; no external runtime font request is needed. Graph visualizations remain separate.

Each host must add `node_modules/@workflow-engine/ui/styles/material-symbols.css` to its Angular build `styles` list and configure the default icon font once before views render, for example in its own environment initializer:

```typescript
inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined', 'mat-ligature-font');
```

The library does not change a host's global icon defaults. Both sandbox and NgModule fixture show the host-owned initializer/build wiring; APOLO must integrate it into its own setup and account for its other icon consumers. The stylesheet resolves the packaged relative font automatically through Angular's CSS bundling. The local 6,656-byte fixed-axis font is an official Google Fonts subset for the 23 names listed in `material-symbols.NOTICE.txt`; adding names requires regenerating that subset through the documented official CSS API. License/provenance assets are included. No direct Platform Browser peer is needed by the library after SVG sanitization is removed.

From the repository root, select Node 22.22.0/npm 10.9.4, then:

```powershell
cd workflow-ui
npm ci
npm run build
npm run test:unit
cd ../workflow-test-host/frontend
npm ci
npm start
```

Both consumers use `install-links=true` and a copied `file:` dependency on `workflow-ui/dist/workflow-ui`. Build the library before installing a consumer. After changing library sources, rebuild it and run consumer `npm ci` again. There are no TypeScript aliases into source or runtime symlinks. The Maven `console-ui` profile performs these steps before the sandbox production build.

For routine sandbox development after this initial setup, `npm run start:console` in `workflow-test-host/frontend` builds the library, refreshes its installed package copy, clears Angular's cache and starts the dev server without reinstalling dependencies. IntelliJ's **Workflow console frontend** configuration uses this command. Restart that frontend after shared library edits to rebuild and load them. The backend's **Workflow console (local)** configuration compiles Java only. Production packaging remains manual.

The isolated tests exercise methods/signals with real Angular DI and doubles for HTTP/DOM; they do not replace browser observations. The verification NgModule host has its own lockfile:

```powershell
cd workflow-ui/verification/ngmodule-host
npm ci
npm run build
npm run test:http
npm start
```

Import standalone components directly or include them in an NgModule's `imports`:

```typescript
import {
  WorkflowInstanceViewerComponent, WorkflowHistoryComponent,
  WorkflowHistoryEventComponent, WorkflowInstanceGraphComponent,
  WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION
} from '@workflow-engine/ui';
```

The three request-owning components accept `instanceId: string`, `sessionScope: unknown | null` and `messages: WorkflowViewerMessages`. The event component also accepts `sequence: string`. Give every new login/session an opaque new scope value, including same-user re-authentication. Null invalidates pending reads and clears content. `messages` defaults to `DEFAULT_VIEWER_MESSAGES`; replace the object on locale changes. The graph accepts `graph: InstanceGraph` and messages and makes no requests.

Supply `WORKFLOW_VIEWER_READS` with `WorkflowViewerReads`: `instance(id, signal)`, `graph(id, signal)`, `history(id, cursor, limit, signal)` and `historyEvent(id, eventRef, signal)` return typed promises. Retained evidence reads use the existing decimal sequence strings. Hosts that expose safe business history can return `BusinessHistoryPage` and `BusinessHistoryEvent`; pass their event references and continuations through unchanged. Honor AbortSignal, and normalize failures with `ViewerReadError`/`normalizeViewerReadError`. Components also guard late results and validate response shapes; cancellation alone is insufficient. There is no automatic retry or persistent response cache.

Optionally supply `WORKFLOW_VIEWER_NAVIGATION` with `destination(intent): UrlTree | null`. Intents are instance/history with instanceId, event with instanceId/sequence, or task/activity with id. Null renders noninteractive content. Hosts independently authorize destination pages; a navigation link is never a permission grant. Instance viewers load metadata then graph; history/event components load only when mounted. Graph failure preserves independently authorized metadata; authentication or concealed access denial clears the composite.

Exact event downloads use the original `detailsJson` string. Derived routing presentation does not replace retained evidence. Render labels and details as text.

The definition graph/fragment editor is part of the shared administration implementation. The exported pure `layoutGraph` function/layout types and `@workflow-engine/ui/styles/graph.scss` Sass asset remain available.

## Optional workflow-web HTTP adapters

Register the capabilities your host uses. Each provider injects the host's existing Angular `HttpClient`, including its interceptors and backend. Keep the host's current HTTP configuration. The mount includes any context prefix and must match the corresponding [workflow-web endpoint group](../workflow-web/README.md).

For Definitions and Task types at `/api/workflow`, add this provider to your existing application configuration.

```typescript
import { provideWorkflowAdministrationHttp } from '@workflow-engine/ui/http';

export const workflowAdministrationProviders = [
  provideWorkflowAdministrationHttp('/api/workflow'),
];
```

Spread `workflowAdministrationProviders` into the standalone application's or NgModule's `providers` array. Import `WorkflowDefinitionsComponent` from `@workflow-engine/ui/administration` and mount `<workflow-definitions [sessionScope]="sessionScope" />` with the host's current session scope. The page requests `GET /api/workflow/definitions` with bounded descending pagination. The backend needs `WorkflowAdministrationConfiguration` with that mount and a host administration grant.

For the other groups, select the providers you need.

```typescript
import {
  provideWorkflowViewerHttp,
  provideWorkflowRuntimeReadsHttp,
  provideWorkflowRuntimeCommandsHttp,
  provideWorkflowCancellationHttp,
  provideWorkflowTaskReassignmentHttp,
} from '@workflow-engine/ui/http';

export const workflowRuntimeProviders = [
  provideWorkflowViewerHttp('/api/workflow'),
  provideWorkflowRuntimeReadsHttp('/api/workflow'),
  provideWorkflowRuntimeCommandsHttp('/api/workflow'),
  provideWorkflowCancellationHttp('/api/workflow'),
  provideWorkflowTaskReassignmentHttp('/api/workflow'),
];
```

- Viewer reads require `WorkflowViewerConfiguration`. Runtime inspection, My tasks and task administration require `WorkflowRuntimeReadConfiguration`.
- Task completion, ownership and their receipt lookups require `WorkflowRuntimeCommandConfiguration` and the host's supported operation policies. Cancellation and reassignment also use runtime reads for availability and target lookup. With separate mounts, pass the read mount first and the command mount second to either provider.
- For read-only runtime pages, register only the viewer and runtime read providers. Register command providers at application scope when using the root-scoped `CommandRecovery`. The server authorizes every request independently.

Custom providers can coexist with these bindings. Register one implementation per token. In your provider factory, use `createWorkflowViewerHttp(inject(HttpClient), basePath)` to build the standard viewer methods and replace the history methods with business-history reads. Use `createWorkflowRuntimeCommandsHttp(inject(HttpClient), basePath)` to supply standard task commands alongside a host-defined `start` adapter.

The host owns authentication, CSRF, credentials, routing, navigation and session lifetime. Give the pages a new opaque scope for each session, including same-user sign-in, and use null on logout. If the host handles a 401 by invalidating the session, associate the request with its original scope so a late response cannot clear a replacement session. Business starts, business-history policy, task editors and diagnostics stay with the host. The adapters preserve exact command input, support abortable requests and leave uncertain mutations available for inspection or explicit recovery.

## Administration components

Import `WorkflowCatalogComponent`, `WorkflowDefinitionsComponent`, `WorkflowDefinitionFamilyComponent` and `WorkflowDefinitionVersionComponent` from `@workflow-engine/ui/administration`. API tokens, models, messages and shared helpers remain exported from `@workflow-engine/ui`. The sandbox lazy-loads its four administration wrappers so these pages do not enter the initial bundle. Provide `WORKFLOW_ADMINISTRATION_API` with the typed `WorkflowAdministrationApi` host transport, including raw document writes and text export. Every operation receives an `AbortSignal`; cancellation does not mean a mutation rolled back. Preserve HTTP status, code, diagnostics and correlation on errors, and use uncertain-write wording without automatic retry advice.

All components accept `sessionScope` (nonnull opaque login generation; null clears protected content) and partial `WorkflowAdministrationMessages`. Family/version components additionally take `definitionId`/`versionId`. The catalog's `initialCode` defaults to empty; sample defaults belong to hosts. Supply the existing `WORKFLOW_VIEWER_NAVIGATION` adapter for definition/version destination intents. The sandbox wrappers supply session, routes and `SANDBOX_REVIEW`; its transport owns CSRF and credentials.

F023 adds two required `WorkflowAdministrationApi` methods; custom providers must implement `createDraftFromVersion(id, token, signal): Promise<DefinitionChange>` and `deleteDraft(id, token, signal): Promise<DefinitionDeletion>`. Creation sends POST `/versions/{id}/drafts` with `{}`; deletion sends DELETE `/versions/{id}` without a body. Both pass the loaded token in `X-Expected-Lock-Version` and use host credentials/CSRF. The deletion result contains `before`, `operation: 'DELETE_DRAFT'` and `context`, with no surviving `after` or replacement token. Keep unknown transport outcomes distinguishable from authoritative HTTP rejection (preserve `httpStatus`); never retry these commands automatically.

The version page copies saved content only, so authors must save or explicitly discard edits before creation. Delete confirmation permits dirty drafts and warns about permanent content deletion and reserved numbering. Uncertain outcomes retain editor text and block repeat submissions until explicit refresh; creation directs authors to inspect the family version list first. Confirmed deletion clears obsolete content. Successful commands retain identity and a host-mapped draft/family link if routing fails. Supply both navigation destinations and a Router to enable automatic navigation; hosts remain responsible for their destination authorization.

The existing dirty-editor feedback, explicit save/lifecycle actions, saved graph, fragment application and Material reload confirmation are retained. Only explicit discard reloads dirty text. Late responses are ignored after input/session changes or destruction; failed writes retain input/token, and a successful write followed by failed refresh has separate feedback. Administration components install no general route-leave guard or command-recovery behavior.

The public exact-text formatting functions and shared presentation helpers also serve the sandbox's remaining host pages. Their names retain the original `Console*` names for those consumers; they contain no sandbox policy or transport.

## Runtime components and recovery

Import runtime components lazily from `@workflow-engine/ui/runtime`: `WorkflowInstancesComponent`, `WorkflowAssignedTasksComponent`, `WorkflowInstanceTasksComponent`, `WorkflowTaskComponent`, `WorkflowActivityComponent`, `WorkflowSubmissionsComponent`, `WorkflowVariablesComponent` and `WorkflowCommandsComponent`. Root exports provide runtime models, tokens, messages, `CommandBuffer` and `CommandRecovery`. Supply `WORKFLOW_RUNTIME_READS` with typed host-authorized methods and cancellation. Components accept opaque `sessionScope`, partial runtime messages, and `id` where applicable; use a new scope object for every login, including same-user reauthentication. Null clears private view state.

Task inspection needs no command provider. To enable completion controls, provide `WORKFLOW_RUNTIME_COMMANDS` with `complete(envelope, signal)` and replay-only `replayComplete(envelope, signal)`. Host transport may use business endpoints and owns credentials/CSRF. Shared controls validate the pinned choice/comment and freeze the UUID, IDs, comment and expected token before dispatch. No command is automatically retried. The exact-task contract supplies the display label and current `canComplete` hint. The host must still authorize completion.

For My Tasks, custom providers implement `assignedTasks(filters: TaskAdministrationFilters, cursor, limit, signal)` and return `TaskPage<AssignedTaskRow>`. Include the ISO-8601 `createdAt` string and exact decimal-string ID on each row. Apply the filters with current personal task-read authorization and return the page in creation-time descending, numeric-ID descending order. Pass opaque continuation tokens through unchanged, including when a page has no visible rows. The standard HTTP provider uses the F039 runtime-read endpoint. Keep the host navigation mapping for the task detail intent to open task names from the inbox.

When the host renders the My Tasks title in its own shell, set `[externalPageHeader]="true"` on `workflow-assigned-tasks`. The visible count stays in the inbox toolbar on the Refresh row.

### Optional task completion forms

The shared task page uses its generic form when the host omits the editor provider or the pinned `TaskContract.taskType` has no exact match. To customize a type, register a standalone Angular component with the page's injector. Pass all of that injector's registrations in one call.

```typescript
import { provideWorkflowTaskEditors } from '@workflow-engine/ui/runtime';
import { BookingReviewComponent } from './booking-review';

// Add to the host's application or route providers.
provideWorkflowTaskEditors({ BOOKING_REVIEW: BookingReviewComponent });
```

The component implements `WorkflowTaskHandlerInputs` from the runtime entry point and declares three Angular inputs named `contract`, `editable` and `submitDecision`. It owns its result controls, workflow comment and outcome buttons. Read the `WorkflowEditableTaskContract` for outcome selectors, comment requirements and result mode. The page renders the captured business context once, so an editor can read frozen inputs for its logic without rendering a second copy. Disable editing and submission when `editable` is false. On action, pass a `WorkflowTaskDecision` snapshot with `outcomeKey`, `comment` and optional exact `resultJson`. For a definitive rejection the callback may return `WorkflowTaskRejection`, including any field errors as paths and messages.

The [sandbox booking component](../workflow-test-host/frontend/src/app/pages/tasks/booking-review.ts) demonstrates a reviewed-room result and pinned Approve and Reject actions. The generic form consumes the authorized `resultContract.form` projection, including exact values encoded as text where needed. See the [form walkthrough](../README.md#try-the-optional-task-completion-forms).

The contract carries a task summary from the same server snapshot as its opaque precondition. The task-specific command body contains `commandId`, `taskId` and operation fields. Preserve `expectedToken` exactly through buffering and recovery. Send it as `X-Task-Precondition` on mutation and replay. Use `TaskCommandResult` and `TaskCommandReplay` for task command outcomes and authorized continuation. Hosts can present technical inspection alongside the shared Task page using their own administrator-protected HTTP route. The sandbox loads its inspection disclosure on expansion. On the wire, shared result forms can reference repeated safe descriptors by ID. Normalize them with `hydrateTaskForm` in the transport. Task collections pass opaque continuations through unchanged.

`CommandRecovery` retains frozen requests and observations across route destruction in memory. The host must call `setScope` whenever its session changes, even when no runtime component is mounted; the sandbox's `ConsoleRuntimeSessionBridge` demonstrates synchronous session invalidation. Shared components also supply their input scope. Clear/log-out/session replacement removes private entries; no browser persistence is installed. Retry submits original values; Find result only looks up a receipt. Original results and separately refreshed current work are labelled independently, and refresh failures retain committed evidence.

Optional `WorkflowRuntimeCommands.start` supplies `freezeBody`, `execute` and `replay` for the host's existing start envelopes. Shared code knows no sandbox request fields. Without this adapter, start restore fails before insertion; completion remains available. Exported envelopes retain `kind`, `body` and `expectedToken`, including raw start-input text. Start forms, validation, exact serialization and business linking remain host-owned.

Navigation adds `commands`, `instanceTasks` and `submissions` intents to the existing host adapter; hosts may return null. Runtime recovery also uses `WORKFLOW_VIEWER_READS` for separate current-instance reads. The existing NgModule fixture demonstrates alternate transport, headings/navigation and a task consumer with no command provider.

The shared task component displays F022 previous decisions for enabled pinned contracts, including completed tasks and hosts without a command provider. Implement `WorkflowRuntimeReads.previousDecisions(id, cursor, limit, signal)` with authorized receiving-task reads; pass the opaque continuation unchanged. The section has its own loading/error/retry state and appends whole pages without gating task completion. Comment and attribution values render as text; source identifiers do not create navigation links. Task/session changes clear the section and abort/ignore stale reads. The NgModule fixture accepts `?previousDecisionEvidence=completed`, `selected`, or `none` for focused rendering checks.

The instance viewer optionally accepts `WORKFLOW_CANCELLATION` implementing `WorkflowCancellation`. Its `availability(id, signal)` reads `/instances/{id}/cancellation-availability`; `cancel(envelope, signal)` sends `/instances/{id}/cancel` and `replay(envelope, signal)` sends lookup-only `/commands/cancel/replay`. Omit this provider for view-only consumers; `WORKFLOW_RUNTIME_COMMANDS` is not required for cancellation. Availability is advisory: show the action only for an ACTIVE instance with `allowed:true` and the same decimal-string lock version. Hosts still authorize every command and recovery request.

The cancellation envelope has `kind:'cancel'`, frozen `body:{commandId,instanceId,reason}` and `expectedToken`. Send only `{commandId,reason}` for mutation, all body fields for replay, and the token in `X-Expected-Lock-Version`. Preserve exact reason text, canonical IDs/UUID and trusted host credentials/CSRF. Cancellation results contain instance/activity and `cancelledTaskCount`, with no representative task. Errors must retain `httpStatus` to distinguish rejection from unknown outcomes.

The Material confirmation requires a nonblank reason of at most 2000 UTF-16 units, explaining retained decisions/business effects. Pending commands prevent duplicate submission; there is no automatic retry. Unknown outcomes remain in the session buffer, with explicit lookup-only recovery and, after a miss, explicit retry of the original envelope. A miss or failed lookup cannot authorize a replacement. Successful execution/recovery refreshes instance, graph and availability. Cancellation reason renders as text beside closure actor/time; stopped graph nodes remain CANCELLED and tasks distinguish instance cancellation from resolved-review cancellation. The existing host session bridge must invalidate `CommandRecovery` on every session replacement even off-route. The database-free NgModule fixture enables cancellation with `?cancellationEvidence=true`; omitting it preserves the view-only configuration.

### Task administration and reassignment

Import `WorkflowTaskAdministrationComponent` from `@workflow-engine/ui/runtime` and supply the optional `WorkflowRuntimeReads.administrationTasks(filters,cursor,limit,signal)` method. The host grants administration access and filters each returned task independently. The component defaults to OPEN, supports case-insensitive literal task-name substring filtering plus exact status/type/target/assignee/instance filters and advances through empty pages when continuation is present. Filters reset the cursor; closed tasks link to inspection-only details. Hosts own the route and navigation entry; this does not change My tasks.

To enable the existing task detail's Reassign action, provide optional `WORKFLOW_TASK_REASSIGNMENT` implementing `WorkflowTaskReassignment`. It supplies exact-task `availability` returning `{allowed,precondition?}`, bounded `targets(taskId,kind,query,cursor,limit,signal)` returning `Promise<WorkflowReassignmentTargetPage>`, and `reassign(envelope,replay,signal)`. Import `WorkflowReassignmentTarget` (`{ref,label}`) and `WorkflowReassignmentTargetPage` (`{items: WorkflowReassignmentTarget[], nextCursor: string | null}`) from `@workflow-engine/ui`. The cursor is opaque and host-owned: pass it back unchanged, use null to end continuation, and do not infer totals or page numbers. The page freezes the precondition from its displayed task snapshot. Omit the token for read-only consumers; the ordinary command provider is independent. Directory failures remain errors, and hosts revalidate permission and destination during execution.

Reassignment uses the task command and replay result wrappers. The frozen session buffer stores `kind:'reassign'` with `body:{commandId,taskId,assignmentKind,assignmentRef,reason}` and `expectedToken`. Host transport maps the two assignment fields into `assignment:{kind,ref}`, takes the task ID from the mutation route, sends it in lookup-only replay bodies, and adds `X-Task-Precondition` to both requests. Preserve exact reason/identity text and credentials/CSRF. There is no automatic mutation retry; unknown outcomes block replacement and preserve the original envelope through lookup misses. Session changes must synchronously invalidate `CommandRecovery` even off-route.

The lazy Material dialog requires a host-selected USER/GROUP/ROLE destination and a nonblank reason of at most 1000 UTF-16 units. It warns that ownership is replaced while review data remains unchanged. Successful commands refresh details and action availability; returning to the administration route loads current rows. Existing administrative release remains separate. The database-free NgModule consumer enables a focused administration/dialog fixture with `?reassignmentEvidence=true`; adding `&taskInputEvidence=completed` demonstrates closed details retaining inputs without mutation controls.
