import type { Decimal, InstanceView } from "../models";

export interface Page<T> { items:T[]; nextAfterId:string|null; }
export interface SubmissionPage {
  items: TaskSubmission[];
  nextAfterTaskId: Decimal | null;
}

export interface TaskAssignment { kind: "USER" | "GROUP" | "ROLE"; ref: string; }

export interface TaskView {
  id: Decimal;
  activityId: Decimal;
  instanceId: Decimal;
  taskTypeId: Decimal;
  taskKey: string;
  assignment: TaskAssignment;
  status: string;
  outcomeKey: string | null;
  createdAt: string;
  dueAt: string | null;
  closedAt: string | null;
  closedBy: string | null;
  closureReason: string | null;
}
export interface TaskContract {
  taskId: Decimal;
  instanceId: Decimal;
  instanceLockVersion: Decimal;
  taskType: string;
  canComplete: boolean;
  assignmentLabel: string;
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

export interface VariablePage { exactJson?: string;
  instanceId: Decimal; lockVersion: Decimal;
  items: { key:string; type:string; initialized:boolean; value:unknown }[];
  nextAfterKey: string|null;
}
