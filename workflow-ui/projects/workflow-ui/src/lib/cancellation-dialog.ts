import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { validateCancellationReason } from './runtime/command/command-buffer';

@Component({
 selector: 'workflow-cancellation-dialog', standalone: true,
 imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule],
 template: `<h2 mat-dialog-title>Cancel workflow #{{data.instanceId}}</h2>
 <mat-dialog-content><p>Outstanding tasks will stop. Previous decisions and business effects remain.</p>
 <mat-form-field><mat-label>Reason</mat-label><textarea matInput [(ngModel)]="reason" maxlength="2000" required rows="5"></textarea></mat-form-field>
 @if(error){<p role="alert">{{error}}</p>}</mat-dialog-content>
 <mat-dialog-actions align="end"><button mat-button mat-dialog-close>Keep workflow active</button>
 <button mat-flat-button (click)="confirm()">Cancel workflow</button></mat-dialog-actions>`,
 styles: [`mat-form-field{width:100%} textarea{resize:vertical}`]
})
export class WorkflowCancellationDialog {
 readonly data = inject<{ instanceId: string }>(MAT_DIALOG_DATA);
 private readonly dialog = inject(MatDialogRef<WorkflowCancellationDialog>);
 reason = '';
 error = '';

 confirm() {
  try {
   validateCancellationReason(this.reason);
   this.dialog.close(this.reason);
  } catch (error) {
   this.error = String((error as Error).message);
  }
 }
}
