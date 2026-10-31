import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { OutboxEvent } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-outbox",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./outbox.html",
})
export class OutboxPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Event outbox",
    eyebrow: "OBSERVABILITY",
    description:
      "Inspect durable handoff events and operate the local dispatcher.",
    icon: "events",
  };
  rows = signal<OutboxEvent[]>([]);
  next: string | null = null;
  pageSize = 50;
  filter = "ALL";
  ngOnInit() {
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.outbox(this.filter, cursor, this.pageSize);
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: OutboxEvent) {
    void this.router.navigate(["/event", row.eventId]);
  }
  async dispatch() {
    await this.run(async () => {
      this.notice.set(JSON.stringify(await this.api.dispatch(this.pageSize)));
    });
    if (!this.error()) await this.load();
  }
}
