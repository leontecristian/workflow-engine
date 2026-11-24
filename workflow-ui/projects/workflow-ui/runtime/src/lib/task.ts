import { Component, Injector, computed, inject, input, signal } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { CommandRecovery, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_TASK_REASSIGNMENT } from '@workflow-engine/ui';
import type { Envelope, TaskDetail, TaskContract, TaskSubmission, PreviousDecision } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
import { DefaultTaskHandlerComponent } from './default-task-handler';
import { WORKFLOW_TASK_EDITORS, type WorkflowEditableTaskContract, type WorkflowTaskDecision, type WorkflowTaskHandlerInputs, type WorkflowTaskRejection } from './task-handler';
import { isBlankComment, isBlankResult } from './task-decision-validation';
import { WorkflowTaskValuesComponent } from './task-values';
import { PreviousDecisionComment } from './previous-decision-comment';

type TimelineDecision = {
  decision: PreviousDecision;
  initials: string;
  outcome: 'approved' | 'rejected' | 'other';
};

@Component({
  selector: 'workflow-task',
  standalone: true,
  imports: [...PAGE_IMPORTS, NgComponentOutlet, WorkflowTaskValuesComponent, PreviousDecisionComment],
  styleUrl: './task.scss',
  templateUrl: './task.html',
})
export class WorkflowTaskComponent extends RuntimeState {
  readonly externalPageHeader = input(false);
  private readonly injector = inject(Injector);
  private readonly reassignment = inject(WORKFLOW_TASK_REASSIGNMENT, { optional: true });
  private readonly taskEditors = inject(WORKFLOW_TASK_EDITORS, { optional: true });
  readonly commands = inject(CommandRecovery);
  readonly commandProvider = inject(WORKFLOW_RUNTIME_COMMANDS, { optional: true });

  readonly detail = signal<TaskDetail | null>(null);
  readonly contract = signal<TaskContract | null>(null);
  protected readonly businessInputs = computed(() => {
    const inputs = this.contract()?.inputs;
    return inputs && Object.keys(inputs).length ? inputs : undefined;
  });
  readonly submission = signal<TaskSubmission | null>(null);
  readonly nextTaskId = signal<string | null>(null);
  readonly previousDecisions = signal<PreviousDecision[]>([]);
  readonly previousCursor = signal<string | null>(null);
  readonly previousBusy = signal(false);
  readonly previousError = signal('');
  readonly newestDecisionsFirst = signal(false);
  readonly decisionTimeline = computed(() => {
    const decisions = this.previousDecisions();
    const ordered = this.newestDecisionsFirst() ? decisions.slice().reverse() : decisions;
    const groups = new Map<string, TimelineDecision[]>();
    const counts = { approved: 0, rejected: 0, other: 0 };
    for (const decision of ordered) {
      const label = decision.outcomeLabel.trim().toLowerCase();
      const outcome =
        label === 'approve' || label === 'approved' ? 'approved' :
        label === 'reject' || label === 'rejected' ? 'rejected' : 'other';
      const initials = decision.actorLabel.trim().split(/\s+/).slice(0, 2)
        .map(word => Array.from(word)[0]).join('').toUpperCase();
      counts[outcome]++;
      const entry: TimelineDecision = { decision, initials: initials || '?', outcome };
      const group = groups.get(decision.nodeName);
      if (group) group.push(entry);
      else groups.set(decision.nodeName, [entry]);
    }
    return { groups: Array.from(groups), counts, total: decisions.length };
  });
  readonly recoveryMiss = signal(false);
  readonly reassignmentAllowed = signal(false);
  readonly reassignmentError = signal('');
  private readonly viewRevision = signal(0);
  private previousRead?: AbortController;
  private confirmation?: { close(): void };
  private dialogOpen = false;
  private changingTask = false;
  private closed = false;
  reason = '';

  constructor() {
    super();
    this.destroyRef.onDestroy(() => { this.closed = true; });
  }

  protected override onInvalidate() {
    this.confirmation?.close();
    this.dialogOpen = false;
  }

  protected override onScope() {
    this.commands.setScope(this.sessionScope);
  }

  protected clearContent() {
    this.viewRevision.update(revision => revision + 1);
    this.changingTask = false;
    this.previousRead?.abort();
    this.previousBusy.set(false);
    this.previousDecisions.set([]);
    this.newestDecisionsFirst.set(false);
    this.previousCursor.set(null);
    this.previousError.set('');
    this.detail.set(null);
    this.contract.set(null);
    this.submission.set(null);
    this.nextTaskId.set(null);
    this.recoveryMiss.set(false);
    this.reassignmentAllowed.set(false);
    this.reassignmentError.set('');
    this.reason = '';
  }

  protected override clearFailedRead() {
    if (!this.changingTask) this.clearContent();
  }

