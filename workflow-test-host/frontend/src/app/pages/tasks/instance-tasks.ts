import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { TaskView } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-instance-tasks",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./instance-tasks.html",
})
export class InstanceTasksPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Instance tasks",
    eyebrow: "RUNTIME INSPECTION",
    description: "Inspect open and completed assignments for this instance.",
    icon: "tasks",
    listTitle: "Tasks",
    listDescription:
      "Select a task to inspect its contract and accepted submission.",
  };
  rows = signal<TaskView[]>([]);
  next: string | null = null;
  pageSize = 50;
  id = "";
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.id = params.get("id")!;
        void this.load();
      });
  }
  async load(cursor?: string) {
    const id = this.id;
    await this.run(async () => {
      const p = await this.api.instanceTasks(id, cursor, this.pageSize);
      if (this.destroyRef.destroyed || this.id !== id) return;
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
