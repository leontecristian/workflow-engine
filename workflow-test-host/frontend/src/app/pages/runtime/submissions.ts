import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { TaskSubmission } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-submissions",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./submissions.html",
})
export class SubmissionsPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Accepted submissions",
    eyebrow: "AUTHORITATIVE EVIDENCE",
    description:
      "Read immutable submissions accepted for the selected activity.",
    icon: "file",
    listTitle: "Submissions",
    listDescription: "These are accepted facts, not editable task drafts.",
  };
  rows = signal<TaskSubmission[]>([]);
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
      const p = await this.api.submissions(id, cursor, this.pageSize);
      if (this.destroyRef.destroyed || this.id !== id) return;
      this.rows.set(p.items);
      this.next = p.nextAfterTaskId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: TaskSubmission) {
    void this.router.navigate(["/task", row.taskId]);
  }
}
