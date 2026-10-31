import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { InboxEvent } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-inbox",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./inbox.html",
})
export class InboxPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Local inbox",
    eyebrow: "OBSERVABILITY",
    description: "Inspect the durable local sink and retained event payloads.",
    icon: "inbox",
    listTitle: "Received events",
    listDescription:
      "A stable event ID has one inbox effect, including after redelivery.",
  };
  rows = signal<InboxEvent[]>([]);
  next: string | null = null;
  pageSize = 50;
  ngOnInit() {
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.inbox(cursor, this.pageSize);
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: InboxEvent) {
    void this.router.navigate(["/inbox-event", row.eventId]);
  }
}
