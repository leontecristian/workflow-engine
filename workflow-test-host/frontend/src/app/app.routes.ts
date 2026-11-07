import { Routes } from "@angular/router";
import { RequestsPage } from "./pages/sandbox/requests";
import { RequestPage } from "./pages/sandbox/request";
import { HistoryPage } from "./pages/runtime/history";
import { HistoryEventPage } from "./pages/runtime/history-event";
import { OutboxPage } from "./pages/events/outbox";
import { EventPage } from "./pages/events/event";
import { InboxPage } from "./pages/events/inbox";
import { InboxEventPage } from "./pages/events/inbox-event";
import { AuditPage } from "./pages/operations/audit";
import { AuditEventPage } from "./pages/operations/audit-event";
import { CapabilitiesPage } from "./pages/operations/capabilities";
import { NotFoundPage } from "./pages/not-found/not-found";
export const APP_ROUTES: Routes = [
  { path: "", redirectTo: "my-tasks", pathMatch: "full" },
  { path: "definitions", loadComponent: () => import("./pages/definitions/definitions").then(m => m.DefinitionsPage) },
  { path: "definition/:id", loadComponent: () => import("./pages/definitions/family").then(m => m.FamilyPage) },
  { path: "version/:id", loadComponent: () => import("./pages/definitions/version").then(m => m.VersionPage) },
  { path: "catalog", loadComponent: () => import("./pages/catalog/catalog").then(m => m.CatalogPage) },
  { path: "requests", component: RequestsPage },
  { path: "request/:id", component: RequestPage },
  { path: "instances", loadComponent: () => import("./pages/runtime/instances").then(m => m.InstancesPage) },
  { path: "my-tasks", loadComponent: () => import("./pages/tasks/assigned-tasks").then(m => m.AssignedTasksPage) },
  { path: "instance-tasks/:id", loadComponent: () => import("./pages/tasks/instance-tasks").then(m => m.InstanceTasksPage) },
  { path: "task/:id", loadComponent: () => import("./pages/tasks/task").then(m => m.TaskPage) },
  { path: "instance/:id", loadComponent: () => import("./pages/runtime/instance").then(m => m.InstancePage) },
  { path: "activity/:id", loadComponent: () => import("./pages/runtime/activity").then(m => m.ActivityPage) },
  { path: "history/:id", component: HistoryPage },
  { path: "history-event/:id/:sequence", component: HistoryEventPage },
  { path: "submissions/:id", loadComponent: () => import("./pages/runtime/submissions").then(m => m.SubmissionsPage) },
  { path: "commands", loadComponent: () => import("./pages/commands/commands").then(m => m.CommandsPage) },
  { path: "events", component: OutboxPage },
  { path: "event/:id", component: EventPage },
  { path: "inbox", component: InboxPage },
  { path: "inbox-event/:id", component: InboxEventPage },
  { path: "audit", component: AuditPage },
  { path: "audit-event/:id", component: AuditEventPage },
  { path: "capabilities", component: CapabilitiesPage },
  { path: "**", component: NotFoundPage },
];
