/** HTTP contracts only. BIGINT identifiers, tokens and sequences are never JS numbers. */
export type Decimal = string;
export type JsonText = string;
export interface Page<T> {
  items: T[];
  nextAfterId: Decimal | null;
}
export interface HistoryPage {
  items: HistorySummary[];
  nextAfterSequence: Decimal | null;
}
export interface SubmissionPage {
  items: TaskSubmission[];
  nextAfterTaskId: Decimal | null;
}
export interface Session {
  actorRef: string;
  roles: string[];
  csrfHeaderName: string;
  csrfToken: string;
}
export interface Capabilities {
  mode: string;
  reviewer: string;
  singleAssignments: string[];
  taskTypes: Record<string, string[]>;
  failureFlags: Record<string, boolean>;
}
export interface DefinitionView {
  id: Decimal;
  key: string;
  name: string;
  description: string | null;
  createdAt: string;
  createdBy: string;
  lockVersion: Decimal;
}
export interface VersionSummary {
  id: Decimal;
  definitionId: Decimal;
  key: string;
  version: number;
  schemaVersion: number;
  status: "DRAFT" | "PUBLISHED" | "RETIRED";
  contentRevision: Decimal;
  lockVersion: Decimal;
  contentHash: string;
  canonicalizationVersion: string;
  createdAt: string;
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
  publishedAt: string | null;
  publishedBy: string | null;
  retiredAt: string | null;
  retiredBy: string | null;
  validation: unknown;
}
export interface VersionDetails {
  summary: VersionSummary;
  documentJson: JsonText;
  validationDetailsJson: JsonText | null;
}
export interface Change<T> {
  before: T | null;
  after: T;
  changed: boolean;
}
export interface DefinitionChange extends Change<VersionSummary> {
  operation: string;
  context: unknown;
}
export interface Diagnostic {
  severity: string;
  code: string;
  message: string;
  stage: string;
  pointer: string | null;
}
export interface ValidationReport {
  valid: boolean;
  diagnostics: Diagnostic[];
  detailsOmitted: boolean;
}
export interface DefinitionCheck {
  change: DefinitionChange;
  report: ValidationReport;
  published: boolean;
}
export interface TaskTypeView {
  id: Decimal;
  code: string;
  name: string;
  description: string | null;
  selectable: boolean;
  lockVersion: Decimal;
}
export interface TaskTypeInput {
  name: string;
  description: string | null;
  selectable: boolean;
}
export interface SandboxRequest {
  id: Decimal;
  name: string;
  createdAt: string;
  createdBy: string;
  instanceId: Decimal | null;
  lockVersion: Decimal;
}
export type InstanceStatus = "ACTIVE" | "COMPLETED" | "CANCELLED";
export interface InstanceSummary {
  id: Decimal;
  definitionVersionId: Decimal;
  definitionKey: string;
  definitionVersion: number;
  status: InstanceStatus;
  currentNodeKey: string;
  resultKey: string | null;
  businessType: string | null;
  businessId: string | null;
  startedAt: string;
  startedBy: string;
  endedAt: string | null;
  lastHistorySequence: Decimal;
  lockVersion: Decimal;
}
export interface InstanceView extends InstanceSummary {
  definitionHash: string;
  endedBy: string | null;
  terminationReason: string | null;
}
export interface TaskView {
  id: Decimal;
  activityId: Decimal;
  instanceId: Decimal;
  taskTypeId: Decimal;
  taskKey: string;
  assignedUserRef: string;
  status: string;
  outcomeKey: string | null;
  createdAt: string;
  dueAt: string | null;
  closedAt: string | null;
  closedBy: string | null;
  closureReason: string | null;
}
export interface TaskContract {
  instanceLockVersion: Decimal;
  taskType: string;
  outcomes: {
    key: string;
    label: string;
    requiresComment: boolean;
  }[];
}
export interface ActivityView {
  id: Decimal;
  instanceId: Decimal;
  nodeKey: string;
  activationSequence: Decimal;
  taskTypeId: Decimal;
  completionPolicy: string;
  contributionOutcomeKey: string | null;
  requiredTaskCount: number;
  reviewRevisionRef: string | null;
  status: string;
  outcomeKey: string | null;
  enteredAt: string;
  closedAt: string | null;
  closedBy: string | null;
  closureReason: string | null;
}
export interface HistorySummary {
  instanceId: Decimal;
  historySequence: Decimal;
  eventType: string;
  commandId: string;
  correlationId: string;
  actorKind: string;
  actorRef: string;
  occurredAt: string;
  activityId: Decimal | null;
  taskId: Decimal | null;
  sourceNodeKey: string | null;
  targetNodeKey: string | null;
  transitionKey: string | null;
  detailsSchemaVersion: number;
}
export interface HistoryEvent {
  summary: HistorySummary;
  detailsJson: JsonText;
}
export interface TaskSubmission {
  taskId: Decimal;
  activityId: Decimal;
  instanceId: Decimal;
  historySequence: Decimal;
  commandId: string;
  actorKind: string;
  actorRef: string;
  occurredAt: string;
  outcomeKey: string;
  comment: string | null;
  inputs: Record<string, string>;
}
export interface Execution {
  commandId: string;
  operation: string;
  instance: InstanceView;
  activity: ActivityView;
  task: TaskView;
  firstHistorySequence: Decimal;
  lastHistorySequence: Decimal;
}
export interface CommandResult {
  replayed: boolean;
  execution: Execution;
}
export interface ReplayResult {
  found: boolean;
  result: CommandResult | null;
}
export interface Observation {
  outcome: string;
  result?: CommandResult | ReplayResult;
  message?: string;
}
export interface OutboxEvent {
  id: Decimal;
  eventId: string;
  instanceId: Decimal;
  historySequence: Decimal;
  commandId: string;
  eventType: string;
  occurredAt: string;
  deliveredAt: string | null;
  deliveryCount: Decimal;
  eventJson: JsonText;
}
export interface InboxEvent {
  id: Decimal;
  eventId: string;
  receivedAt: string;
  eventJson: JsonText;
}
export interface AuditView {
  id: Decimal;
  occurredAt: string;
  actorRef: string;
  operation: string;
  entityKind: string;
  entityId: Decimal;
  correlationId: string;
  evidenceJson: JsonText;
}
export interface DispatchResult {
  delivered: number;
  duplicates: number;
}
export interface DeliveryResult {
  eventId: string;
  duplicate: boolean;
}
