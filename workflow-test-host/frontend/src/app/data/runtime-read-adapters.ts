import { Injectable, inject } from '@angular/core';
import { findNodeAtLocation, parseTree, visit, type JSONPath } from 'jsonc-parser';
import { ConsoleSession } from './console-session';
import type { WorkflowRuntimeReads, InstanceSummary, Page, VariablePage, TaskView, TaskContract, TaskSubmission, ActivityView, SubmissionPage } from '@workflow-engine/ui';
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
 private async readExact<T>(path:string,signal:AbortSignal){
  const text=await this.readText(path,signal);
  // The server enforces a 1 MiB UTF-8 response limit. Bound our tree walk as well.
  if(text.length>1024*1024)throw new Error('Response JSON exceeds the size limit.');
  let depth=0;
  const value=JSON.parse(text) as T;
  const enter=()=>{if(++depth>128)throw new Error('Response JSON exceeds the nesting limit.');};
  const exit=()=>{depth--;};
  visit(text,{onObjectBegin:enter,onArrayBegin:enter,onObjectEnd:exit,onArrayEnd:exit});
  const tree=parseTree(text);
  return {value,objectAt(location:JSONPath){
   const node=tree&&findNodeAtLocation(tree,location);
   if(!node)return undefined;
   if(node.type!=='object')throw new Error('Invalid read response.');
   return text.slice(node.offset,node.offset+node.length);
  }};
 }
 private query(values:Record<string,string|undefined>){return '?'+new URLSearchParams(Object.entries(values).filter((p):p is [string,string]=>p[1]!==undefined));}
 listInstances(status:string,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<Page<InstanceSummary>>('/instances'+this.query({status,afterId:cursor,limit:String(limit)}),signal);}
 variables(id:string,afterKey:string|undefined,expectedLockVersion:string|undefined,signal:AbortSignal){return this.read<VariablePage>('/instances/'+encodeURIComponent(id)+'/variables'+this.query({afterKey,expectedLockVersion,limit:'50'}),signal);}
 assignedTasks(cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<Page<TaskView>>('/tasks/mine'+this.query({afterId:cursor,limit:String(limit)}),signal);}
 instanceTasks(id:string,cursor:string|undefined,limit:number,signal:AbortSignal){return this.read<Page<TaskView>>('/instances/'+encodeURIComponent(id)+'/tasks'+this.query({afterId:cursor,limit:String(limit)}),signal);}
 task(id:string,signal:AbortSignal){return this.read<TaskView>('/tasks/'+encodeURIComponent(id),signal);}
 async taskContract(id:string,signal:AbortSignal){
  const {value,objectAt}=await this.readExact<TaskContract>('/tasks/'+encodeURIComponent(id)+'/contract',signal);
  const contract=value.resultContract;
  if(contract&&contract.mode!=='FORBIDDEN')contract.schemaJson=objectAt(['resultContract','schema']);
  return value;
 }
 async taskSubmission(id:string,signal:AbortSignal){
  const {value,objectAt}=await this.readExact<WireSubmission|null>('/tasks/'+encodeURIComponent(id)+'/submission',signal);
  return value&&exactSubmission(value,objectAt(['result']));
 }
 activity(id:string,signal:AbortSignal){return this.read<ActivityView>('/activities/'+encodeURIComponent(id),signal);}
 async submissions(id:string,cursor:string|undefined,limit:number,signal:AbortSignal):Promise<SubmissionPage>{
  const path='/activities/'+encodeURIComponent(id)+'/submissions'+this.query({afterTaskId:cursor,limit:String(limit)});
  const {value,objectAt}=await this.readExact<Omit<SubmissionPage,'items'>&{items:WireSubmission[]}>(path,signal);
  return {...value,items:value.items.map((row,index)=>exactSubmission(row,objectAt(['items',index,'result'])))};
 }
}

type WireSubmission=TaskSubmission&{result?:object};
function exactSubmission(row:WireSubmission,resultJson:string|undefined):TaskSubmission {
 delete row.result;
 if(resultJson!==undefined)row.resultJson=resultJson;
 return row;
}
