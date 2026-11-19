import { Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import type { TaskContract } from '@workflow-engine/ui';
import type { WorkflowTaskHandlerInputs } from '@workflow-engine/ui/runtime';

@Component({
  selector: 'console-booking-review',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  template: `
    @if (bookingReference; as reference) {
      <p>Booking reference <strong>{{ reference }}</strong></p>
    }
    @if (resultMode !== 'FORBIDDEN') {
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>Reviewed room</mat-label>
        <input matInput [(ngModel)]="reviewedRoom" [disabled]="!editable"
          [required]="resultMode === 'REQUIRED'" />
      </mat-form-field>
    }
    <mat-form-field appearance="outline" class="full-width">
      <mat-label>Comment</mat-label>
      <textarea matInput [(ngModel)]="comment" [disabled]="!editable" maxlength="2000" rows="4"></textarea>
      <mat-hint align="end">{{ comment.length }} / 2000</mat-hint>
    </mat-form-field>
    <div class="actions">
      @for (outcome of contract.outcomes; track outcome.key) {
        <button mat-flat-button type="button" (click)="submit(outcome.key)"
          [disabled]="!editable || (resultMode === 'REQUIRED' && !reviewedRoom.trim())">
          {{ outcome.label }}{{ outcome.requiresComment ? ' - comment required' : '' }}
        </button>
      }
    </div>
  `,
})
export class BookingReviewComponent implements WorkflowTaskHandlerInputs {
  @Input({ required: true }) contract!: TaskContract;
  @Input({ required: true }) editable = false;
  @Input({ required: true }) submitDecision!: WorkflowTaskHandlerInputs['submitDecision'];

  reviewedRoom = '';
  comment = '';

  get bookingReference(): string | undefined {
    const reference = this.contract.inputs?.['bookingReference'];
    if (reference?.type !== 'STRING') return undefined;
    const value: unknown = JSON.parse(reference.valueJson);
    return typeof value === 'string' ? value : undefined;
  }

  get resultMode(): 'FORBIDDEN' | 'OPTIONAL' | 'REQUIRED' {
    return this.contract.resultContract?.mode ?? 'FORBIDDEN';
  }

  submit(outcomeKey: string): Promise<void> {
    const resultJson = this.resultMode !== 'FORBIDDEN' && this.reviewedRoom.trim()
      ? JSON.stringify({ reviewedRoom: this.reviewedRoom })
      : undefined;
    return this.submitDecision({
      outcomeKey,
      comment: this.comment,
      ...(resultJson === undefined ? {} : { resultJson }),
    });
  }
}
