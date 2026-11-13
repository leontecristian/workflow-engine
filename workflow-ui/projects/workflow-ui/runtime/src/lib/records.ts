import { Component, inject, input, output } from "@angular/core";
import { CommonModule } from "@angular/common";
import { RouterLink } from "@angular/router";
import { MatButtonModule } from "@angular/material/button";
import { MatTableModule } from "@angular/material/table";
import { MatTooltipModule } from "@angular/material/tooltip";
import { ConsoleIcon } from "@workflow-engine/ui";
import { WORKFLOW_VIEWER_NAVIGATION, type ViewerDestination } from "@workflow-engine/ui";
import { ConsoleStatus } from "@workflow-engine/ui";
export interface RecordColumn {
  key: string;
  label: string;
}
const COLUMNS: Record<string, RecordColumn[]> = {
  instances: [
    { key: "id", label: "Instance" },
    { key: "definitionKey", label: "Workflow" },
    { key: "status", label: "Status" },
    { key: "currentNodeKey", label: "Current step" },
    { key: "startedAt", label: "Started" },
    { key: "startedBy", label: "Started by" },
    { key: "resultKey", label: "Result" },
  ],
  submissions: [
    { key: "taskId", label: "Task ID" },
    { key: "outcomeKey", label: "Outcome" },
    { key: "actorRef", label: "Submitted by" },
    { key: "occurredAt", label: "Submitted" },
  ],
  tasks: [
    { key: "nodeName", label: "Task" },
    { key: "taskTypeName", label: "Task type" },
    { key: "instanceId", label: "Instance" },
    { key: "assignment", label: "Assignment" },
    { key: "assigneeRef", label: "Assignee" },
    { key: "status", label: "Status" },
  ],
};
@Component({
  selector: "console-records",
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatTooltipModule,
    RouterLink,
    ConsoleStatus,
    ConsoleIcon,
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
            @if (kind() === "instances" && column.key === "id") {
              <button
                mat-button
                class="record-link monospace"
                (click)="inspect.emit(row)"
                [disabled]="disabled()"
              >
                #{{ row.id }}
              </button>
            } @else if (
              kind() === "instances" && column.key === "definitionKey"
            ) {
              <span class="monospace">{{ row.definitionKey }}</span
              ><small class="record-id"
                >Version {{ row.definitionVersion }}</small
              >
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
              ><small class="record-id"
                >@if (column.key === 'nodeName') { {{row.taskKey}} · }#{{ row.id || row.taskId || row.historySequence }}</small
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
          @if (kind() === "instances") {
            <a
              mat-icon-button
              [routerLink]="link({kind:'instanceTasks',id:row.id})"
              matTooltip="Instance tasks"
              [attr.aria-label]="'Tasks for instance ' + row.id"
              ><console-icon name="tasks"
            /></a>
            <a
              mat-icon-button
              [routerLink]="link({kind:'history',instanceId:row.id})"
              matTooltip="Ordered history"
              [attr.aria-label]="'History for instance ' + row.id"
              ><console-icon name="history"
            /></a>
          }
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
            <console-icon name="chevron" />
          </button></td
      ></ng-container>
      <tr mat-header-row *matHeaderRowDef="columnKeys()"></tr>
      <tr mat-row *matRowDef="let row; columns: columnKeys()"></tr>
    </table>
  </div>`,
})
export class ConsoleRecords {
  private readonly navigation=inject(WORKFLOW_VIEWER_NAVIGATION,{optional:true});
  link(intent:ViewerDestination){return this.navigation?.destination(intent)??null;}
  rows = input<any[]>([]);
  kind = input("");
  label = input("Records");
  disabled = input(false);
  inspect = output<any>();
  columns(): RecordColumn[] {
    return COLUMNS[this.kind()] || COLUMNS["tasks"];
  }
  columnKeys(): string[] {
    return [...this.columns().map((column) => column.key), "actions"];
  }
  cell(row: any, key: string): any {
    if (key === "nodeName") return row.nodeName || row.taskKey;
    return key === "assignment" && row.assignment ? `${row.assignment.kind}: ${row.assignment.ref}` : row[key];
  }
  isStatus(key: string): boolean {
    return ["status"].includes(key);
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
