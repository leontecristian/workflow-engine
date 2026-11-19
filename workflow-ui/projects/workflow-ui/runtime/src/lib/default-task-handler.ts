import { NgTemplateOutlet } from '@angular/common';
import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import type { WorkflowEditableTaskContract, WorkflowTaskHandlerInputs, WorkflowTaskRejection } from './task-handler';
import { isBlankComment } from './task-decision-validation';
import { TaskAnswer, TaskFormDraft, type TaskFieldView } from './task-form-draft';

@Component({
  selector: 'workflow-default-task-handler',
  standalone: true,
  imports: [NgTemplateOutlet, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './default-task-handler.html',
  styleUrl: './default-task-handler.scss',
})
export class DefaultTaskHandlerComponent implements WorkflowTaskHandlerInputs {
  private activeContract!: WorkflowEditableTaskContract;

  @Input({ required: true })
  set contract(value: WorkflowEditableTaskContract) {
    if (value === this.activeContract) return;
    this.activeContract = value;
    const form = value.resultContract?.form;
    this.draft = form ? new TaskFormDraft(form) : null;
    this.showResult = this.resultMode === 'REQUIRED';
    this.comment = '';
    this.rejection = undefined;
    this.refreshView();
  }
  get contract() { return this.activeContract; }

  @Input({ required: true }) editable = false;
  @Input({ required: true }) submitDecision!: WorkflowTaskHandlerInputs['submitDecision'];
  @Input() reloadForm?: () => void;

  draft: TaskFormDraft | null = null;
  activeView: TaskFieldView | null = null;
  showResult = false;
  comment = '';
  submitting = false;
  private rejection?: WorkflowTaskRejection;
  private visiblePaths = new Set<string>();

  get resultMode() { return this.contract?.resultContract?.mode ?? 'FORBIDDEN'; }
  get disabled(): boolean { return !this.editable || this.submitting; }
  get resultUnavailable(): boolean { return this.resultMode !== 'FORBIDDEN' && !this.draft; }
  get commentRequired(): string {
    return this.contract.outcomes.filter(outcome => outcome.requiresComment).map(outcome => outcome.label).join(', ');
  }

  toggleResult(show: boolean): void {
    this.showResult = show;
    this.rejection = undefined;
    this.refreshView();
  }

  setPresence(view: TaskFieldView, presence: TaskAnswer['presence']): void {
    view.answer.presence = presence;
    this.changed(view.path);
  }

  writeText(view: TaskFieldView, text: string): void {
    view.answer.text = text;
    view.answer.literal = undefined;
    view.answer.presence = 'present';
    this.changed(view.path);
  }

  choose(view: TaskFieldView, literal: string): void {
    view.answer.literal = literal;
    view.answer.text = undefined;
    view.answer.presence = 'present';
    this.changed(view.path);
  }

  chooseShape(view: TaskFieldView, alternative: number): void {
    view.answer.alternative = alternative;
    view.answer.presence = 'present';
    this.changed(view.path);
  }

  addItem(view: TaskFieldView): void {
    if (view.atItemLimit) return;
    view.answer.items.push(new TaskAnswer());
    this.changed(view.path);
  }

  removeItem(view: TaskFieldView, index: number): void {
    view.answer.items.splice(index, 1);
    this.changed(view.path);
  }

  moveItem(view: TaskFieldView, index: number, offset: number): void {
    view.answer.moveItem(index, offset);
    this.changed(view.path);
  }

  fieldErrors(view: TaskFieldView): string[] {
    const external = this.rejection?.fieldErrors?.filter(error => error.path === view.path).map(error => error.message) ?? [];
    return view.issue ? [view.issue, ...external] : external;
  }

  get formErrors(): string[] {
    if (!this.rejection) return [];
    const fields = this.rejection.fieldErrors ?? [];
    if (!fields.length) return this.rejection.message ? [this.rejection.message] : [];
    return fields.filter(error => !this.visiblePaths.has(error.path)).map(error => error.message);
  }

  canSubmit(outcome: { key?: string; requiresComment: boolean }): boolean {
    return !this.disabled && !!outcome.key && !this.resultUnavailable
      && (!outcome.requiresComment || !isBlankComment(this.comment))
      && (!this.showResult || this.activeView?.valid === true);
  }

  async submit(outcomeKey: string): Promise<void> {
    const outcome = this.contract.outcomes.find(candidate => candidate.key === outcomeKey);
    if (!outcome || !this.canSubmit(outcome)) return;
    const resultJson = this.showResult ? this.draft?.result(this.activeView ?? undefined) : undefined;
    if (this.showResult && resultJson === undefined) return;
    const contract = this.contract;
    this.submitting = true;
    this.rejection = undefined;
    try {
      const rejected = await this.submitDecision({outcomeKey, comment: this.comment, ...(resultJson === undefined ? {} : {resultJson})});
      if (contract === this.contract) this.rejection = rejected || undefined;
    } finally {
      this.submitting = false;
    }
  }

  private changed(path: string): void {
    if (this.rejection) {
      const fields = this.rejection.fieldErrors?.filter(error => error.path !== path && !error.path.startsWith(path + '/')) ?? [];
      this.rejection = fields.length ? {fieldErrors: fields} : undefined;
    }
    this.refreshView();
  }

  private refreshView(): void {
    this.activeView = this.showResult ? this.draft?.view() ?? null : null;
    this.visiblePaths = new Set();
    if (this.activeView) this.collectPaths(this.activeView);
  }

  private collectPaths(view: TaskFieldView, hiddenKey?: string): void {
    this.visiblePaths.add(view.path);
    for (const child of view.children) if (child.field.key !== hiddenKey || hiddenKey === undefined) this.collectPaths(child);
    if (view.branch) this.collectPaths(view.branch, view.field.selectorKey);
  }
}
