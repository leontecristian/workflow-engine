import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { OutboxEvent } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-event",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./event.html",
})
export class EventPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Event details",
    eyebrow: "DURABLE HANDOFF",
    description:
      "Inspect the immutable event payload and its local delivery record.",
    icon: "events",
  };
  id = "";
  detail = signal<OutboxEvent | null>(null);
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
  async load() {
    const id = this.id;
    await this.run(async () => {
      const d = await this.api.outboxEvent(id);
      if (!this.destroyRef.destroyed && this.id === id) this.detail.set(d);
    });
  }
  async redeliver() {
    await this.run(async () => {
      this.notice.set(JSON.stringify(await this.api.redeliver(this.id)));
    });
    if (!this.error()) await this.load();
  }
}
