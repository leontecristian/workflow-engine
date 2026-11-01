import { Injectable, inject } from "@angular/core";
import { ConsoleSession } from "./console-session";
import { commandRequest, type Envelope } from "../command-buffer";
import type {
  Page,
  DefinitionView,
  VersionSummary,
  VersionDetails,
  DefinitionChange,
  DefinitionCheck,
  Change,
  TaskTypeView,
  TaskTypeInput,
  SandboxRequest,
  InstanceSummary,
  InstanceView,
  InstanceGraph,
  TaskView,
  TaskContract,
  TaskSubmission,
  ActivityView,
  HistoryPage,
  HistoryEvent,
  SubmissionPage,
  OutboxEvent,
  InboxEvent,
  AuditView,
  Capabilities,
  CommandResult,
  ReplayResult,
  DispatchResult,
  DeliveryResult,
} from "./dtos";
function pageQuery(
  cursor: string | undefined,
  limit: number,
  extra: Record<string, string> = {},
  cursorName = "afterId",
) {
  const params = new URLSearchParams({ limit: String(limit), ...extra });
  if (cursor) params.set(cursorName, cursor);
  return "?" + params;
}
const segment = (value: string) => encodeURIComponent(value);
/** Endpoint contracts; authoring documents are passed through as raw text. */
@Injectable({ providedIn: "root" })
export class ConsoleApi {
  private readonly transport = inject(ConsoleSession);
  readonly session = this.transport.session;
  readonly capabilities = this.transport.capabilities;
  get admin() {
    return this.transport.admin;
  }
  initialize() {
    return this.transport.initialize();
  }
  logout() {
    return this.transport.logout();
  }
  listDefinitions(cursor?: string, limit = 50) {
    return this.transport.call<Page<DefinitionView>>(
      "/definitions" + pageQuery(cursor, limit),
    );
  }
  definition(id: string) {
    return this.transport.call<DefinitionView>("/definitions/" + segment(id));
  }
  saveMetadata(
    id: string,
    name: string,
    description: string | null,
    token: string,
  ) {
    return this.transport.call<Change<DefinitionView>>(
      "/definitions/" + segment(id),
      "PUT",
      { name, description },
      token,
    );
  }
  listVersions(id: string, status = "", cursor?: string, limit = 50) {
    return this.transport.call<Page<VersionSummary>>(
      `/definitions/${segment(id)}/versions` +
        pageQuery(cursor, limit, status ? { status } : {}),
    );
  }
  version(id: string) {
    return this.transport.call<VersionDetails>("/versions/" + segment(id));
  }
  lookupVersion(key: string, version: string) {
    return this.transport.call<VersionDetails>(
      "/versions/lookup?" + new URLSearchParams({ key, version }),
    );
  }
  importDraft(document: string) {
    return this.transport.call<DefinitionChange>(
      "/versions/import",
      "POST",
      document,
      null,
      true,
    );
  }
  saveDocument(id: string, document: string, token: string) {
    return this.transport.call<DefinitionChange>(
      `/versions/${segment(id)}/document`,
      "PUT",
      document,
      token,
      true,
    );
  }
  checkVersion(id: string, action: "validate" | "publish", token: string) {
    return this.transport.call<DefinitionCheck>(
      `/versions/${segment(id)}/${action}`,
      "POST",
      undefined,
      token,
    );
  }
  retireVersion(id: string, token: string) {
    return this.transport.call<DefinitionChange>(
      `/versions/${segment(id)}/retire`,
      "POST",
      undefined,
      token,
    );
  }
  listTaskTypes(filter: string, cursor?: string, limit = 50) {
    return this.transport.call<Page<TaskTypeView>>(
      "/task-types" + pageQuery(cursor, limit, { filter }),
    );
  }
  createTaskType(
    body: TaskTypeInput & {
      code: string;
    },
  ) {
    return this.transport.call<Change<TaskTypeView>>(
      "/task-types",
      "POST",
      body,
    );
  }
  updateTaskType(id: string, body: TaskTypeInput, token: string) {
    return this.transport.call<Change<TaskTypeView>>(
      "/task-types/" + segment(id),
      "PUT",
      body,
      token,
    );
  }
  listRequests(cursor?: string, limit = 50) {
    return this.transport.call<Page<SandboxRequest>>(
      "/sandbox-requests" + pageQuery(cursor, limit),
    );
  }
  request(id: string) {
    return this.transport.call<SandboxRequest>(
      "/sandbox-requests/" + segment(id),
    );
  }
  createRequest(name: string) {
    return this.transport.call<SandboxRequest>("/sandbox-requests", "POST", {
      name,
    });
  }
  listInstances(status: string, cursor?: string, limit = 50) {
    return this.transport.call<Page<InstanceSummary>>(
      "/instances" + pageQuery(cursor, limit, { status }),
    );
  }
  instance(id: string) {
    return this.transport.call<InstanceView>("/instances/" + segment(id));
  }
  variables(id:string, afterKey?:string, expectedLockVersion?:string) {
    const params=new URLSearchParams({limit:"50"});
    if(afterKey) params.set("afterKey",afterKey);
    if(expectedLockVersion) params.set("expectedLockVersion",expectedLockVersion);
    return this.transport.call<import("./dtos").VariablePage>("/instances/"+segment(id)+"/variables?"+params);
  }
  serviceEffects(id:string,cursor?:string) {
    return this.transport.call<Page<import("./dtos").ServiceEffect>>("/instances/"+segment(id)+"/service-effects"+pageQuery(cursor,50));
  }
  instanceGraph(id: string) {
    return this.transport.call<InstanceGraph>(
      `/instances/${segment(id)}/graph`,
    );
  }
  assignedTasks(cursor?: string, limit = 50) {
    return this.transport.call<Page<TaskView>>(
      "/tasks/mine" + pageQuery(cursor, limit),
    );
  }
  instanceTasks(id: string, cursor?: string, limit = 50) {
    return this.transport.call<Page<TaskView>>(
      `/instances/${segment(id)}/tasks` + pageQuery(cursor, limit),
    );
  }
  task(id: string) {
    return this.transport.call<TaskView>("/tasks/" + segment(id));
  }
  taskContract(id: string) {
    return this.transport.call<TaskContract>(`/tasks/${segment(id)}/contract`);
  }
  taskSubmission(id: string) {
    return this.transport.call<TaskSubmission | null>(
      `/tasks/${segment(id)}/submission`,
    );
  }
  activity(id: string) {
    return this.transport.call<ActivityView>("/activities/" + segment(id));
  }
  history(id: string, cursor?: string, limit = 50) {
    return this.transport.call<HistoryPage>(
      `/instances/${segment(id)}/history` +
        pageQuery(cursor, limit, {}, "afterSequence"),
    );
  }
  historyEvent(id: string, sequence: string) {
    return this.transport.call<HistoryEvent>(
      `/instances/${segment(id)}/history/${segment(sequence)}`,
    );
  }
  submissions(id: string, cursor?: string, limit = 50) {
    return this.transport.call<SubmissionPage>(
      `/activities/${segment(id)}/submissions` +
        pageQuery(cursor, limit, {}, "afterTaskId"),
    );
  }
  outbox(status: string, cursor?: string, limit = 50) {
    return this.transport.call<Page<OutboxEvent>>(
      "/events" + pageQuery(cursor, limit, { status }),
    );
  }
  outboxEvent(id: string) {
    return this.transport.call<OutboxEvent>("/events/" + segment(id));
  }
  dispatch(limit: number) {
    return this.transport.call<DispatchResult>("/events/dispatch", "POST", {
      limit,
    });
  }
  redeliver(id: string) {
    return this.transport.call<DeliveryResult>(
      `/events/${segment(id)}/redeliver`,
      "POST",
    );
  }
  inbox(cursor?: string, limit = 50) {
    return this.transport.call<Page<InboxEvent>>(
      "/inbox" + pageQuery(cursor, limit),
    );
  }
  inboxEvent(id: string) {
    return this.transport.call<InboxEvent>("/inbox/" + segment(id));
  }
  audit(cursor?: string, limit = 50) {
    return this.transport.call<Page<AuditView>>(
      "/audit" + pageQuery(cursor, limit),
    );
  }
  auditEvent(id: string) {
    return this.transport.call<AuditView>("/audit/" + segment(id));
  }
  getCapabilities() {
    return this.transport.call<Capabilities>("/capabilities");
  }
  executeCommand(envelope: Envelope, replay: boolean) {
    const request = commandRequest(envelope, replay);
    return this.transport.call<CommandResult | ReplayResult>(
      request.path,
      "POST",
      request.body,
      request.token,
    );
  }
}
