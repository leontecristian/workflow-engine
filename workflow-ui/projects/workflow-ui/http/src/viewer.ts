import { normalizeViewerReadError } from '@workflow-engine/ui';
import type { HistoryEvent, HistoryPage, InstanceGraph, InstanceView, WorkflowViewerReads } from '@workflow-engine/ui';
import { WorkflowHttpError, workflowErrorBody, workflowUrl } from './transport';
import type { WorkflowHttpTransport } from './transport';

export function createWorkflowViewerHttp(transport: WorkflowHttpTransport, basePath: string): WorkflowViewerReads {
  const instancePath = (id: string) => '/instances/' + encodeURIComponent(id);

  async function read<T>(path: string, signal: AbortSignal, query?: Parameters<typeof workflowUrl>[2]): Promise<T> {
    try {
      const response = await transport.json<T>(workflowUrl(basePath, path, query), { signal });
      if (response === null || typeof response !== 'object' || Array.isArray(response)) {
        throw new Error('Invalid read response.');
      }
      return response;
    } catch (error) {
      if (!(error instanceof WorkflowHttpError)) throw normalizeViewerReadError(error);
      const { code, correlationId, diagnostics } = workflowErrorBody(error.body);
      const httpStatus = error.status;
      throw Object.assign(normalizeViewerReadError({ httpStatus, code, correlationId }), { httpStatus, diagnostics });
    }
  }

  return {
    instance(id, signal) {
      return read<InstanceView>(instancePath(id), signal);
    },
    history(id, cursor, limit, signal) {
      return read<HistoryPage>(instancePath(id) + '/history', signal, { afterSequence: cursor, limit });
    },
    historyEvent(id, eventRef, signal) {
      return read<HistoryEvent>(instancePath(id) + '/history/' + encodeURIComponent(eventRef), signal);
    },
    graph(id, signal) {
      return read<InstanceGraph>(instancePath(id) + '/graph', signal);
    },
  };
}
