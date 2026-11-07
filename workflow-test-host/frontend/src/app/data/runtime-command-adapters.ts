import { DestroyRef, Injectable, inject } from '@angular/core';
import { CommandRecovery } from '@workflow-engine/ui';
import type { WorkflowRuntimeCommands, StartEnvelopeAdapter, Envelope, CommandResult, ReplayResult } from '@workflow-engine/ui';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConsoleSession } from './console-session';
import { commandRequest, freezeStartBody } from '../command-buffer';
@Injectable({providedIn:'root'})
export class ConsoleRuntimeCommands implements WorkflowRuntimeCommands {
 private readonly session=inject(ConsoleSession);
 readonly start:StartEnvelopeAdapter={freezeBody:freezeStartBody,
  execute:(envelope,signal)=>this.dispatch<CommandResult>(envelope,false,signal),
  replay:(envelope,signal)=>this.dispatch<ReplayResult>(envelope,true,signal)};
 complete(envelope:Envelope,signal:AbortSignal){return this.dispatch<CommandResult>(envelope,false,signal);}
 replayComplete(envelope:Envelope,signal:AbortSignal){return this.dispatch<ReplayResult>(envelope,true,signal);}
 private dispatch<T>(envelope:Envelope,replay:boolean,signal:AbortSignal):Promise<T>{
  const request=commandRequest(envelope,replay);
  return this.session.runtimeCommand<T>(request.path,request.body,request.token,envelope.kind==='start',signal);
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
