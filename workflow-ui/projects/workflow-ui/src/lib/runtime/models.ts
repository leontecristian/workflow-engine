import type { Decimal, InstanceView } from "../models";

export interface Page<T> { items:T[]; nextAfterId:string|null; }
export interface SubmissionPage {
  items: TaskSubmission[];
  nextAfterTaskId: Decimal | null;
}

export interface TaskAssignment { kind: "USER" | "GROUP" | "ROLE"; ref: string; }
export type TaskResultContract =
  | { mode: "FORBIDDEN" }
  | { mode: "OPTIONAL" | "REQUIRED"; schema: object; schemaJson?: string };
export interface TaskInput {
  readonly type: "STRING" | "LONG" | "DECIMAL" | "BOOLEAN" | "DATE" | "DATETIME" | "JSON";
  /** Exact JSON token text. Extract from the response before reserializing any parsed values. */
  readonly valueJson: string;
}

export interface TaskView {
  id: Decimal;
  activityId: Decimal;
  instanceId: Decimal;
  taskTypeId: Decimal;
  taskKey: string;
  nodeName?: string;
  taskType?: string;
  taskTypeName?: string;
  assignment: TaskAssignment;
  assigneeRef: string | null;
  status: string;
  outcomeKey: string | null;
  createdAt: string;
  dueAt: string | null;
  closedAt: string | null;
  closedBy: string | null;
  closureReason: string | null;
}
export interface PreviousDecision {
  taskId: Decimal; activityId: Decimal; historySequence: Decimal;
  nodeKey: string; nodeName: string; outcomeKey: string; outcomeLabel: string;
  actorKind: string; actorRef: string; occurredAt: string; comment: string | null;
}
export interface PreviousDecisionPage { items: PreviousDecision[]; nextAfterSequence: Decimal | null; }
export interface TaskContract {
  previousDecisions?: { mode: "NONE" | "ALL_PREVIOUS" | "SELECTED" };
  taskId: Decimal;
  instanceId: Decimal;
  instanceLockVersion: Decimal;
  taskType: string;
  claimPolicy: "OPTIONAL" | "REQUIRED";
  canClaim: boolean;
  canRelease: boolean;
  canAdminRelease: boolean;
  canComplete: boolean;
  assignmentLabel: string;
  resultContract?: TaskResultContract;
  inputs?: Readonly<Record<string, TaskInput>>;
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
  /** Exact result text extracted from the authorized HTTP response. */
  resultJson?: string;
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
