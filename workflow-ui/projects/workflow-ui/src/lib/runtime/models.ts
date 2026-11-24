import type { Decimal, InstanceView } from "../models";
import type { WorkflowTaskFormField } from "./task-form";

export interface Page<T> { items:T[]; nextAfterId:string|null; }
export interface TaskPage<T> { items:T[]; nextCursor:string|null; }
/** Host-approved destination reference and display label; no host directory types escape this boundary. */
export interface WorkflowReassignmentTarget { ref:string; label:string; }
/** Bounded destination lookup result. The host owns the opaque cursor; null means no further page. */
export interface WorkflowReassignmentTargetPage { items:WorkflowReassignmentTarget[]; nextCursor:string|null; }
export interface SubmissionPage {
  items: (TaskSubmission & { taskId: Decimal })[];
  nextAfterTaskId: Decimal | null;
}

export interface TaskAssignment { kind: "USER" | "GROUP" | "ROLE"; ref: string; }
export type TaskResultContract =
  | { mode: "FORBIDDEN"; form?: never }
  | { mode: "OPTIONAL" | "REQUIRED"; form?: WorkflowTaskFormField };
export interface TaskInput {
  readonly type: "STRING" | "LONG" | "DECIMAL" | "BOOLEAN" | "DATE" | "DATETIME" | "JSON";
  /** Exact JSON token text. Extract from the response before reserializing any parsed values. */
  readonly valueJson: string;
}

export interface TaskDetail {
  nodeName?: string;
  taskTypeName?: string;
  status: string;
  dueAt?: string | null;
  closedAt?: string | null;
  assignmentLabel?: string;
  ownershipLabel?: string;
  closureLabel?: string;
  outcomeLabel?: string;
}
export interface TaskView extends TaskDetail { id: Decimal; }
export interface PreviousDecision {
  nodeName: string; outcomeLabel: string; actorLabel: string; occurredAt: string; comment?: string | null;
}
export interface PreviousDecisionPage { items: PreviousDecision[]; nextCursor: string | null; }
export interface TaskContract {
  task: TaskDetail;
  /** Display context from the task's pinned workflow. Instance access is independently authorized. */
  workflow?: { name: string; instanceId: Decimal };
  precondition?: string;
  previousDecisionsAvailable: boolean;
  taskType?: string;
  claimRequired: boolean;
  canClaim: boolean;
  canRelease: boolean;
  canAdminRelease: boolean;
  canComplete: boolean;
  resultContract?: TaskResultContract;
  inputs?: Readonly<Record<string, TaskInput>>;
  outcomes: {
    key?: string;
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
  actorLabel: string;
  occurredAt: string;
  outcomeLabel: string;
  comment?: string | null;
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
export interface TaskCommandResult {
  replayed: boolean;
  status: string;
  taskStatus: string;
  collectivePending?: boolean;
  nextTaskId?: Decimal;
}
export interface TaskCommandReplay { found: boolean; result: TaskCommandResult | null; }
export interface CancellationExecution {
  commandId: string; operation: 'CANCEL_WORKFLOW'; instance: InstanceView; activity: ActivityView;
  cancelledTaskCount: number; firstHistorySequence: Decimal; lastHistorySequence: Decimal;
}
export interface CancellationResult { replayed: boolean; execution: CancellationExecution; }
export interface CancellationReplay { found: boolean; result: CancellationResult | null; }
export interface Observation {
  outcome: string;
  result?: CommandResult | ReplayResult | CancellationResult | CancellationReplay | TaskCommandResult | TaskCommandReplay;
  message?: string;
}

export interface VariablePage { exactJson?: string;
  instanceId: Decimal; lockVersion: Decimal;
  items: { key:string; type:string; initialized:boolean; value:unknown }[];
  nextAfterKey: string|null;
}

export interface TaskAdministrationFilters {
 status:'OPEN'|'COMPLETED'|'CANCELLED'|'ALL'; taskType?:string; taskName?:string;
 assignmentKind?:'USER'|'GROUP'|'ROLE'; assignmentRef?:string; assigneeRef?:string; instanceId?:string;
}

export interface TaskAdministrationRow extends TaskView {
 instanceId: Decimal;
 createdAt: string;
}
