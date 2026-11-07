import { InjectionToken } from '@angular/core';
import type { UrlTree } from '@angular/router';
import type { InstanceView, InstanceGraph, HistoryPage, HistoryEvent } from './models';

/** Host transport; no root HTTP providers or private response caching are installed. */
export interface WorkflowViewerReads {
  instance(id: string, signal: AbortSignal): Promise<InstanceView>;
  history(id: string, afterSequence: string, limit: number, signal: AbortSignal): Promise<HistoryPage>;
  historyEvent(id: string, sequence: string, signal: AbortSignal): Promise<HistoryEvent>;
  graph(id: string, signal: AbortSignal): Promise<InstanceGraph>;
}
export const WORKFLOW_VIEWER_READS = new InjectionToken<WorkflowViewerReads>('Workflow viewer reads');
export type ViewerDestination = { kind: 'instance' | 'history'; instanceId: string }
  | { kind: 'event'; instanceId: string; sequence: string }
  | { kind: 'task' | 'activity'; id: string };
export interface WorkflowViewerNavigation { destination(intent: ViewerDestination): UrlTree | null; }
export const WORKFLOW_VIEWER_NAVIGATION = new InjectionToken<WorkflowViewerNavigation>('Workflow viewer navigation');
export type ViewerReadCategory = 'CANCELLED' | 'UNAUTHENTICATED' | 'NOT_FOUND' | 'LIMIT_EXCEEDED' | 'FAILED';
export class ViewerReadError extends Error {
  constructor(public readonly category: ViewerReadCategory, public readonly serverCode?: string, public readonly correlationId?: string) { super(category); }
}
/** For host adapters only. No server message or command-recovery text is displayed. */
export function normalizeViewerReadError(error: unknown): ViewerReadError {
  if (error instanceof ViewerReadError) return error;
  const e = error as { name?: string; httpStatus?: number; code?: string; correlationId?: string } | null;
  const category = e?.name === 'AbortError' ? 'CANCELLED' : e?.httpStatus === 401 ? 'UNAUTHENTICATED'
    : e?.httpStatus === 403 || e?.httpStatus === 404 ? 'NOT_FOUND' : e?.code === 'LIMIT_EXCEEDED' ? 'LIMIT_EXCEEDED' : 'FAILED';
  return new ViewerReadError(category, e?.code, e?.correlationId);
}
export const DEFAULT_VIEWER_MESSAGES = {
  instance: 'Workflow instance', history: 'Ordered history', event: 'History event', graph: 'Workflow graph',
  currentPosition: 'Current position', result: 'Result', notFinished: 'Not finished', token: 'Instance token', lastSequence: 'Last history sequence',
  refresh: 'Refresh current state', loading: 'Loading…', empty: 'No events on this page.', next: 'Next page', pageSize: 'Page size',
  missing: 'Resource missing or unavailable.', unauthenticated: 'Sign in to view this resource.', failed: 'Read failed. Refresh to try again.',
  limit: 'Read limit exceeded.', unavailable: 'Workflow graph unavailable', details: 'Exact persisted evidence', download: 'Download exact details JSON',
  current: 'Current step', completed: 'Completed', finalPosition: 'Completed · final position', pending: 'Not reached',
  legend: 'Workflow graph legend', zoomControls: 'Graph zoom controls', zoomOut: 'Zoom out', zoomIn: 'Zoom in', resetZoom: 'Reset zoom',
  explore: 'Workflow graph. Scroll horizontally or vertically to explore all steps.', pinned: 'Pinned workflow graph',
  description: 'Read-only pinned graph. Arrows show direction; green arrows were traversed. Select a recorded link to navigate.',
  traversed: 'Traversed', notTraversed: 'Not traversed', steps: 'Workflow steps and recorded links', activity: 'Activity', task: 'Task',
  recordedThrough: 'Recorded through history sequence', refreshHint: 'Refresh to read the latest state.',
  sequence: 'Sequence', type: 'Event type', actor: 'Actor', occurred: 'Occurred at', open: 'Open',
  routing: 'Exclusive route', selected: 'Selected', defaultRoute: 'Default', noReads: 'No variables read.',
  routingHint: 'Only evaluated conditions and actual reads are recorded.', shortCircuit: 'Evaluation stopped at the first true condition.',
  defaultHint: 'Default selected after the conditions; no condition returned true.'
};
export type WorkflowViewerMessages = typeof DEFAULT_VIEWER_MESSAGES;
