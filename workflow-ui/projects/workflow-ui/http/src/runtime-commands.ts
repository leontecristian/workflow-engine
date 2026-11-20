import { freezeResultJson } from '@workflow-engine/ui';
import type {
  CancellationReplay, CancellationResult, CommandBody, Envelope, TaskCommandReplay, TaskCommandResult,
  WorkflowCancellation, WorkflowRuntimeCommands, WorkflowTaskReassignment,
} from '@workflow-engine/ui';
import { runtimeCommandFailure, runtimeRead } from './runtime-errors';
import { workflowUrl, type WorkflowHttpTransport } from './transport';

type StandardCommand = Exclude<Envelope['kind'], 'start'>;
type OwnershipCommand = 'claim' | 'release' | 'admin-release';
type Receipt<T> = { found: boolean; result: T | null };
type Decoder<T> = (value: unknown) => value is T;

function commandBody(kind: StandardCommand, fields: CommandBody): string {
  if (kind === 'reassign') {
    const { assignmentKind, assignmentRef, ...body } = fields;
    return JSON.stringify({ ...body, assignment: { kind: assignmentKind, ref: assignmentRef } });
  }
  if (kind === 'complete' && Object.hasOwn(fields, 'resultJson')) {
    const { resultJson, ...body } = fields;
    return JSON.stringify(body).slice(0, -1) + ',"result":' + freezeResultJson(resultJson) + '}';
  }
  return JSON.stringify(fields);
}

function isObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isTaskResult(value: unknown): value is TaskCommandResult {
  return isObject(value) && typeof value['replayed'] === 'boolean'
    && ['ACTIVE', 'COMPLETED', 'CANCELLED'].some(status => status === value['status'])
    && ['OPEN', 'COMPLETED', 'CANCELLED'].some(status => status === value['taskStatus'])
    && (value['collectivePending'] === undefined || typeof value['collectivePending'] === 'boolean')
    && (value['nextTaskId'] === undefined || typeof value['nextTaskId'] === 'string');
}

function isCancellationResult(value: unknown): value is CancellationResult {
  if (!isObject(value) || typeof value['replayed'] !== 'boolean') return false;
  const execution = value['execution'];
  if (!isObject(execution) || execution['operation'] !== 'CANCEL_WORKFLOW') return false;
  const { instance, activity } = execution;
  return typeof execution['commandId'] === 'string'
    && isObject(instance) && typeof instance['id'] === 'string' && typeof instance['lockVersion'] === 'string'
    && instance['status'] === 'CANCELLED' && typeof instance['endedAt'] === 'string'
    && isObject(activity) && typeof activity['id'] === 'string' && activity['status'] === 'CANCELLED'
    && Number.isInteger(execution['cancelledTaskCount'])
    && typeof execution['firstHistorySequence'] === 'string' && typeof execution['lastHistorySequence'] === 'string';
}

function isReceipt<T>(value: unknown, acceptsResult: Decoder<T>): value is Receipt<T> {
  return isObject(value) && (value['found'] === true && acceptsResult(value['result'])
    || value['found'] === false && value['result'] === null);
}

async function dispatch<T>(
  transport: WorkflowHttpTransport, basePath: string, kind: StandardCommand, envelope: Envelope,
  signal: AbortSignal, replay: boolean, accepts: Decoder<T>,
): Promise<T> {
  if (envelope.kind !== kind) throw new Error('Command envelope does not match the requested operation.');
  const cancelling = kind === 'cancel';
  const idField = cancelling ? 'instanceId' : 'taskId';
  const { [idField]: id, ...mutationFields } = envelope.body;
  const path = replay ? `/commands/${kind}/replay`
    : `/${cancelling ? 'instances' : 'tasks'}/${encodeURIComponent(id!)}/${kind}`;
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (envelope.expectedToken !== null) {
    headers[cancelling ? 'X-Expected-Lock-Version' : 'X-Task-Precondition'] = envelope.expectedToken;
  }
  const body = commandBody(kind, replay ? envelope.body : mutationFields);
  return transport.json<T>(workflowUrl(basePath, path), { method: 'POST', body, headers, signal }, accepts)
    .catch(error => runtimeCommandFailure(error, !cancelling));
}

const isTaskReceipt: Decoder<TaskCommandReplay> = (value): value is TaskCommandReplay => isReceipt(value, isTaskResult);
const isCancellationReceipt: Decoder<CancellationReplay> =
  (value): value is CancellationReplay => isReceipt(value, isCancellationResult);

function taskAction(
  transport: WorkflowHttpTransport, basePath: string, kind: OwnershipCommand | 'reassign',
  envelope: Envelope, replay: boolean, signal: AbortSignal,
): Promise<TaskCommandResult | TaskCommandReplay> {
  return replay ? dispatch(transport, basePath, kind, envelope, signal, true, isTaskReceipt)
    : dispatch(transport, basePath, kind, envelope, signal, false, isTaskResult);
}

export function createWorkflowRuntimeCommandsHttp(transport: WorkflowHttpTransport, basePath: string): WorkflowRuntimeCommands {
  return {
    complete: (envelope, signal) => dispatch(transport, basePath, 'complete', envelope, signal, false, isTaskResult),
    replayComplete: (envelope, signal) => dispatch(transport, basePath, 'complete', envelope, signal, true, isTaskReceipt),
    async ownership(envelope, replay, signal) {
      const kind = envelope.kind;
      if (kind !== 'claim' && kind !== 'release' && kind !== 'admin-release') {
        throw new Error('Command envelope does not match a task ownership operation.');
      }
      return taskAction(transport, basePath, kind, envelope, replay, signal);
    },
  };
}

export function createWorkflowCancellationHttp(
  transport: WorkflowHttpTransport, readBasePath: string, commandBasePath = readBasePath,
): WorkflowCancellation {
  return {
    availability: (id, signal) => runtimeRead(transport.json(workflowUrl(readBasePath,
      `/instances/${encodeURIComponent(id)}/cancellation-availability`), { signal })),
    cancel: (envelope, signal) => dispatch(transport, commandBasePath, 'cancel', envelope, signal, false, isCancellationResult),
    replay: (envelope, signal) => dispatch(transport, commandBasePath, 'cancel', envelope, signal, true, isCancellationReceipt),
  };
}

export function createWorkflowTaskReassignmentHttp(
  transport: WorkflowHttpTransport, readBasePath: string, commandBasePath = readBasePath,
): WorkflowTaskReassignment {
  const taskUrl = (taskId: string) => `/tasks/${encodeURIComponent(taskId)}`;
  return {
    availability: (taskId, signal) => runtimeRead(transport.json(workflowUrl(readBasePath,
      `${taskUrl(taskId)}/reassignment-availability`), { signal })),
    targets: (taskId, kind, query, cursor, limit, signal) => runtimeRead(transport.json(workflowUrl(readBasePath,
      `${taskUrl(taskId)}/reassignment-targets`, { kind, query, cursor, limit }), { signal })),
    reassign: (envelope, replay, signal) => taskAction(transport, commandBasePath, 'reassign', envelope, replay, signal),
  };
}
