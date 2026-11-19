import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { WorkflowTaskHandlerInputs } from './task-handler';
import { isBlankComment, isBlankResult } from './task-decision-validation';

@Component({
  selector: 'workflow-default-task-handler',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatExpansionModule, MatFormFieldModule, MatInputModule],
  templateUrl: './default-task-handler.html',
  styles: [`
    mat-form-field { width: 100%; }
    .actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .muted { opacity: .7; }
    textarea.code, pre { font-family: monospace; }
    pre { white-space: pre-wrap; overflow-wrap: anywhere; }
  `],
})
export class DefaultTaskHandlerComponent implements WorkflowTaskHandlerInputs {
  @Input({ required: true }) contract!: WorkflowTaskHandlerInputs['contract'];
  @Input({ required: true }) editable = false;
  @Input({ required: true }) submitDecision!: WorkflowTaskHandlerInputs['submitDecision'];

  comment = '';
  resultDraft = '';

  get resultMode() { return this.contract.resultContract?.mode ?? 'FORBIDDEN'; }
  get resultSchema() {
    const result = this.contract.resultContract;
    return result && result.mode !== 'FORBIDDEN' ? result.schemaJson : undefined;
  }

  canSubmit(outcome: { requiresComment: boolean }): boolean {
    return this.editable
      && (!outcome.requiresComment || !isBlankComment(this.comment))
      && (this.resultMode !== 'REQUIRED' || !isBlankResult(this.resultDraft));
  }

  submit(outcomeKey: string): Promise<void> {
    const outcome = this.contract.outcomes.find(candidate => candidate.key === outcomeKey);
    if (!outcome || !this.canSubmit(outcome)) return Promise.resolve();
    return this.submitDecision({
      outcomeKey,
      comment: this.comment,
      ...(this.resultMode !== 'FORBIDDEN' && !isBlankResult(this.resultDraft) ? { resultJson: this.resultDraft } : {}),
    });
  }
}
