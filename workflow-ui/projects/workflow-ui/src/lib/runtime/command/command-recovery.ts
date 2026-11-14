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
 readonly runningIds=new Set<string>();
 private scope:object|null=null;
 private generation=0;
 setScope(scope:object|null){if(scope!==this.scope){this.clear();this.scope=scope;}}
 clear(){this.generation++;this.buffer.clear();this.observations.clear();this.pendingIds.clear();this.runningIds.clear();this.result.set(null);}
 select(id:string){this.buffer.select(id);this.result.set(this.observations.get(id)||null);}
 restore(text:string){if(!this.scope)throw new Error('No current session');const e=this.buffer.restore(text);this.result.set(this.observations.get(e.body['commandId']!)||null);}
 execution():Execution|CancellationExecution|null {const r=this.result()?.result;return !r?null:'execution' in r?r.execution:r.result?.execution||null;}
 async execute(replay:boolean,signal=new AbortController().signal){
  const envelope=this.buffer.command;
  if(!envelope||!this.scope)throw new Error('No command selected in a current session');
  if(envelope.kind==='reassign'?!this.reassignment:envelope.kind==='cancel'?!this.cancellation:!this.transport)throw new Error('Host command adapter is required');
  const generation=this.generation,id=envelope.body['commandId']!;
  const wasUncertain=this.pendingIds.has(id);
  if(this.runningIds.has(id))throw new Error('Command is already pending');
  this.runningIds.add(id);
  if((envelope.kind==='cancel'||envelope.kind==='reassign')&&!replay)this.pendingIds.add(id);
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
   if(generation===this.generation&&(envelope.kind==='cancel'||envelope.kind==='reassign')){
    if(!replay||'found' in result&&result.found)this.pendingIds.delete(id);
   }
   this.record(generation,id,{outcome:replay?'REPLAY_ONLY_LOOKUP':'replayed' in result&&result.replayed?'REPLAYED_ORIGINAL_RESULT':'NEW_COMMITTED_RESULT',result});return result;
  } catch(error) {
   const failure=error as {httpStatus?:number;code?:string};
   const status=failure?.httpStatus;
   const rejected=status&&status<500||status===503&&failure?.code==='RESULT_VALIDATION_FAILED';
   if(generation===this.generation&&(envelope.kind==='cancel'||envelope.kind==='reassign')&&!rejected)this.pendingIds.add(id);
   if(generation===this.generation&&(envelope.kind==='cancel'||envelope.kind==='reassign')&&rejected&&!replay&&!wasUncertain)this.pendingIds.delete(id);
   this.record(generation,id,{outcome:rejected?'REJECTED':'UNKNOWN_OUTCOME',message:String(error)});throw error;
  } finally {
   if(generation===this.generation)this.runningIds.delete(id);
  }
 }
 private record(generation:number,id:string,observation:Observation){
  if(generation!==this.generation||!this.scope)return;
  this.observations.set(id,observation);
  if(this.buffer.command?.body['commandId']===id)this.result.set(observation);
 }
}
