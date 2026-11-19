import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { isBlankComment } from '@workflow-engine/ui/runtime';
import type { WorkflowEditableTaskContract, WorkflowTaskHandlerInputs } from '@workflow-engine/ui/runtime';

@Component({
  selector: 'console-booking-review',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
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
      <textarea matInput [(ngModel)]="comment" [disabled]="!editable" maxlength="2000" rows="4"></textarea>
      @if(commentOutcomes){<mat-hint>Required for {{commentOutcomes}}.</mat-hint>}
    </mat-form-field>
    <div class="actions">
      @for (outcome of contract.outcomes; track outcome.key) {
        <button mat-flat-button type="button" (click)="submit(outcome.key)"
          [disabled]="!editable || (resultMode !== 'FORBIDDEN' && !reviewedRoom.trim()) || (outcome.requiresComment && blankComment)">
          {{ outcome.label }}
        </button>
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

  get blankComment(): boolean {
    return isBlankComment(this.comment);
  }

  get commentOutcomes(): string {
    return this.contract.outcomes.filter(outcome => outcome.requiresComment).map(outcome => outcome.label).join(', ');
  }

  get resultMode(): 'FORBIDDEN' | 'OPTIONAL' | 'REQUIRED' {
    return this.contract.resultContract?.mode ?? 'FORBIDDEN';
  }

  async submit(outcomeKey: string): Promise<void> {
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
