import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { isBlankComment } from '@workflow-engine/ui/runtime';
import type { WorkflowEditableTaskContract, WorkflowTaskHandlerInputs } from '@workflow-engine/ui/runtime';

@Component({
  selector: 'console-booking-review',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule],
  styleUrl: './booking-review.scss',
  template: `
    @if (resultMode !== 'FORBIDDEN') {
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Reviewed room</mat-label>
        <input matInput [(ngModel)]="reviewedRoom" [disabled]="!editable"
          [required]="resultMode === 'REQUIRED'" />
      </mat-form-field>
    }
    <mat-form-field appearance="outline" class="full-width" subscriptSizing="dynamic">
      <mat-label>Comment</mat-label>
      <textarea #commentField matInput [(ngModel)]="comment" [errorStateMatcher]="commentErrorMatcher"
                [disabled]="!editable" maxlength="2000" rows="4"></textarea>
      @if(missingRejectComment){<mat-error>Add a comment to reject</mat-error>}
      @if(commentOutcomes){<mat-hint>Required for {{commentOutcomes}}.</mat-hint>}
    </mat-form-field>
    <div class="actions decision-actions">
      @for (outcome of decisionOutcomes; track outcome.key) {
        @if (outcome.key === 'REJECT') {
          <button mat-stroked-button type="button" class="reject-action"
                  (click)="submit(outcome.key, commentField)" [disabled]="!canAttempt(outcome)">
            <mat-icon aria-hidden="true">close</mat-icon>
            {{outcome.label}}
          </button>
        } @else {
          <button mat-flat-button type="button" (click)="submit(outcome.key)" [disabled]="!canAttempt(outcome)">
            @if (outcome.key === 'APPROVE') { <mat-icon aria-hidden="true">check</mat-icon> }
            {{outcome.label}}
          </button>
        }
      }
    </div>
  `,
})
export class BookingReviewComponent implements WorkflowTaskHandlerInputs {
  @Input({ required: true }) contract!: WorkflowEditableTaskContract;
  @Input({ required: true }) editable = false;
  @Input({ required: true }) submitDecision!: WorkflowTaskHandlerInputs['submitDecision'];

  reviewedRoom = '';
  comment = '';
  private rejectedWithoutComment = false;
  readonly commentErrorMatcher = { isErrorState: () => this.missingRejectComment };

  get blankComment(): boolean {
    return isBlankComment(this.comment);
  }

  get missingRejectComment(): boolean {
    return this.rejectedWithoutComment && this.blankComment;
  }

  get decisionOutcomes() {
    const outcomes = this.contract.outcomes;
    const [first, second] = outcomes;
    return outcomes.length === 2 && first.key === 'APPROVE' && second.key === 'REJECT'
      ? [second, first] : outcomes;
  }

  get commentOutcomes(): string {
    return this.contract.outcomes.filter(outcome => outcome.requiresComment).map(outcome => outcome.label).join(', ');
  }

  get resultMode(): 'FORBIDDEN' | 'OPTIONAL' | 'REQUIRED' {
    return this.contract.resultContract?.mode ?? 'FORBIDDEN';
  }

  canAttempt(outcome: { key?: string; requiresComment: boolean }): boolean {
    return this.editable && !!outcome.key && (this.resultMode === 'FORBIDDEN' || !!this.reviewedRoom.trim())
      && (outcome.key === 'REJECT' || !outcome.requiresComment || !this.blankComment);
  }

  async submit(outcomeKey: string, commentField?: HTMLTextAreaElement): Promise<void> {
    const outcome = this.contract.outcomes.find(candidate => candidate.key === outcomeKey);
    if (!outcome || !this.canAttempt(outcome)) return;
    if (outcome.requiresComment && this.blankComment) {
      this.rejectedWithoutComment = true;
      commentField?.focus();
      return;
    }
    const resultJson = this.resultMode !== 'FORBIDDEN' && this.reviewedRoom.trim()
      ? JSON.stringify({ reviewedRoom: this.reviewedRoom })
      : undefined;
    await this.submitDecision({
      outcomeKey,
      comment: this.comment,
      ...(resultJson === undefined ? {} : { resultJson }),
    });
  }
}
