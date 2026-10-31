import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { AuditView } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-audit",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./audit.html",
})
export class AuditPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Management audit",
    eyebrow: "OBSERVABILITY",
    description:
      "Trace committed catalog and definition changes to their trusted actors.",
    icon: "history",
    listTitle: "Management changes",
    listDescription:
      "Changed results are audited in the same transaction as the core mutation.",
  };
  rows = signal<AuditView[]>([]);
  next: string | null = null;
  pageSize = 50;
  ngOnInit() {
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.audit(cursor, this.pageSize);
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    void this.router.navigate(["/audit-event", row.id]);
  }
}
