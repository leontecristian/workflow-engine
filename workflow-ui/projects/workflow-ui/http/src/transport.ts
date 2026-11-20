import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscriber } from 'rxjs';

interface RequestOptions {
  method?: string;
  body?: string;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

export class WorkflowHttpError extends Error {
  constructor(readonly status: number, readonly body: unknown) {
    super('Workflow HTTP request failed');
  }
}

export function workflowErrorBody(body: unknown) {
  const fields = body as Record<string, unknown> | null | undefined;
  return {
    code: typeof fields?.['code'] === 'string' ? fields['code'] : undefined,
    message: typeof fields?.['message'] === 'string' ? fields['message'] : undefined,
    correlationId: typeof fields?.['correlationId'] === 'string' ? fields['correlationId'] : undefined,
    diagnostics: Array.isArray(fields?.['diagnostics']) ? fields['diagnostics'] as unknown[] : undefined,
    fieldErrors: Array.isArray(fields?.['fieldErrors']) ? fields['fieldErrors'] as unknown[] : undefined,
  };
}

/** Paths contain encoded segments. Supply unencoded query values. */
export function workflowUrl(
  basePath: string,
  path = '',
  query: Record<string, string | number | boolean | null | undefined> = {},
): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null) params.set(key, String(value));
  }
  const url = basePath.replace(/\/+$/, '') + '/' + path.replace(/^\/+/, '');
  const search = params.toString();
  return search ? url + '?' + search : url;
}

export class WorkflowHttpTransport {
  constructor(private readonly client: HttpClient) {}

  async text(url: string, options?: RequestOptions): Promise<string> {
    return (await this.request(url, options)).body ?? '';
  }

  async json<T>(url: string, options?: RequestOptions, valid?: (value: unknown) => boolean): Promise<T> {
    const response = await this.request(url, options);
    let value: unknown;
    try {
      value = JSON.parse(response.body ?? '');
    } catch {
      throw new WorkflowHttpError(response.status, undefined);
    }
    if (valid && !valid(value)) throw new WorkflowHttpError(response.status, undefined);
    return value as T;
  }

  private request(url: string, options: RequestOptions = {}): Promise<HttpResponse<string>> {
    const { method = 'GET', body, headers, signal } = options;
    const cancelled = () => new DOMException('Aborted', 'AbortError');
    return new Promise((resolve, reject) => {
      if (signal?.aborted) {
        reject(cancelled());
        return;
      }

      // Create the Subscriber before starting HTTP, so even synchronous aborts can unsubscribe.
      const subscriber = new Subscriber<HttpResponse<string>>({
        next(response) {
          resolve(response);
          subscriber.unsubscribe();
        },
        error(error: unknown) {
          reject(httpError(error));
        },
        complete() {
          reject(new WorkflowHttpError(0, undefined));
        },
      });
      const abort = () => {
        reject(cancelled());
        subscriber.unsubscribe();
      };
      signal?.addEventListener('abort', abort, { once: true });
      subscriber.add(() => signal?.removeEventListener('abort', abort));

      try {
        this.client.request(method, url, {
          body,
          headers,
          observe: 'response',
          responseType: 'text',
        }).subscribe(subscriber);
      } catch (error) {
        subscriber.error(error);
      }
    });
  }
}

function httpError(error: unknown): WorkflowHttpError {
  if (!(error instanceof HttpErrorResponse)) return new WorkflowHttpError(0, undefined);
  let body: unknown = error.error;
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body);
    } catch {
      // Keep the status even when a gateway or interceptor returns non-JSON.
    }
  }
  return new WorkflowHttpError(error.status, body);
}
