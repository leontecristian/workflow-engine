# Workflow UI library

Follow the [frontend style guide](../docs/frontend-style-guide.md) for the agreed Material 3, icon, layout and visual-review conventions. This README describes the library's consumer setup and contracts.

`@workflow-engine/ui` is an Angular 19.2 partial-compiled library. It owns read-only instance metadata, pinned graphs, ordered history, exact events, definition/catalog administration, runtime inspection and optional task/recovery components. It has no application bootstrap, authentication, HTTP providers, routes, translation service or global theme. Angular (including Forms), Router, Material, CDK, RxJS and jsonc-parser are peers.

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

Supply `WORKFLOW_VIEWER_READS` with `WorkflowViewerReads`: `instance(id, signal)`, `graph(id, signal)`, `history(id, afterSequence, limit, signal)` and `historyEvent(id, sequence, signal)` return typed promises. All IDs/cursors remain decimal strings. Honor AbortSignal, and normalize failures with `ViewerReadError`/`normalizeViewerReadError`. Components also guard late results and validate response shapes; cancellation alone is insufficient. There is no automatic retry or persistent response cache.

Optionally supply `WORKFLOW_VIEWER_NAVIGATION` with `destination(intent): UrlTree | null`. Intents are instance/history with instanceId, event with instanceId/sequence, or task/activity with id. Null renders noninteractive content. Hosts independently authorize destination pages; a navigation link is never a permission grant. Instance viewers load metadata then graph; history/event components load only when mounted. Graph failure preserves independently authorized metadata; authentication or concealed access denial clears the composite.

Exact event downloads use the original `detailsJson` string. Derived routing presentation does not replace retained evidence. Render labels and details as text.

The definition graph/fragment editor is part of the shared administration implementation. The exported pure `layoutGraph` function/layout types and `@workflow-engine/ui/styles/graph.scss` Sass asset remain available.

## Administration components

Import `WorkflowCatalogComponent`, `WorkflowDefinitionsComponent`, `WorkflowDefinitionFamilyComponent` and `WorkflowDefinitionVersionComponent` from `@workflow-engine/ui/administration`. API tokens, models, messages and shared helpers remain exported from `@workflow-engine/ui`. The sandbox lazy-loads its four administration wrappers so these pages do not enter the initial bundle. Provide `WORKFLOW_ADMINISTRATION_API` with the typed `WorkflowAdministrationApi` host transport, including raw document writes and text export. Every operation receives an `AbortSignal`; cancellation does not mean a mutation rolled back. Preserve HTTP status, code, diagnostics and correlation on errors, and use uncertain-write wording without automatic retry advice.

All components accept `sessionScope` (nonnull opaque login generation; null clears protected content) and partial `WorkflowAdministrationMessages`. Family/version components additionally take `definitionId`/`versionId`. The catalog's `initialCode` defaults to empty; sample defaults belong to hosts. Supply the existing `WORKFLOW_VIEWER_NAVIGATION` adapter for definition/version destination intents. The sandbox wrappers supply session, routes and `SANDBOX_REVIEW`; its transport owns CSRF and credentials.

F023 adds two required `WorkflowAdministrationApi` methods; custom providers must implement `createDraftFromVersion(id, token, signal): Promise<DefinitionChange>` and `deleteDraft(id, token, signal): Promise<DefinitionDeletion>`. Creation sends POST `/versions/{id}/drafts` with `{}`; deletion sends DELETE `/versions/{id}` without a body. Both pass the loaded token in `X-Expected-Lock-Version` and use host credentials/CSRF. The deletion result contains `before`, `operation: 'DELETE_DRAFT'` and `context`, with no surviving `after` or replacement token. Keep unknown transport outcomes distinguishable from authoritative HTTP rejection (preserve `httpStatus`); never retry these commands automatically.

The version page copies saved content only, so authors must save or explicitly discard edits before creation. Delete confirmation permits dirty drafts and warns about permanent content deletion and reserved numbering. Uncertain outcomes retain editor text and block repeat submissions until explicit refresh; creation directs authors to inspect the family version list first. Confirmed deletion clears obsolete content. Successful commands retain identity and a host-mapped draft/family link if routing fails. Supply both navigation destinations and a Router to enable automatic navigation; hosts remain responsible for their destination authorization.

The existing dirty-editor feedback, explicit save/lifecycle actions, saved graph, fragment application and Material reload confirmation are retained. Only explicit discard reloads dirty text. Late responses are ignored after input/session changes or destruction; failed writes retain input/token, and a successful write followed by failed refresh has separate feedback. Administration components install no general route-leave guard or command-recovery behavior.

The public exact-text formatting functions and shared presentation helpers also serve the sandbox's remaining host pages. Their names retain the original `Console*` names for those consumers; they contain no sandbox policy or transport.

## Runtime components and recovery

