import { HttpClient } from '@angular/common/http';
import { inject, type InjectionToken, type Provider } from '@angular/core';
import {
  WORKFLOW_ADMINISTRATION_API,
  WORKFLOW_CANCELLATION,
  WORKFLOW_RUNTIME_COMMANDS,
  WORKFLOW_RUNTIME_READS,
  WORKFLOW_TASK_REASSIGNMENT,
  WORKFLOW_VIEWER_READS,
} from '@workflow-engine/ui';
import { createWorkflowAdministrationHttp } from './administration';
import { createWorkflowViewerHttp as createViewer } from './viewer';
import { createWorkflowRuntimeReadsHttp } from './runtime-reads';
import {
  createWorkflowCancellationHttp,
  createWorkflowRuntimeCommandsHttp as createCommands,
  createWorkflowTaskReassignmentHttp,
} from './runtime-commands';
import { WorkflowHttpTransport } from './transport';

function bind<T>(token: InjectionToken<T>, create: (transport: WorkflowHttpTransport) => T): Provider {
  return {
    provide: token,
    useFactory: () => create(new WorkflowHttpTransport(inject(HttpClient))),
  };
}

export function provideWorkflowAdministrationHttp(basePath: string): Provider {
  return bind(WORKFLOW_ADMINISTRATION_API, transport => createWorkflowAdministrationHttp(transport, basePath));
}

export function provideWorkflowViewerHttp(basePath: string): Provider {
  return bind(WORKFLOW_VIEWER_READS, transport => createViewer(transport, basePath));
}

export function provideWorkflowRuntimeReadsHttp(basePath: string): Provider {
  return bind(WORKFLOW_RUNTIME_READS, transport => createWorkflowRuntimeReadsHttp(transport, basePath));
}

export function provideWorkflowRuntimeCommandsHttp(basePath: string): Provider {
  return bind(WORKFLOW_RUNTIME_COMMANDS, transport => createCommands(transport, basePath));
}

export function provideWorkflowCancellationHttp(readBasePath: string, commandBasePath = readBasePath): Provider {
  return bind(WORKFLOW_CANCELLATION, transport => createWorkflowCancellationHttp(transport, readBasePath, commandBasePath));
}

export function provideWorkflowTaskReassignmentHttp(readBasePath: string, commandBasePath = readBasePath): Provider {
  return bind(WORKFLOW_TASK_REASSIGNMENT, transport => createWorkflowTaskReassignmentHttp(transport, readBasePath, commandBasePath));
}

/** Construct the standard viewer reads for a host adapter that supplies its own history. */
export function createWorkflowViewerHttp(client: HttpClient, basePath: string) {
  return createViewer(new WorkflowHttpTransport(client), basePath);
}

/** Construct the standard task commands for a host adapter that supplies business start. */
export function createWorkflowRuntimeCommandsHttp(client: HttpClient, basePath: string) {
  return createCommands(new WorkflowHttpTransport(client), basePath);
}
