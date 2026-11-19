import { Injectable, inject } from '@angular/core';
import { findNodeAtLocation, parseTree, visit, type JSONPath } from 'jsonc-parser';
import { ConsoleSession } from './console-session';
import { hydrateTaskForm } from '@workflow-engine/ui';
import type { TaskAdministrationFilters, TaskAdministrationRow, WorkflowRuntimeReads, InstanceSummary, Page, TaskPage, VariablePage, TaskDetail, TaskView, TaskContract, TaskInput, TaskSubmission, ActivityView, SubmissionPage, PreviousDecisionPage, WorkflowTaskFormWireField } from '@workflow-engine/ui';

export interface TaskDiagnostics {
 task: { id: string; activityId: string; instanceId: string };
 taskJson: string;
 contractJson: string;
 submissionJson?: string;
 resultJson?: string;
}
@Injectable({providedIn:'root'})
export class ConsoleRuntimeReads implements WorkflowRuntimeReads {
 private readonly session=inject(ConsoleSession);
 private readText(path:string,signal:AbortSignal){
  return this.session.administrationCall<string>(path,signal,'GET',undefined,undefined,false,true);
 }
 private async read<T>(path:string,signal:AbortSignal):Promise<T>{
  const text=await this.readText(path,signal);
  const value=JSON.parse(text) as T;if(value&&typeof value==='object')Object.defineProperty(value,'exactJson',{value:text});return value;
 }
 private async readExact<T>(path:string,signal:AbortSignal,limit=1024*1024){
  return this.parseExact<T>(await this.readText(path,signal),limit);
 }
 private parseExact<T>(text:string,limit:number){
  if(text.length>limit)throw new Error('Response JSON exceeds the size limit.');
  let depth=0;
  const value=JSON.parse(text) as T;
  const enter=()=>{if(++depth>128)throw new Error('Response JSON exceeds the nesting limit.');};
  const exit=()=>{depth--;};
  visit(text,{onObjectBegin:enter,onArrayBegin:enter,onObjectEnd:exit,onArrayEnd:exit});
  const tree=parseTree(text);
  function tokenAt(location:JSONPath, objectOnly = false) {
   const node=tree&&findNodeAtLocation(tree,location);
   if(!node)return undefined;
   if(objectOnly&&node.type!=='object')throw new Error('Invalid read response.');
   return text.slice(node.offset,node.offset+node.length);
  }
  return {value, tokenAt, objectAt:(location:JSONPath)=>tokenAt(location,true)};
 }
 private query(values:Record<string,string|undefined>){return '?'+new URLSearchParams(Object.entries(values).filter((p):p is [string,string]=>p[1]!==undefined));}
 listInstances(status:string,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<Page<InstanceSummary>>('/instances'+this.query({status,afterId:cursor,limit:String(limit)}),signal);}
 variables(id:string,afterKey:string|undefined,expectedLockVersion:string|undefined,signal:AbortSignal){return this.read<VariablePage>('/instances/'+encodeURIComponent(id)+'/variables'+this.query({afterKey,expectedLockVersion,limit:'50'}),signal);}
 administrationTasks(filters:TaskAdministrationFilters,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<TaskPage<TaskAdministrationRow>>('/tasks/administration'+this.query({...filters,cursor,limit:String(limit)}),signal);}
 assignedTasks(cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<TaskPage<TaskView>>('/tasks/mine'+this.query({cursor,limit:String(limit)}),signal);}
 instanceTasks(id:string,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<TaskPage<TaskView>>('/instances/'+encodeURIComponent(id)+'/tasks'+this.query({cursor,limit:String(limit)}),signal);}
 task(id:string,signal:AbortSignal){return this.read<TaskDetail>('/tasks/'+encodeURIComponent(id),signal);}
 async taskContract(id:string,signal:AbortSignal){
  const {value:{inputs,resultContract,...contract},tokenAt}=await this.readExact<WireTaskContract>(
   '/tasks/'+encodeURIComponent(id)+'/contract',signal,16*1024*1024);
  const result= resultContract?.mode==='FORBIDDEN' ? {mode:resultContract.mode}
   : resultContract&&{mode:resultContract.mode,form:resultContract.form===undefined?undefined:hydrateTaskForm(resultContract.form)};
  if(inputs===undefined)return {...contract,resultContract:result};
  return {...contract,resultContract:result,inputs:Object.fromEntries(Object.entries(inputs).map(([key,input])=>{
   const valueJson=tokenAt(['inputs',key,'value']);
   if(valueJson===undefined)throw new Error('Invalid read response.');
   return [key,{type:input.type,valueJson}];
  }))};
 }
 async readTaskDiagnostics(id:string,signal:AbortSignal):Promise<TaskDiagnostics>{
  // An inspection failure is local to the disclosure, even if the server returns 401.
  const response=await fetch('/console/api/tasks/'+encodeURIComponent(id)+'/diagnostics',
   {credentials:'same-origin',signal});
  if(!response.ok)throw new Error('Task diagnostics are unavailable.');
  const {value,objectAt}=this.parseExact<{task:TaskDiagnostics['task'];submission?:object|null}>(
   await response.text(),16*1024*1024);
  const taskJson=objectAt(['task']),contractJson=objectAt(['contract']);
  if(!taskJson||!contractJson)throw new Error('Invalid diagnostics response.');
  return {task:value.task,taskJson,contractJson,
   submissionJson:value.submission?objectAt(['submission']):undefined,
   resultJson:value.submission?objectAt(['submission','result']):undefined};
 }
 previousDecisions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal){
  return this.read<PreviousDecisionPage>('/tasks/'+encodeURIComponent(id)+'/previous-decisions'+this.query({cursor,limit:String(limit)}),signal);
 }
 async taskSubmission(id:string,signal:AbortSignal){
  const {value,objectAt}=await this.readExact<WireSubmission|null>('/tasks/'+encodeURIComponent(id)+'/submission',signal);
  return value&&exactSubmission(value,objectAt(['result']));
 }
 activity(id:string,signal:AbortSignal){return this.read<ActivityView>('/activities/'+encodeURIComponent(id),signal);}
 async submissions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<SubmissionPage>{
  const path='/activities/'+encodeURIComponent(id)+'/submissions'+this.query({afterTaskId:cursor,limit:String(limit)});
  const {value,objectAt}=await this.readExact<Omit<SubmissionPage,'items'>&{items:(WireSubmission&{taskId:string})[]}>(path,signal);
  return {...value,items:value.items.map((row,index)=>exactSubmission(row,objectAt(['items',index,'result'])))};
 }
}

type WireSubmission=TaskSubmission&{result?:object};
type WireTaskContract=Omit<TaskContract,'inputs'|'resultContract'>&{
 inputs?:Record<string,{type:TaskInput['type'];value:unknown}>;
 resultContract?:{mode:'FORBIDDEN'|'OPTIONAL'|'REQUIRED';form?:WorkflowTaskFormWireField};
};
function exactSubmission<T extends WireSubmission>(row:T,resultJson:string|undefined):T {
 delete row.result;
 if(resultJson!==undefined)row.resultJson=resultJson;
 return row;
}