  async load() {
    this.clearContent();
    this.notice.set('');
    const id = this.id;
    const loaded = await this.run(async (signal, current) => {
      const contract = await this.api.taskContract(id, signal);
      if (!current()) return;
      this.detail.set(contract.task);
      this.contract.set(contract);

      const [accepted, availability] = await Promise.all([
        contract.task.status === 'COMPLETED' ? this.api.taskSubmission(id, signal).catch(error => {
          if ([403, 404].includes(error?.httpStatus)) return null;
          throw error;
        }) : null,
        this.reassignment && contract.task.status === 'OPEN'
          ? this.reassignment.availability(id, signal).catch(error => {
              if (error?.httpStatus === 401) throw error;
              return null;
            }) : null,
      ]);
      if (!current()) return;
      this.submission.set(accepted);
      this.reassignmentAllowed.set(availability?.allowed === true);
    });
    if (loaded) await this.loadPreviousDecisions();
  }

  get hasPreviousDecisions() { return this.contract()?.previousDecisionsAvailable === true; }
  noPreviousComment(comment?: string | null) { return comment == null || isBlankComment(comment); }

  async loadPreviousDecisions(more = false) {
    const cursor = more ? this.previousCursor() : null;
    if (!this.hasPreviousDecisions || this.previousBusy() || !this.sessionScope || (more && !cursor)) return;
    this.previousRead?.abort();
    const controller = this.previousRead = new AbortController();
    const id = this.id;
    this.previousBusy.set(true);
    this.previousError.set('');
    try {
      const page = await this.api.previousDecisions(id, cursor ?? undefined, 20, controller.signal);
      if (controller.signal.aborted) return;
      this.previousDecisions.set(more ? [...this.previousDecisions(), ...page.items] : page.items);
      this.previousCursor.set(page.nextCursor);
    } catch (failure) {
      if (controller.signal.aborted) return;
      const status = (failure as { httpStatus?: number })?.httpStatus;
      if (status === 401 || status === 403 || status === 404) {
        this.clearContent();
        this.error.set('Resource missing or unavailable.');
      } else {
        this.previousError.set('Previous decisions could not be loaded. Try again.');
      }
    } finally {
      if (!controller.signal.aborted) this.previousBusy.set(false);
    }
  }

  private readonly handlerView = computed(() => {
    const revision = this.viewRevision();
    const contract = this.contract();
    if (!this.commandProvider || !contract?.canComplete || !contract.taskType || !contract.precondition
      || contract.task.status !== 'OPEN' || !contract.outcomes.every(outcome => !!outcome.key)) return null;
    const editableContract = contract as WorkflowEditableTaskContract;
    return {
      contract: editableContract,
      component: this.taskEditors?.get(editableContract.taskType) ?? DefaultTaskHandlerComponent,
      submitDecision: async (decision: WorkflowTaskDecision) => {
        if (this.viewRevision() !== revision || this.contract() !== contract || this.actionsBlocked) return;
        return this.complete(decision, editableContract);
      },
    };
  });

  readonly handler = computed(() => this.handlerView()?.component ?? null);
  private readonly reloadForm = () => { void this.load(); };
  readonly handlerInputs = computed((): (WorkflowTaskHandlerInputs & Record<string, unknown>) | null => {
    const view = this.handlerView();
    return view && {
      contract: view.contract, editable: !this.actionsBlocked, submitDecision: view.submitDecision,
      ...(view.component === DefaultTaskHandlerComponent ? { reloadForm: this.reloadForm } : {}),
    };
  });

  pendingCommand(): Envelope | undefined {
    this.commands.runningIds;
    return this.commands.buffer.entries.find(entry => entry.kind !== 'start' && entry.kind !== 'cancel'
      && entry.body['taskId'] === this.id && this.commands.pendingIds.has(entry.body['commandId']!));
  }

  get uncertain() { return !!this.pendingCommand(); }
  private get actionsBlocked() { return this.closed || this.busy() || this.uncertain; }

  canReassign() {
    return this.detail()?.status === 'OPEN' && this.reassignmentAllowed() && !!this.contract()?.precondition && !this.uncertain;
  }

  async confirmReassignment() {
    if (!this.canReassign() || this.actionsBlocked || this.dialogOpen) return;
    const contract = this.contract()!;
    const taskId = this.id;
    const revision = this.viewRevision();
    this.dialogOpen = true;
    this.reassignmentError.set('');
    try {
      const [{ MatDialog }, { WorkflowReassignmentDialog }] = await Promise.all([
        import('@angular/material/dialog'), import('./reassignment-dialog'),
      ]);
      if (this.viewRevision() !== revision || this.contract() !== contract || this.actionsBlocked) {
        this.dialogOpen = false;
        return;
      }
      const dialog = this.confirmation = this.injector.get(MatDialog).open(WorkflowReassignmentDialog, {
        data: { task: contract.task, taskId, version: contract.precondition }, width: '36rem', maxWidth: '95vw',
      });
      dialog.afterClosed().subscribe((envelope: Envelope | undefined) => {
        this.dialogOpen = false;
        if (envelope && this.viewRevision() === revision && this.contract() === contract && !this.actionsBlocked) {
          void this.send(this.commands.buffer.remember(envelope));
        }
      });
    } catch {
      this.dialogOpen = false;
      if (this.contract() === contract) this.reassignmentError.set('Confirmation could not be opened. Try again.');
    }
  }

