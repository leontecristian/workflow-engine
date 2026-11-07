import { Component, input } from "@angular/core";
import { CommonModule } from "@angular/common";
import { MatExpansionModule } from "@angular/material/expansion";
import { ConsoleStatus } from "./status";
@Component({
  selector: "console-details",
  standalone: true,
  styles: [".metadata-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:16px}dt{font-size:12px}dd{margin:4px 0;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere}"],
  imports: [CommonModule, MatExpansionModule, ConsoleStatus],
  template: `@if (value()) {
      <dl class="metadata-grid">
        @for (entry of scalarEntries(); track entry[0]) {
          <div>
            <dt>{{ label(entry[0]) }}</dt>
            <dd>
              @if (isStatus(entry[0])) {
                <console-status [value]="entry[1]" />
              } @else if (isDate(entry[0], entry[1])) {
                <span
                  >{{ entry[1] | date: "MMM d, y, HH:mm:ss" : "UTC" }}
                  <small>UTC</small></span
                >
              } @else {
                <span [class.monospace]="isExact(entry[0])">{{
                  entry[1] === null ? "—" : entry[1]
                }}</span>
              }
            </dd>
          </div>
        }
      </dl>
      <mat-expansion-panel class="evidence-panel"
        ><mat-expansion-panel-header
          ><mat-panel-title>Technical record</mat-panel-title
          ><mat-panel-description
            >Exact API response</mat-panel-description
          ></mat-expansion-panel-header
        >
        <pre>{{ value() | json }}</pre>
      </mat-expansion-panel>
    } @else {
      <p class="empty-copy">No record available.</p>
    }`,
})
export class ConsoleDetails {
  value = input<any>(null);
  scalarEntries(): [string, any][] {
    return Object.entries(this.value() || {}).filter(
      ([key, value]) =>
        !key.endsWith("Json") && (value === null || typeof value !== "object"),
    );
  }
  label(key: string): string {
    return key
      .replace(/([a-z])([A-Z])/g, "$1 $2")
      .replace(/^./, (c) => c.toUpperCase());
  }
  isStatus(key: string): boolean {
    return ["status", "selectable", "replayed"].includes(key);
  }
  isExact(key: string): boolean {
    return /[Ii]d$|[Kk]ey$|[Vv]ersion$|[Ss]equence$|[Hh]ash$|[Rr]ef$/.test(key);
  }
  isDate(key: string, value: any): boolean {
    return (
      key.endsWith("At") &&
      typeof value === "string" &&
      /^\d{4}-\d{2}-\d{2}T/.test(value)
    );
  }
}
