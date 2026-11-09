import { Injectable, inject, signal } from '@angular/core';
import { WORKFLOW_RUNTIME_COMMANDS } from '../contracts';
import { CommandBuffer } from './command-buffer';
import type { Observation, Execution } from '../models';
/** In-memory session state. Route destruction does not discard immutable requests. */
@Injectable({providedIn:'root'})
export class CommandRecovery {
 private readonly transport=inject(WORKFLOW_RUNTIME_COMMANDS,{optional:true});
 readonly buffer=new CommandBuffer(this.transport?.start);
 readonly result=signal<Observation|null>(null);
 readonly observations=new Map<string,Observation>();
 private scope:object|null=null;
 private generation=0;
 setScope(scope:object|null){if(scope!==this.scope){this.clear();this.scope=scope;}}
 clear(){this.generation++;this.buffer.clear();this.observations.clear();this.result.set(null);}
 select(id:string){this.buffer.select(id);this.result.set(this.observations.get(id)||null);}
 restore(text:string){if(!this.scope)throw new Error('No current session');const e=this.buffer.restore(text);this.result.set(this.observations.get(e.body['commandId']!)||null);}
 execution():Execution|null {const r=this.result()?.result;return !r?null:'execution' in r?r.execution:r.result?.execution||null;}
 async execute(replay:boolean,signal=new AbortController().signal){
  const envelope=this.buffer.command;
  if(!envelope||!this.scope)throw new Error('No command selected in a current session');
  if(!this.transport)throw new Error('Host command adapter is required');
  const generation=this.generation,id=envelope.body['commandId']!;
  this.result.set(this.observations.get(id)||null);
  try {
   const result=envelope.kind==='start'
    ? await (replay?this.transport.start!.replay(envelope,signal):this.transport.start!.execute(envelope,signal))
    : envelope.kind==='complete'
     ? await (replay?this.transport.replayComplete(envelope,signal):this.transport.complete(envelope,signal))
     : await (this.transport.ownership?.(envelope,replay,signal) ?? Promise.reject(new Error('Host ownership transport is required')));
   this.record(generation,id,{outcome:replay?'REPLAY_ONLY_LOOKUP':'replayed' in result&&result.replayed?'REPLAYED_ORIGINAL_RESULT':'NEW_COMMITTED_RESULT',result});return result;
  } catch(error) {
   const status=(error as {httpStatus?:number})?.httpStatus;
   this.record(generation,id,{outcome:status&&status<500?'REJECTED':'UNKNOWN_OUTCOME',message:String(error)});throw error;
  }
 }
 private record(generation:number,id:string,observation:Observation){
  if(generation!==this.generation||!this.scope)return;
  this.observations.set(id,observation);
  if(this.buffer.command?.body['commandId']===id)this.result.set(observation);
 }
}
