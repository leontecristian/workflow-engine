import { Component } from "@angular/core";
import { MatButtonModule } from "@angular/material/button";
import { MatDialogModule } from "@angular/material/dialog";
export { MatDialog } from "@angular/material/dialog";

@Component({
  selector: "console-reload-definition-dialog",
  standalone: true,
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>Discard unsaved changes?</h2>
    <mat-dialog-content>
      Discard unsaved document text and reload the saved definition?
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Cancel</button>
      <button mat-flat-button [mat-dialog-close]="true">Discard and reload</button>
    </mat-dialog-actions>
  `,
})
export class ReloadDefinitionDialog {}