Import runtime components lazily from `@workflow-engine/ui/runtime`: `WorkflowInstancesComponent`, `WorkflowAssignedTasksComponent`, `WorkflowInstanceTasksComponent`, `WorkflowTaskComponent`, `WorkflowActivityComponent`, `WorkflowSubmissionsComponent`, `WorkflowVariablesComponent` and `WorkflowCommandsComponent`. Root exports provide runtime models, tokens, messages, `CommandBuffer` and `CommandRecovery`. Supply `WORKFLOW_RUNTIME_READS` with typed host-authorized methods and cancellation. Components accept opaque `sessionScope`, partial runtime messages, and `id` where applicable; use a new scope object for every login, including same-user reauthentication. Null clears private view state.

Task inspection needs no command provider. To enable completion controls, provide `WORKFLOW_RUNTIME_COMMANDS` with `complete(envelope, signal)` and replay-only `replayComplete(envelope, signal)`. Host transport may use business endpoints and owns credentials/CSRF. Shared controls validate the pinned choice/comment and freeze the UUID, IDs, comment and expected token before dispatch. No command is automatically retried. Task views expose typed assignment targets, while the exact-task contract supplies the display label and current `canComplete` hint; the host must still authorize completion. `diagnosticsEnabled` defaults to false: normal completion refreshes the task and confirms success, while uncertain commands stay frozen. Set it to true only for diagnostic recovery links and raw command details.

`CommandRecovery` retains frozen requests and observations across route destruction in memory. The host must call `setScope` whenever its session changes, even when no runtime component is mounted; the sandbox's `ConsoleRuntimeSessionBridge` demonstrates synchronous session invalidation. Shared components also supply their input scope. Clear/log-out/session replacement removes private entries; no browser persistence is installed. Retry submits original values; Find result only looks up a receipt. Original results and separately refreshed current work are labelled independently, and refresh failures retain committed evidence.

Optional `WorkflowRuntimeCommands.start` supplies `freezeBody`, `execute` and `replay` for the host's existing start envelopes. Shared code knows no sandbox request fields. Without this adapter, start restore fails before insertion; completion remains available. Exported envelopes retain `kind`, `body` and `expectedToken`, including raw start-input text. Start forms, validation, exact serialization and business linking remain host-owned.

Navigation adds `commands`, `instanceTasks` and `submissions` intents to the existing host adapter; hosts may return null. Runtime recovery also uses `WORKFLOW_VIEWER_READS` for separate current-instance reads. The existing NgModule fixture demonstrates alternate transport, headings/navigation and a task consumer with no command provider.

The shared task component displays F022 previous decisions for enabled pinned contracts, including completed tasks and hosts without a command provider. Implement `WorkflowRuntimeReads.previousDecisions(id, cursor, limit, signal)` with authorized receiving-task reads; pass exact sequence strings through unchanged. The section has its own loading/error/retry state and appends whole pages without gating task completion. Comment and attribution values render as text; source identifiers do not create navigation links. Task/session changes clear the section and abort/ignore stale reads. The NgModule fixture accepts `?previousDecisionEvidence=completed`, `selected`, or `none` for focused rendering checks.

The instance viewer optionally accepts `WORKFLOW_CANCELLATION` implementing `WorkflowCancellation`. Its `availability(id, signal)` reads `/instances/{id}/cancellation-availability`; `cancel(envelope, signal)` sends `/instances/{id}/cancel` and `replay(envelope, signal)` sends lookup-only `/commands/cancel/replay`. Omit this provider for view-only consumers; `WORKFLOW_RUNTIME_COMMANDS` is not required for cancellation. Availability is advisory: show the action only for an ACTIVE instance with `allowed:true` and the same decimal-string lock version. Hosts still authorize every command and recovery request.

The cancellation envelope has `kind:'cancel'`, frozen `body:{commandId,instanceId,reason}` and `expectedToken`. Send only `{commandId,reason}` for mutation, all body fields for replay, and the token in `X-Expected-Lock-Version`. Preserve exact reason text, canonical IDs/UUID and trusted host credentials/CSRF. Cancellation results contain instance/activity and `cancelledTaskCount`, with no representative task. Errors must retain `httpStatus` to distinguish rejection from unknown outcomes.

The Material confirmation requires a nonblank reason of at most 2000 UTF-16 units, explaining retained decisions/business effects. Pending commands prevent duplicate submission; there is no automatic retry. Unknown outcomes remain in the session buffer, with explicit lookup-only recovery and, after a miss, explicit retry of the original envelope. A miss or failed lookup cannot authorize a replacement. Successful execution/recovery refreshes instance, graph and availability. Cancellation reason renders as text beside closure actor/time; stopped graph nodes remain CANCELLED and tasks distinguish instance cancellation from resolved-review cancellation. The existing host session bridge must invalidate `CommandRecovery` on every session replacement even off-route. The database-free NgModule fixture enables cancellation with `?cancellationEvidence=true`; omitting it preserves the view-only configuration.
