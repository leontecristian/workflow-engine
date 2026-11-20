import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap } from 'rxjs';
import { ConsoleSession } from './console-session';

export const CONSOLE_WORKFLOW_API = '/console/api';

export const consoleWorkflowSessionInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(CONSOLE_WORKFLOW_API + '/')) return next(request);

  const session = inject(ConsoleSession);
  const scope = session.viewerScope();
  const identity = session.session();
  const outgoing = request.method === 'GET' || !identity ? request : request.clone({
    setHeaders: { [identity.csrfHeaderName]: identity.csrfToken },
  });
  return next(outgoing).pipe(tap({
    error(error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 401 && session.viewerScope() === scope) {
        session.clear();
      }
    },
  }));
};
