export type { SubmissionPage, TaskView, TaskContract, ActivityView, TaskSubmission, Execution, CommandResult, ReplayResult, Observation, VariablePage } from '@workflow-engine/ui';
import type { DefinitionView, VersionSummary, VersionDetails, Change, DefinitionChange, Diagnostic, ValidationReport, DefinitionCheck, TaskTypeView, TaskTypeInput } from '@workflow-engine/ui';
export type { DefinitionView, VersionSummary, VersionDetails, Change, DefinitionChange, Diagnostic, ValidationReport, DefinitionCheck, TaskTypeView, TaskTypeInput } from '@workflow-engine/ui';
import type { InstanceStatus, InstanceSummary, InstanceView, InstanceGraphNode, InstanceGraphEdge, InstanceGraph, HistorySummary, HistoryEvent, HistoryPage } from "@workflow-engine/ui";
export type { InstanceStatus, InstanceSummary, InstanceView, InstanceGraphNode, InstanceGraphEdge, InstanceGraph, HistorySummary, HistoryEvent, HistoryPage } from "@workflow-engine/ui";
/** HTTP contracts only. BIGINT identifiers, tokens and sequences are never JS numbers. */
export type Decimal = string;
export type JsonText = string;
export interface Page<T> {
  items: T[];
  nextAfterId: Decimal | null;
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
export interface SandboxRequestSummary {
  id: Decimal;
  name: string;
  status: string;
  createdAt: string;
  createdBy: string;
  instanceId: Decimal | null;
  lockVersion: Decimal;
}
export interface SandboxRequest extends SandboxRequestSummary {
  requestDataJson: string | null;
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

export interface ServiceEffect {
  id:Decimal; requestId:Decimal; instanceId:Decimal; commandId:string; nodeKey:string;
  quantity:string; actorRef:string; occurredAt:string;
}
