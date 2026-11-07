import { Directive, Input, OnChanges, OnDestroy, inject, signal } from '@angular/core';
import { WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION, DEFAULT_VIEWER_MESSAGES, normalizeViewerReadError } from './contracts';
import type { WorkflowViewerMessages, ViewerReadError } from './contracts';

/** Shared lifecycle for the three request-owning components, with no shared response state. */
@Directive()
export abstract class ViewerReadState implements OnChanges, OnDestroy {
  @Input() instanceId = '';
  @Input() sessionScope: unknown = null;
  @Input() messages: WorkflowViewerMessages = DEFAULT_VIEWER_MESSAGES;
  readonly reads = inject(WORKFLOW_VIEWER_READS);
  readonly navigation = inject(WORKFLOW_VIEWER_NAVIGATION, {optional: true});
  readonly busy = signal(false);
  readonly error = signal<ViewerReadError | null>(null);
  private generation = 0;
  private controller?: AbortController;
  private destroyed = false;
  abstract clear(): void;
  abstract load(): Promise<void>;
  ngOnChanges(changes: Record<string, unknown>): void {
    if ('instanceId' in changes || 'sessionScope' in changes || 'sequence' in changes) {
      this.invalidate(); this.clear(); this.error.set(null);
      if (this.sessionScope != null && this.instanceId) void this.load();
    }
  }
  protected begin() {
    this.invalidate(); this.error.set(null); this.clear();
    if (this.destroyed || this.sessionScope == null || !this.instanceId) return null;
    const generation = this.generation, id = this.instanceId, scope = this.sessionScope;
    const controller = this.controller = new AbortController();
    this.busy.set(true);
    return { id, signal: controller.signal,
      current: () => !this.destroyed && generation === this.generation && id === this.instanceId && scope === this.sessionScope };
  }
  protected fail(error: unknown): void { this.clear(); const normalized = normalizeViewerReadError(error); if (normalized.category !== 'CANCELLED') this.error.set(normalized); }
  errorText(): string {
    switch (this.error()?.category) {
      case 'UNAUTHENTICATED': return this.messages.unauthenticated;
      case 'NOT_FOUND': return this.messages.missing;
      case 'LIMIT_EXCEEDED': return this.messages.limit;
      default: return this.error() ? this.messages.failed : '';
    }
  }
  private invalidate() { this.generation++; this.controller?.abort(); this.busy.set(false); }
  ngOnDestroy() { this.destroyed = true; this.invalidate(); this.clear(); }
}
