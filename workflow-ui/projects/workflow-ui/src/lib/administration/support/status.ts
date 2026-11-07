import { Component, input } from "@angular/core";
@Component({
  selector: "console-status",
  standalone: true,
  styles: [".status-pill{display:inline-flex;align-items:center;gap:6px;padding:4px 8px;border-radius:12px;background:#8882;white-space:nowrap}.status-success{color:#236b35}.status-danger{color:#a02626}.status-info{color:#24528a}.status-dot{width:6px;height:6px;border-radius:50%;background:currentColor}"],
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
        "APPROVED",
        "PASSED",
        "DELIVERED",
        "SELECTABLE",
        "TRUE",
      ].includes(value)
    )
      return "success";
    if (["ACTIVE", "OPEN", "PENDING", "DRAFT", "IN_REVIEW"].includes(value)) return "info";
    if (["FAILED", "REJECTED", "UNKNOWN_OUTCOME"].includes(value))
      return "danger";
    return "neutral";
  }
}
