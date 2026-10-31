import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { TaskView } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-assigned-tasks",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./assigned-tasks.html",
})
export class AssignedTasksPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "My tasks",
    eyebrow: "YOUR WORKSPACE",
    description:
      "Your directly assigned open tasks, read from the workflow engine.",
    icon: "tasks",
    listTitle: "Assigned to you",
    listDescription:
      "Only your authenticated account’s stored assignments appear here.",
  };
  rows = signal<TaskView[]>([]);
  next: string | null = null;
  pageSize = 50;
  ngOnInit() {
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.assignedTasks(cursor, this.pageSize);
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    void this.router.navigate(["/task", row.id]);
  }
}
