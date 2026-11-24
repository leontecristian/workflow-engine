import { InjectionToken } from '@angular/core';
import type { InstanceSummary } from '../models';
import type { Page, TaskPage, PreviousDecisionPage, VariablePage, TaskAdministrationFilters, TaskAdministrationRow, AssignedTaskRow, TaskView, TaskDetail, TaskContract, TaskSubmission, ActivityView, SubmissionPage, TaskCommandResult, TaskCommandReplay } from './models';
import type { Envelope, StartEnvelopeAdapter } from './command/command-buffer';
import type { WorkflowReassignmentTargetPage } from './models';
/** Host-authorized reads; transport and cancellation belong to the host. */
export interface WorkflowRuntimeReads {
 listInstances(status:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<Page<InstanceSummary>>;
 variables(id:string,afterKey:string|undefined,expectedLockVersion:string|undefined,signal:AbortSignal):Promise<VariablePage>;
 administrationTasks?(filters:TaskAdministrationFilters,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<TaskPage<TaskAdministrationRow>>;
 assignedTasks(filters:TaskAdministrationFilters,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<TaskPage<AssignedTaskRow>>;
 instanceTasks(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<TaskPage<TaskView>>;
 task(id:string,signal:AbortSignal):Promise<TaskDetail>;
 taskContract(id:string,signal:AbortSignal):Promise<TaskContract>;
 previousDecisions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<PreviousDecisionPage>;
 taskSubmission(id:string,signal:AbortSignal):Promise<TaskSubmission|null>;
 activity(id:string,signal:AbortSignal):Promise<ActivityView>;
 submissions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<SubmissionPage>;
}
export const WORKFLOW_RUNTIME_READS = new InjectionToken<WorkflowRuntimeReads>('Workflow runtime reads');
/** Optional host execution transport, including optional host-owned start codec/dispatch. */
export interface WorkflowRuntimeCommands {
 complete(envelope:Envelope,signal:AbortSignal):Promise<TaskCommandResult>;
 replayComplete(envelope:Envelope,signal:AbortSignal):Promise<TaskCommandReplay>;
 ownership?(envelope:Envelope,replay:boolean,signal:AbortSignal):Promise<TaskCommandResult | TaskCommandReplay>;
 start?: StartEnvelopeAdapter;
}
export const WORKFLOW_RUNTIME_COMMANDS = new InjectionToken<WorkflowRuntimeCommands>('Workflow runtime commands');
/** Optional instance cancellation; hosts own transport, identity and authorization. */
export interface WorkflowCancellation {
 availability(id:string,signal:AbortSignal):Promise<{allowed:boolean;lockVersion:string}>;
 cancel(envelope:Envelope,signal:AbortSignal):Promise<import('./models').CancellationResult>;
 replay(envelope:Envelope,signal:AbortSignal):Promise<import('./models').CancellationReplay>;
}
export const WORKFLOW_CANCELLATION = new InjectionToken<WorkflowCancellation>('Workflow cancellation');
export const DEFAULT_ASSIGNED_TASKS_DESCRIPTION = 'Your eligible open tasks.';
export const DEFAULT_RUNTIME_MESSAGES = {
 instancesTitle:'Instances', taskTitle:'Task details', assignedTitle:'My Tasks', instanceTasksTitle:'Instance tasks',
 activityTitle:'Activity details', submissionsTitle:'Activity submissions', variablesTitle:'Execution variables', commandsTitle:'Command recovery',
 refresh:'Refresh', loading:'Loading…', empty:'No records on this page.', next:'Next page', failed:'Read failed. Refresh to try again.',
};
export type WorkflowRuntimeMessages = typeof DEFAULT_RUNTIME_MESSAGES;

/** Optional host-owned task administration permissions, directory and command transport. */
export interface WorkflowTaskReassignment {
 availability(taskId:string,signal:AbortSignal):Promise<{allowed:boolean;precondition?:string}>;
 targets(taskId:string,kind:'USER'|'GROUP'|'ROLE',query:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<WorkflowReassignmentTargetPage>;
 reassign(envelope:Envelope,replay:boolean,signal:AbortSignal):Promise<TaskCommandResult|TaskCommandReplay>;
}
export const WORKFLOW_TASK_REASSIGNMENT = new InjectionToken<WorkflowTaskReassignment>('Workflow task reassignment');
