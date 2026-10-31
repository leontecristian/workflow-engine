import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { InstanceSummary } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-instances",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./instances.html",
})
export class InstancesPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Instances",
    eyebrow: "WORKFLOW MONITORING",
    description:
      "Monitor running workflows, inspect their current step and open their execution evidence.",
    icon: "workflow",
    listTitle: "Workflow instances",
  };
  rows = signal<InstanceSummary[]>([]);
  next: string | null = null;
  pageSize = 50;
  filter = "ACTIVE";
  ngOnInit() {
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.listInstances(
        this.filter,
        cursor,
        this.pageSize,
      );
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    void this.router.navigate(["/instance", row.id]);
  }
}
