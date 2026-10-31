import { Component, input } from "@angular/core";
@Component({
  selector: "console-status",
  standalone: true,
  template: `<span class="status-pill" [class]="'status-pill status-' + tone()"
    ><span class="status-dot"></span>{{ value() }}</span
  >`,
})
export class ConsoleStatus {
  value = input<unknown>("");
  tone(): string {
    const value = String(this.value()).toUpperCase();
    if (
      [
        "PUBLISHED",
        "COMPLETED",
        "PASSED",
        "DELIVERED",
        "SELECTABLE",
        "TRUE",
      ].includes(value)
    )
      return "success";
    if (["ACTIVE", "OPEN", "PENDING", "DRAFT"].includes(value)) return "info";
    if (["FAILED", "REJECTED", "UNKNOWN_OUTCOME"].includes(value))
      return "danger";
    return "neutral";
  }
}
