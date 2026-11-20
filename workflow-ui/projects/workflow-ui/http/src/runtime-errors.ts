import { WorkflowHttpError, workflowErrorBody } from './transport';

export function runtimeRead<T>(response: Promise<T>): Promise<T> {
  return response.catch(error => {
    if (!(error instanceof WorkflowHttpError)) throw error;
    const { code, correlationId, diagnostics } = workflowErrorBody(error.body);
    const message = error.status >= 200 && error.status < 300 ? 'Invalid read response.' : 'Read failed.';
    throw Object.assign(new Error(message), { httpStatus: error.status, code, correlationId, diagnostics });
  });
}

type FieldError = { path: string; message: string };

function fieldErrors(items: unknown[]): FieldError[] {
  return items.flatMap(item => {
    if (!item || typeof item !== 'object') return [];
    const { path, message } = item as Partial<FieldError>;
    return typeof path === 'string' && typeof message === 'string' && message ? [{ path, message }] : [];
  });
}

function unreadableCommandResponse(status: number): Error {
  // A successful HTTP status alone is not sufficient evidence of a committed command.
  return Object.assign(new Error('Response unavailable. Command outcome may be unknown; use Find result.'), { status });
}

export function runtimeCommandFailure(error: unknown, taskCommand: boolean): never {
  if (!(error instanceof WorkflowHttpError)) throw error;
  const { status, body } = error;
  if (status === 0) {
    throw Object.assign(new Error('Transport outcome unknown. Keep the original command and use Find result or Retry.'), {
      httpStatus: status,
    });
  }
  if (status >= 200 && status < 300) throw unreadableCommandResponse(status);

  const failure = workflowErrorBody(body);
  const resultFailure = failure.code?.startsWith('RESULT_');
  const message = taskCommand
    ? failure.message && failure.message !== failure.code ? failure.message
      : resultFailure ? 'Check the result and try again.' : 'The task action could not be completed.'
    : `${status} ${failure.code || 'REQUEST_FAILED'} ${JSON.stringify(failure.diagnostics || [])} [${failure.correlationId || ''}]`;
  throw Object.assign(new Error(message), {
    httpStatus: status,
    code: failure.code,
    diagnostics: failure.diagnostics,
    correlationId: failure.correlationId,
    fieldErrors: fieldErrors(failure.fieldErrors ?? failure.diagnostics ?? []),
  });
}
