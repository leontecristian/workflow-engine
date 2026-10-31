import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import { CommandRecovery } from "../../data/command-recovery";
import { exactDecimal } from "../../command-buffer";
import type { VersionSummary, SandboxRequest } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-requests",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./requests.html",
})
export class RequestsPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Requests & start",
    eyebrow: "WORKFLOW EXECUTION",
    description:
      "Create a sandbox request and start an explicitly selected published workflow.",
    icon: "start",
  };
  rows = signal<SandboxRequest[]>([]);
  next: string | null = null;
  pageSize = 50;
  name = "";
  familyId = "";
  versionId = "";
  requestId = "";
  requestToken = "0";
  publishedRows = signal<VersionSummary[]>([]);
  publishedNext: string | null = null;
  commands = inject(CommandRecovery);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const id = params.get("requestId"),
          version = params.get("requestVersion");
        if (id !== null && version !== null) {
          this.requestId = id;
          this.requestToken = version;
        }
      });
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.listRequests(cursor, this.pageSize);
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    void this.router.navigate(["/request", row.id]);
  }
  async createRequest() {
    await this.run(async () => {
      const r = await this.api.createRequest(this.name);
      this.requestId = r.id;
      this.requestToken = r.lockVersion;
      this.notice.set("Request " + r.id + " created");
    });
    if (!this.error()) await this.load();
  }
  async start(): Promise<void> {
    await this.run(async () => {
      this.commands.buffer.remember({
        kind: "start",
        body: {
          commandId: crypto.randomUUID(),
          definitionVersionId: exactDecimal(this.versionId),
          requestId: exactDecimal(this.requestId),
          expectedRequestVersion: exactDecimal(this.requestToken, true),
        },
        expectedToken: null,
      });
      await this.issue(false);
    });
  }
  async loadPublished(next = false) {
    await this.run(async () => {
      const p = await this.api.listVersions(
        exactDecimal(this.familyId),
        "PUBLISHED",
        next ? this.publishedNext || undefined : undefined,
        this.pageSize,
      );
      this.publishedRows.set(p.items);
      this.publishedNext = p.nextAfterId;
      this.versionId = "";
    });
  }
  async issue(replay: boolean) {
    await this.commands.execute(replay);
    this.notice.set(
      replay
        ? "Replay-only lookup returned; no new command was executed."
        : "Command returned after commit. Inspect current state separately.",
    );
    await this.router.navigate(["/commands"]);
  }
}
