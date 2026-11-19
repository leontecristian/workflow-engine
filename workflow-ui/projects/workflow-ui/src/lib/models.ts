/** Detached read wire contracts. Longs remain decimal strings. */
export type Decimal = string;
export type JsonText = string;
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
export interface InstanceGraphNode {
  key: string;
  name: string;
  type: string;
  state: "ACTIVE" | "COMPLETED" | "PENDING" | "CANCELLED";
  activityId: Decimal | null;
  taskId: Decimal | null;
}
export interface InstanceGraphEdge {
  key: string;
  from: string;
  to: string;
  outcome: string | null;
  traversed: boolean;
}
export interface InstanceGraph {
  instance: InstanceView;
  nodes: InstanceGraphNode[];
  edges: InstanceGraphEdge[];
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

export interface HistoryPage { items: HistorySummary[]; nextAfterSequence: Decimal | null; }

export interface BusinessHistorySummary {
  eventRef: string;
  label: string;
  actorLabel: string;
  occurredAt: string;
}
export interface BusinessHistoryPage {
  representation: 'business';
  items: BusinessHistorySummary[];
  nextCursor: string | null;
}
export interface BusinessHistoryEvent {
  representation: 'business';
  summary: BusinessHistorySummary;
  stepName: string | null;
  outcomeLabel: string | null;
  comment: string | null;
  taskId: Decimal | null;
}
