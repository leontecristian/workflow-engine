import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { HistoryEvent } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-history-event",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./history-event.html",
})
export class HistoryEventPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "History event",
    eyebrow: "EXECUTION EVIDENCE",
    description: "Exact persisted details from the ordered instance history.",
    icon: "history",
  };
  id = "";
  sequence = "";
  detail = signal<HistoryEvent | null>(null);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => {
        this.id = p.get("id")!;
        this.sequence = p.get("sequence")!;
        void this.load();
      });
  }
  async load() {
    const id = this.id,
      sequence = this.sequence;
    await this.run(async () => {
      const d = await this.api.historyEvent(id, sequence);
      if (
        !this.destroyRef.destroyed &&
        this.id === id &&
        this.sequence === sequence
      )
        this.detail.set(d);
    });
  }
}
