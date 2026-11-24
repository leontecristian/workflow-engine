import { Component, DestroyRef, Injector, TemplateRef, computed, effect, inject, linkedSignal, signal, untracked, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { CommandRecovery, prettyDocument, type TaskContract } from '@workflow-engine/ui';
import { WorkflowTaskComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
import { ConsoleTaskDiagnostics, type TaskDiagnostics } from '../../data/task-diagnostics';
import { downloadJson } from '../../ui/download-json';
import type { PageHeaderSource } from '../../shell/page-header';

interface InspectionState {
  open: boolean;
  loading?: boolean;
  failed?: boolean;
  data?: TaskDiagnostics;
  precondition?: string;
}

@Component({
  selector: 'console-task',
  standalone: true,
  imports: [WorkflowTaskComponent, RouterLink, MatButton, DatePipe],
  templateUrl: './task.html',
  styleUrl: './task.scss',
})
export class TaskPage implements PageHeaderSource {
  readonly pageHeaderParent = { label: 'My tasks', path: '/my-tasks' };
  private readonly taskView = viewChild(WorkflowTaskComponent);
  private readonly headerMeta = viewChild<TemplateRef<unknown>>('headerMeta');
  protected readonly headerContract = computed(() => this.taskView()?.contract());
  readonly pageHeader = computed(() => {
    const task = this.headerContract()?.task;
    const meta = this.headerMeta();
    return task && meta ? { title: task.nodeName || task.taskTypeName || 'Task', meta } : null;
  });
  readonly session = inject(ConsoleSession);
  readonly id = signal('');
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);
  private readonly reads = inject(ConsoleTaskDiagnostics);
  private readonly commands = inject(CommandRecovery);
  readonly inspectionScope = computed(() => {
    const session = this.session.viewerScope();
    const id = this.id();
    return session && id && this.session.admin ? { session, id } : null;
  });
  readonly inspection = linkedSignal({
    source: this.inspectionScope,
    computation: (): InspectionState => ({ open: false }),
  });
  private request?: AbortController;
  readonly diagnosticText = prettyDocument;
  readonly downloadResult = downloadJson;

  constructor() {
    this.destroyRef.onDestroy(() => this.request?.abort());
  }

  ngOnInit() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {
        const id = params.get('id') ?? '';
        if (id !== this.id()) this.request?.abort();
        this.id.set(id);
      });
    this.session.identityChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.request?.abort());
  }

  ngAfterViewInit() {
    let previousScope: ReturnType<typeof this.inspectionScope> = null;
    let previousContract: TaskContract | undefined;
    effect(() => {
      const scope = this.inspectionScope();
      const contract = this.headerContract();
      const earlier = scope === previousScope ? previousContract : undefined;
      if (scope !== previousScope || contract) {
        previousScope = scope;
        previousContract = contract ?? undefined;
      }
      if (scope && contract && contract !== earlier) untracked(() => {
        const state = this.inspection();
        if (!state.open) return;
        if (!earlier) {
          this.inspection.set({ ...state, precondition: contract.precondition });
          return;
        }
        this.request?.abort();
        this.inspection.set({ open: true });
        void this.loadInspection();
      });
    }, { injector: this.injector });
  }

  toggleInspection(open: boolean) {
    if (!this.inspectionScope() || this.inspection().open === open) return;
    this.request?.abort();
    this.inspection.set({ open });
    if (open) void this.loadInspection();
  }

  async loadInspection() {
    const scope = this.inspectionScope();
    const state = this.inspection();
    if (!scope || !state.open || state.loading) return;
    const controller = this.request = new AbortController();
    const precondition = this.headerContract()?.precondition;
    this.inspection.set({ open: true, loading: true, precondition });
    const current = () => !controller.signal.aborted && this.inspectionScope() === scope;
    try {
      const data = await this.reads.readTaskDiagnostics(scope.id, controller.signal);
      if (current()) this.inspection.set({ ...this.inspection(), loading: false, data });
    } catch {
      if (current()) this.inspection.set({ open: true, failed: true });
    } finally {
      if (this.request === controller) this.request = undefined;
    }
  }

  get hasTaskCommand() {
    return this.commands.buffer.command?.body?.['taskId'] === this.id();
  }
}
