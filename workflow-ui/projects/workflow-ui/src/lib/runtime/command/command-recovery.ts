import { Injectable, inject, signal } from '@angular/core';
import { WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_CANCELLATION, WORKFLOW_TASK_REASSIGNMENT } from '../contracts';
import { CommandBuffer } from './command-buffer';
import type { Observation, Execution, CancellationExecution } from '../models';
/** In-memory session state. Route destruction does not discard immutable requests. */
@Injectable({providedIn:'root'})
export class CommandRecovery {
 private readonly transport=inject(WORKFLOW_RUNTIME_COMMANDS,{optional:true});
 private readonly cancellation=inject(WORKFLOW_CANCELLATION,{optional:true});
 private readonly reassignment=inject(WORKFLOW_TASK_REASSIGNMENT,{optional:true});
 readonly buffer=new CommandBuffer(this.transport?.start);
 readonly result=signal<Observation|null>(null);
 readonly observations=new Map<string,Observation>();
 readonly pendingIds=new Set<string>();
 private readonly running=signal(new Set<string>());
 get runningIds(){return this.running();}
 private scope:object|null=null;
 private generation=0;
 setScope(scope:object|null){if(scope!==this.scope){this.clear();this.scope=scope;}}
 clear(){this.generation++;this.buffer.clear();this.observations.clear();this.pendingIds.clear();this.running.set(new Set());this.result.set(null);}
 select(id:string){this.buffer.select(id);this.result.set(this.observations.get(id)||null);}
 restore(text:string){if(!this.scope)throw new Error('No current session');const e=this.buffer.restore(text);this.result.set(this.observations.get(e.body['commandId']!)||null);}
 execution():Execution|CancellationExecution|null {
  const r=this.result()?.result;
  const settled=r&&'found' in r?r.result:r;
  return settled&&'execution' in settled?settled.execution:null;
 }
 taskResult(){
  const r=this.result()?.result;
  const settled=r&&'found' in r?r.result:r;
  return settled&&'taskStatus' in settled?settled:null;
 }
 async execute(replay:boolean,signal=new AbortController().signal){
  const envelope=this.buffer.command;
  if(!envelope||!this.scope)throw new Error('No command selected in a current session');
  if(envelope.kind==='reassign'?!this.reassignment:envelope.kind==='cancel'?!this.cancellation:!this.transport)throw new Error('Host command adapter is required');
  const generation=this.generation,id=envelope.body['commandId']!;
  const wasUncertain=this.pendingIds.has(id);
  if(this.runningIds.has(id))throw new Error('Command is already pending');
  this.running.update(ids=>new Set(ids).add(id));
  const tracksPending=envelope.kind!=='start';
  if(tracksPending&&!replay)this.pendingIds.add(id);
  this.result.set(this.observations.get(id)||null);
  try {
   const result=envelope.kind==='reassign'
    ? await this.reassignment!.reassign(envelope,replay,signal)
    : envelope.kind==='cancel'
    ? await (replay?this.cancellation!.replay(envelope,signal):this.cancellation!.cancel(envelope,signal))
    : envelope.kind==='start'
    ? await (replay?this.transport!.start!.replay(envelope,signal):this.transport!.start!.execute(envelope,signal))
    : envelope.kind==='complete'
     ? await (replay?this.transport!.replayComplete(envelope,signal):this.transport!.complete(envelope,signal))
     : await (this.transport!.ownership?.(envelope,replay,signal) ?? Promise.reject(new Error('Host ownership transport is required')));
   if(generation===this.generation&&tracksPending){
    if(!replay||'found' in result&&result.found)this.pendingIds.delete(id);
   }
   this.record(generation,id,{outcome:replay?'REPLAY_ONLY_LOOKUP':'replayed' in result&&result.replayed?'REPLAYED_ORIGINAL_RESULT':'NEW_COMMITTED_RESULT',result});return result;
  } catch(error) {
   const failure=error as {httpStatus?:number;code?:string};
   const status=failure?.httpStatus;
   const rejected=status&&status<500||status===503&&failure?.code==='RESULT_VALIDATION_FAILED';
   if(generation===this.generation&&tracksPending&&!rejected)this.pendingIds.add(id);
   // Task mutation routing rechecks the receipt before returning a definitive stale version.
   const staleTaskRetry=envelope.kind!=='cancel'&&status===409&&failure.code==='STALE_VERSION';
   if(generation===this.generation&&tracksPending&&rejected&&!replay&&(!wasUncertain||staleTaskRetry))this.pendingIds.delete(id);
   this.record(generation,id,{outcome:rejected?'REJECTED':'UNKNOWN_OUTCOME',message:String(error)});throw error;
  } finally {
   if(generation===this.generation){
    const remaining=new Set(this.runningIds);
    remaining.delete(id);
    this.running.set(remaining);
   }
  }
 }
 private record(generation:number,id:string,observation:Observation){
  if(generation!==this.generation||!this.scope)return;
  this.observations.set(id,observation);
  if(this.buffer.command?.body['commandId']===id)this.result.set(observation);
 }
}
