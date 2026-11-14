import { Component, inject, input, output } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { MatButtonModule } from "@angular/material/button";
import { MatTableModule } from "@angular/material/table";
import { MatTooltipModule } from "@angular/material/tooltip";
import { MatIconModule } from "@angular/material/icon";
import { ConsoleStatus, WORKFLOW_VIEWER_NAVIGATION } from "@workflow-engine/ui";
export interface RecordColumn {
  key: string;
  label: string;
}
const COLUMNS: Record<string, RecordColumn[]> = {
  definitions: [
    { key: "id", label: "ID" },
    { key: "name", label: "Name" },
    { key: "key", label: "Workflow key" },
    { key: "createdBy", label: "Created by" },
    { key: "createdAt", label: "Created" },
  ],
  definition: [
    { key: "id", label: "ID" },
    { key: "version", label: "Version" },
    { key: "status", label: "Status" },
    { key: "contentRevision", label: "Revision" },
    { key: "updatedAt", label: "Updated" },
  ],
  catalog: [
    { key: "name", label: "Task type" },
    { key: "code", label: "Permanent code" },
    { key: "selectable", label: "Authoring" },
    { key: "lockVersion", label: "Version" },
  ],
};
@Component({
  selector: "console-records",
  standalone: true,
  host: {
    "[class.definition-records]": "kind() === 'definitions'",
    "[class.version-records]": "kind() === 'definition'",
  },
  styles: [":host{display:block;overflow:auto}table{width:100%}.record-id,.date-cell small{display:block}.monospace{font-family:monospace}", `
    :host(.definition-records) { overflow:visible; min-width:0; }
    :host(.definition-records) .table-scroll { margin:0; overflow-x:auto; }
    :host(.definition-records) table { table-layout:fixed; min-width:760px; }
    :host(.definition-records) th, :host(.definition-records) td { overflow-wrap:anywhere; }
    :host(.definition-records) .mat-column-actions { width:80px; }
    :host(.definition-records) .mat-column-id { width:6rem; }
    :host(.definition-records) .definition-id { color:var(--mat-sys-primary); text-decoration:none; }
    :host(.version-records) { overflow:visible; min-width:0; }
    :host(.version-records) .table-scroll { margin:0; overflow-x:auto; }
    :host(.version-records) table { table-layout:fixed; min-width:760px; }
    :host(.version-records) th, :host(.version-records) td { overflow-wrap:anywhere; }
    :host(.version-records) .mat-column-actions { width:80px; }
    :host(.version-records) .mat-column-id { width:6rem; }
    :host(.version-records) .mat-column-version { width:8rem; }
    :host(.version-records) .version-link { color:var(--mat-sys-primary); text-decoration:none; }
  `],
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatTooltipModule,
    RouterLink,
    ConsoleStatus,
    MatIconModule,
  ],
  template: `<div
    class="table-scroll"
    [class.instances-table]="kind() === 'instances'"
  >
    <table mat-table [dataSource]="rows()" [attr.aria-label]="label()">
      @for (column of columns(); track column.key) {
        <ng-container [matColumnDef]="column.key"
          ><th mat-header-cell *matHeaderCellDef>{{ column.label }}</th>
          <td mat-cell *matCellDef="let row">
            @if (kind() === 'definitions' && column.key === 'id') {
              @if (definitionRoute(row); as destination) {
                <a class="definition-id monospace" [routerLink]="destination">#{{row.id}}</a>
              } @else {
                <span class="monospace">#{{row.id}}</span>
              }
            } @else if (kind() === 'definition' && column.key === 'id') {
              @if (versionRoute(row); as destination) {
                <a class="version-link monospace" [routerLink]="destination">#{{row.id}}</a>
              } @else {
                <span class="monospace">#{{row.id}}</span>
              }
            } @else if (isStatus(column.key)) {
              <console-status [value]="cell(row, column.key)" />
            } @else if (isDate(column.key, cell(row, column.key))) {
              <span class="date-cell"
                >{{ cell(row, column.key) | date: "MMM d, y" : "UTC"
                }}<small
                  >{{
                    cell(row, column.key) | date: "HH:mm:ss" : "UTC"
                  }}
                  UTC</small
                ></span
              >
            } @else if (column === columns()[0]) {
              <button
                mat-button
                class="record-link"
                (click)="inspect.emit(row)"
                [disabled]="disabled()"
              >
                {{ cell(row, column.key) ?? "—" }}</button
              >
              <small class="record-id"
                >#{{ row.id || row.taskId || row.historySequence }}</small
              >
            } @else {
              <span [class.monospace]="isExact(column.key)">{{
                cell(row, column.key) ?? "—"
              }}</span>
            }
          </td></ng-container
        >
      }
      <ng-container matColumnDef="actions"
        ><th mat-header-cell *matHeaderCellDef>
          <span class="visually-hidden">Actions</span>
        </th>
        <td mat-cell *matCellDef="let row">
          <button
            mat-icon-button
            [matTooltip]="
              kind() === 'catalog' ? 'Edit task type' : 'Inspect record'
            "
            [attr.aria-label]="
              'Inspect ' +
              (row.name || row.id || row.eventId || row.taskId || 'record')
            "
            (click)="inspect.emit(row)"
            [disabled]="disabled()"
          >
            <mat-icon aria-hidden="true" iconPositionEnd>chevron_right</mat-icon>
          </button></td
      ></ng-container>
      <tr mat-header-row *matHeaderRowDef="columnKeys()"></tr>
      <tr mat-row *matRowDef="let row; columns: columnKeys()"></tr>
    </table>
  </div>`,
})
export class ConsoleRecords {
  private readonly navigation = inject(WORKFLOW_VIEWER_NAVIGATION, { optional:true });
  rows = input<any[]>([]);
  kind = input("");
  label = input("Records");
  disabled = input(false);
  inspect = output<any>();
  definitionRoute(row: { id:string }) {
    return this.kind() === 'definitions' && !this.disabled()
      ? this.navigation?.destination({ kind:'definition', id:row.id }) ?? null : null;
  }
  versionRoute(row: { id:string }) {
    return this.kind() === 'definition' && !this.disabled()
      ? this.navigation?.destination({ kind:'version', id:row.id }) ?? null : null;
  }
  columns(): RecordColumn[] {
    return COLUMNS[this.kind()] || COLUMNS["definitions"];
  }
  columnKeys(): string[] {
    return [...this.columns().map((column) => column.key), "actions"];
  }
  cell(row: any, key: string): any {
    if (key === "deliveryState")
      return row.deliveredAt ? "DELIVERED" : "PENDING";
    if (key === "selectable") return row.selectable ? "SELECTABLE" : "DISABLED";
    return row[key];
  }
  isStatus(key: string): boolean {
    return ["status", "selectable", "deliveryState"].includes(key);
  }
  isDate(key: string, value: any): boolean {
    return (
      key.endsWith("At") &&
      typeof value === "string" &&
      /^\d{4}-\d{2}-\d{2}T/.test(value)
    );
  }
  isExact(key: string): boolean {
    return /[Ii]d$|[Kk]ey$|[Vv]ersion$|[Ss]equence$|^code$/.test(key);
  }
}
