import { Injectable, inject } from '@angular/core';
import { ConsoleSession } from './console-session';
import type { WorkflowRuntimeReads, InstanceSummary, Page, VariablePage, TaskView, TaskContract, TaskSubmission, ActivityView, SubmissionPage } from '@workflow-engine/ui';
@Injectable({providedIn:'root'})
export class ConsoleRuntimeReads implements WorkflowRuntimeReads {
 private readonly session=inject(ConsoleSession);
 private async read<T>(path:string,signal:AbortSignal):Promise<T>{
  const text=await this.session.administrationCall<string>(path,signal,'GET',undefined,undefined,false,true);
  const value=JSON.parse(text) as T;if(value&&typeof value==='object')Object.defineProperty(value,'exactJson',{value:text});return value;
 }
 private query(values:Record<string,string|undefined>){return '?'+new URLSearchParams(Object.entries(values).filter((p):p is [string,string]=>p[1]!==undefined));}
 listInstances(status:string,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<Page<InstanceSummary>>('/instances'+this.query({status,afterId:cursor,limit:String(limit)}),signal);}
 variables(id:string,afterKey:string|undefined,expectedLockVersion:string|undefined,signal:AbortSignal){return this.read<VariablePage>('/instances/'+encodeURIComponent(id)+'/variables'+this.query({afterKey,expectedLockVersion,limit:'50'}),signal);}
 assignedTasks(cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<Page<TaskView>>('/tasks/mine'+this.query({afterId:cursor,limit:String(limit)}),signal);}
 instanceTasks(id:string,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<Page<TaskView>>('/instances/'+encodeURIComponent(id)+'/tasks'+this.query({afterId:cursor,limit:String(limit)}),signal);}
 task(id:string,signal:AbortSignal){return this.read<TaskView>('/tasks/'+encodeURIComponent(id),signal);}
 taskContract(id:string,signal:AbortSignal){return this.read<TaskContract>('/tasks/'+encodeURIComponent(id)+'/contract',signal);}
 taskSubmission(id:string,signal:AbortSignal){return this.read<TaskSubmission|null>('/tasks/'+encodeURIComponent(id)+'/submission',signal);}
 activity(id:string,signal:AbortSignal){return this.read<ActivityView>('/activities/'+encodeURIComponent(id),signal);}
 submissions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<SubmissionPage>('/activities/'+encodeURIComponent(id)+'/submissions'+this.query({afterTaskId:cursor,limit:String(limit)}),signal);}
}
