import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { HistorySummary } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { destroyedState, pageState } from "../../ui/page-state";
@Component({
  selector: "console-history",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./history.html",
})
export class HistoryPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Execution history",
    eyebrow: "ORDERED EVIDENCE",
    description: "Follow the authoritative sequence of workflow events.",
    icon: "history",
    listTitle: "History events",
    listDescription:
      "Sequences are exact decimal strings and remain ordered across pages.",
  };
  rows = signal<HistorySummary[]>([]);
  next: string | null = null;
  pageSize = 50;
  id = "";
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isDestroyed = destroyedState(this.destroyRef);
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
      const p = await this.api.history(id, cursor, this.pageSize);
      if (this.isDestroyed() || this.id !== id) return;
      this.rows.set(p.items);
      this.next = p.nextAfterSequence;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: HistorySummary) {
    void this.router.navigate(["/history-event", this.id, row.historySequence]);
  }
}