  reasonBlank() { return isBlankComment(this.reason); }

  async ownership(kind: 'claim' | 'release' | 'admin-release') {
    const contract = this.contract();
    if (!this.commandProvider?.ownership || !contract?.precondition || this.actionsBlocked) return;
    const allowed = kind === 'claim' ? contract.canClaim : kind === 'release' ? contract.canRelease : contract.canAdminRelease;
    if (!allowed || (kind === 'admin-release' && this.reasonBlank())) return;
    const body = { commandId: crypto.randomUUID(), taskId: this.id,
      ...(kind === 'admin-release' ? { reason: this.reason } : {}) };
    await this.send(this.commands.buffer.remember({ kind, body, expectedToken: contract.precondition }));
  }

  private async complete(decision: WorkflowTaskDecision, contract: WorkflowEditableTaskContract) {
    const { outcomeKey, comment, resultJson } = decision ?? {};
    const selected = contract.outcomes.find(outcome => outcome.key === outcomeKey);
    const mode = contract.resultContract?.mode ?? 'FORBIDDEN';
    const hasResult = typeof resultJson === 'string' && !isBlankResult(resultJson);
    let validationMessage: string | undefined;
    if (!selected) validationMessage = 'Choose an available outcome.';
    else if (typeof comment !== 'string' || comment.length > 2000) validationMessage = 'Enter a comment of at most 2000 characters.';
    else if (selected.requiresComment && isBlankComment(comment)) validationMessage = 'A comment is required for ' + selected.label + '.';
    else if (resultJson !== undefined && typeof resultJson !== 'string') validationMessage = 'The result is invalid.';
    else if (mode === 'REQUIRED' && !hasResult) validationMessage = 'Enter the required result.';
    else if (mode === 'FORBIDDEN' && hasResult) validationMessage = 'This task does not accept a result.';
    if (validationMessage) {
      this.error.set(validationMessage);
      return { message: validationMessage };
    }
    let envelope: Envelope;
    try {
      envelope = this.commands.buffer.remember({
        kind: 'complete',
        body: { commandId: crypto.randomUUID(), taskId: this.id, outcomeKey, comment: comment || null,
          ...(hasResult ? { resultJson } : {}) },
        expectedToken: contract.precondition,
      });
    } catch {
      const message = 'Check the result and try again.';
      this.error.set(message);
      return { message };
    }
    return this.send(envelope);
  }

  async recover(replay: boolean) {
    const envelope = this.pendingCommand();
    if (!envelope || this.busy()) return;
    await this.send(envelope, replay, true);
  }

  private async send(envelope: Envelope, replay = false, recovering = false): Promise<WorkflowTaskRejection | void> {
    if (this.busy() || this.closed || this.uncertain && !recovering) return;
    const scope = this.sessionScope, id = this.id, displayed = this.contract();
    const revision = this.viewRevision();
    this.changingTask = true;
    this.recoveryMiss.set(false);
    this.notice.set('');
    let conflict = false;
    let found = true;
    let nextTaskId: string | undefined;
    let message = '';
    let rejection: WorkflowTaskRejection | undefined;
    const succeeded = await this.run(async (signal, current) => {
      this.commands.select(envelope.body['commandId']!);
      try {
        const result = await this.commands.execute(replay, signal);
        if (!current()) return;
        const settled = 'found' in result ? result.result : result;
        if (!settled) { found = false; this.recoveryMiss.set(true); return; }
        if ('taskStatus' in settled) {
          nextTaskId = settled.nextTaskId;
          message = envelope.kind === 'complete'
            ? settled.collectivePending === undefined ? 'Task completed.'
              : settled.collectivePending ? 'Your contribution was accepted. The review is waiting for other tasks.'
                : 'Your submission resolved the review.'
            : envelope.kind === 'reassign' ? 'Task reassigned.' : 'Task ownership updated.';
        }
      } catch (error) {
        if (!current()) return;
        const failure = error as { httpStatus?: number; code?: string } & WorkflowTaskRejection;
        conflict = failure.httpStatus === 409;
        if (!this.uncertain && !conflict) {
          rejection = { message: failure.message || 'The task action could not be completed.', fieldErrors: failure.fieldErrors };
        }
        if (failure.httpStatus === 401 || failure.httpStatus === 403 && !recovering) throw error;
        if (this.uncertain && recovering) this.error.set('The original action is still unresolved.');
        else if (rejection?.message) this.error.set(rejection.message);
      }
    });
    if (this.closed || this.sessionScope !== scope || this.id !== id || this.viewRevision() !== revision
      || this.contract() !== displayed) return;
    this.changingTask = false;
    if (conflict || succeeded && found && !rejection && !this.uncertain) {
      const refresh = this.load();
      const current = this.capture();
      await refresh;
      if (!current()) return;
      if (conflict) this.error.set('The task changed. Current details have been refreshed.');
      else {
        this.nextTaskId.set(nextTaskId && nextTaskId !== id ? nextTaskId : null);
        if (message) this.notice.set(message);
      }
    }
    return rejection;
  }
}
