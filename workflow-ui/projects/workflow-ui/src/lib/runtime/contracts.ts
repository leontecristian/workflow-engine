import { InjectionToken } from '@angular/core';
import type { InstanceSummary } from '../models';
import type { Page, PreviousDecisionPage, VariablePage, TaskView, TaskContract, TaskSubmission, ActivityView, SubmissionPage, CommandResult, ReplayResult } from './models';
import type { Envelope, StartEnvelopeAdapter } from './command/command-buffer';
/** Host-authorized reads; transport and cancellation belong to the host. */
export interface WorkflowRuntimeReads {
 listInstances(status:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<Page<InstanceSummary>>;
 variables(id:string,afterKey:string|undefined,expectedLockVersion:string|undefined,signal:AbortSignal):Promise<VariablePage>;
 assignedTasks(cursor:string|undefined,limit:number,signal:AbortSignal):Promise<Page<TaskView>>;
 instanceTasks(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<Page<TaskView>>;
 task(id:string,signal:AbortSignal):Promise<TaskView>;
 taskContract(id:string,signal:AbortSignal):Promise<TaskContract>;
 previousDecisions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<PreviousDecisionPage>;
 taskSubmission(id:string,signal:AbortSignal):Promise<TaskSubmission|null>;
 activity(id:string,signal:AbortSignal):Promise<ActivityView>;
 submissions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<SubmissionPage>;
}
export const WORKFLOW_RUNTIME_READS = new InjectionToken<WorkflowRuntimeReads>('Workflow runtime reads');
/** Optional host execution transport, including optional host-owned start codec/dispatch. */
export interface WorkflowRuntimeCommands {
 complete(envelope:Envelope,signal:AbortSignal):Promise<CommandResult>;
 replayComplete(envelope:Envelope,signal:AbortSignal):Promise<ReplayResult>;
 ownership?(envelope:Envelope,replay:boolean,signal:AbortSignal):Promise<CommandResult | ReplayResult>;
 start?: StartEnvelopeAdapter;
}
export const WORKFLOW_RUNTIME_COMMANDS = new InjectionToken<WorkflowRuntimeCommands>('Workflow runtime commands');
export const DEFAULT_RUNTIME_MESSAGES = {
 instancesTitle:'Instances', taskTitle:'Task details', assignedTitle:'My tasks', instanceTasksTitle:'Instance tasks',
 activityTitle:'Activity details', submissionsTitle:'Activity submissions', variablesTitle:'Execution variables', commandsTitle:'Command recovery',
 refresh:'Refresh', loading:'Loading…', empty:'No records on this page.', next:'Next page', failed:'Read failed. Refresh to try again.',
};
export type WorkflowRuntimeMessages = typeof DEFAULT_RUNTIME_MESSAGES;
