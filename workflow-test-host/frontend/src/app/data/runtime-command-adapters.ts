import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommandRecovery, type CommandResult, type ReplayResult, type StartEnvelopeAdapter } from '@workflow-engine/ui';
import { createWorkflowRuntimeCommandsHttp } from '@workflow-engine/ui/http';
import { commandRequest, freezeStartBody } from '../command-buffer';
import { ConsoleSession } from './console-session';
import { CONSOLE_WORKFLOW_API } from './workflow-http-session';

export function consoleRuntimeCommands() {
  const session = inject(ConsoleSession);
  const commands = createWorkflowRuntimeCommandsHttp(inject(HttpClient), CONSOLE_WORKFLOW_API);
  const start: StartEnvelopeAdapter = {
    freezeBody: freezeStartBody,
    execute(envelope, signal) {
      const { path, body } = commandRequest(envelope, false);
      return session.startCommand<CommandResult>(path, body, signal);
    },
    replay(envelope, signal) {
      const { path, body } = commandRequest(envelope, true);
      return session.startCommand<ReplayResult>(path, body, signal);
    },
  };
  return { ...commands, start };
}

/** Scope identity, rather than actor name, defines private state lifetime. */
@Injectable({providedIn:'root'})
export class ConsoleRuntimeSessionBridge {
  private readonly session = inject(ConsoleSession);
  private readonly recovery = inject(CommandRecovery);

  constructor() {
    this.recovery.setScope(this.session.viewerScope());
    this.session.identityChanges.pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.recovery.setScope(this.session.viewerScope()));
  }
}
