# Workflow instance viewer

`@workflow-engine/ui` is an Angular 19.2 partial-compiled library. It owns read-only instance metadata, pinned graphs, ordered history and exact events. It has no application bootstrap, authentication, HTTP providers, routes, translation service or global theme. Angular, Router, Material, CDK and RxJS are peers.

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

For routine sandbox development after this initial setup, `npm run start:console` in `workflow-test-host/frontend` builds the library, refreshes just its installed package copy and starts the dev server without reinstalling dependencies. IntelliJ's **Workflow console frontend** configuration uses this command. Stop that dev server before running it again. The backend's **Workflow console (local)** configuration compiles Java only; production packaging remains manual.

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

The sandbox definition preview also consumes the exported pure `layoutGraph` function/layout types and the existing `@workflow-engine/ui/styles/graph.scss` Sass asset. That host-owned preview does not become a library authoring feature.
