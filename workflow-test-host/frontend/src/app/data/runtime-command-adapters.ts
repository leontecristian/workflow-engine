import { DestroyRef, Injectable, inject } from '@angular/core';
import { CommandRecovery } from '@workflow-engine/ui';
import type { WorkflowRuntimeCommands, WorkflowCancellation, CancellationResult, CancellationReplay, StartEnvelopeAdapter, Envelope, CommandResult, ReplayResult, WorkflowReassignmentTargetPage } from '@workflow-engine/ui';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConsoleSession } from './console-session';
import { commandRequest, freezeStartBody } from '../command-buffer';
@Injectable({providedIn:'root'})
export class ConsoleRuntimeCommands implements WorkflowRuntimeCommands, WorkflowCancellation {
 private readonly session=inject(ConsoleSession);
 readonly start:StartEnvelopeAdapter={freezeBody:freezeStartBody,
  execute:(envelope,signal)=>this.dispatch<CommandResult>(envelope,false,signal),
  replay:(envelope,signal)=>this.dispatch<ReplayResult>(envelope,true,signal)};
 complete(envelope:Envelope,signal:AbortSignal){return this.dispatch<CommandResult>(envelope,false,signal);}
 replayComplete(envelope:Envelope,signal:AbortSignal){return this.dispatch<ReplayResult>(envelope,true,signal);}
 reassignmentAvailability(taskId:string,signal:AbortSignal){return this.session.viewerRead<{allowed:boolean;instanceLockVersion:string}>('/tasks/'+encodeURIComponent(taskId)+'/reassignment-availability',signal);}
 targets(taskId:string,kind:'USER'|'GROUP'|'ROLE',query:string,cursor:string|undefined,limit:number,signal:AbortSignal){
  const params=new URLSearchParams({kind,query,limit:String(limit)});if(cursor!==undefined)params.set('cursor',cursor);
  return this.session.viewerRead<WorkflowReassignmentTargetPage>('/tasks/'+encodeURIComponent(taskId)+'/reassignment-targets?'+params,signal);
 }
 reassign(envelope:Envelope,replay:boolean,signal:AbortSignal){return this.dispatch<CommandResult|ReplayResult>(envelope,replay,signal);}
 ownership(envelope:Envelope,replay:boolean,signal:AbortSignal){return this.dispatch<CommandResult | ReplayResult>(envelope,replay,signal);}
 availability(id:string,signal:AbortSignal){return this.session.viewerRead<{allowed:boolean;lockVersion:string}>(`/instances/${encodeURIComponent(id)}/cancellation-availability`,signal);}
 cancel(envelope:Envelope,signal:AbortSignal){return this.dispatch<CancellationResult>(envelope,false,signal);}
 replay(envelope:Envelope,signal:AbortSignal){return this.dispatch<CancellationReplay>(envelope,true,signal);}
 private dispatch<T>(envelope:Envelope,replay:boolean,signal:AbortSignal):Promise<T>{
  const request=commandRequest(envelope,replay);
  return this.session.runtimeCommand<T>(request.path,request.body,request.token,typeof request.body==='string',signal);
 }
}
/** Host session bridge: scope identity, rather than actor name, defines private state lifetime. */
@Injectable({providedIn:'root'})
export class ConsoleRuntimeSessionBridge {
 private readonly session=inject(ConsoleSession);
 private readonly recovery=inject(CommandRecovery);
 constructor(){
  this.recovery.setScope(this.session.viewerScope());
  this.session.identityChanges.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(()=>this.recovery.setScope(this.session.viewerScope()));
 }
}
