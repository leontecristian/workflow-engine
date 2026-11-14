import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
export { MatDialog } from '@angular/material/dialog';

export interface DraftDialogData { action: 'create' | 'delete'; key: string; version: number; }

@Component({
  selector: 'workflow-draft-dialog', standalone: true,
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>{{data.action === 'create' ? 'Create new draft?' : 'Delete draft permanently?'}}</h2>
    <mat-dialog-content>
      <p>Workflow {{data.key}}, version {{data.version}}.</p>
      @if(data.action === 'create') {
        <p>Copy the saved source into a new draft. The server assigns its version number automatically. This does not validate or publish it.</p>
      } @else {
        <p>The draft content will be permanently deleted. Its version number remains reserved and can never be reused. There is no undo.</p>
        <p>Any unsaved edits will be discarded.</p>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <button mat-flat-button [mat-dialog-close]="true">{{data.action === 'create' ? 'Create new draft' : 'Delete draft'}}</button>
    </mat-dialog-actions>`,
})
export class DraftDefinitionDialog { readonly data = inject<DraftDialogData>(MAT_DIALOG_DATA); }
