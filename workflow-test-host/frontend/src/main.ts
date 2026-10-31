import { bootstrapApplication } from "@angular/platform-browser";
import { Component, DestroyRef, inject, OnInit, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import {
  provideRouter,
  Router,
  RouterLink,
  RouterOutlet,
  NavigationEnd,
  withHashLocation,
} from "@angular/router";
import { ConsoleApi } from "./app/console-api";
import { rawDocument, exactDecimal } from "./app/command-buffer";

@Component({
  selector: "console-page",
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <header>
      <p class="eyebrow">
        LOCAL DEVELOPMENT · {{ api.admin ? "ADMINISTRATOR" : "REVIEWER" }}
      </p>
      <h1>{{ title }}</h1>
    </header>
    @if (error()) {
      <div class="error" role="alert">{{ error() }}</div>
    }
    @if (notice()) {
      <div class="notice" role="status">{{ notice() }}</div>
    }
    @if (busy()) {
      <p role="status">Working…</p>
    }
    @if (page === "definitions") {
      <section>
        <h2>Import an explicit version</h2>
        <p>
          Paste or upload the workflow JSON exactly. Import never overwrites an
          existing version.
        </p>
        <input
          type="file"
          accept=".json,application/json"
          (change)="upload($event)"
        /><textarea
          [(ngModel)]="document"
          aria-label="Raw workflow JSON"
          rows="14"
        ></textarea
        ><button (click)="importDraft()" [disabled]="busy()">
          Import draft
        </button>
      </section>
      <section>
        <h2>Find exact version</h2>
        <label>Workflow key<input [(ngModel)]="lookupKey" /></label
        ><label
          >Version<input
            [(ngModel)]="lookupVersion"
            inputmode="numeric" /></label
        ><button (click)="lookup()" [disabled]="busy()">Find version</button>
      </section>
      <section>
        <h2>Definitions</h2>
        <ng-container *ngTemplateOutlet="list"></ng-container>
      </section>
    }
    @if (page === "definition") {
      <section>
        <h2>Family metadata</h2>
        <label>Name<input [(ngModel)]="name" /></label
        ><label
          >Description<textarea [(ngModel)]="description"></textarea></label
        ><button (click)="metadata()" [disabled]="busy()">Save metadata</button>
        <pre>{{ detail() | json }}</pre>
      </section>
      <section>
        <h2>Versions</h2>
        <label
          >Status<select [(ngModel)]="filter" (change)="load()">
            <option value="">All</option>
            <option>DRAFT</option>
            <option>PUBLISHED</option>
            <option>RETIRED</option>
          </select></label
        ><ng-container *ngTemplateOutlet="list"></ng-container>
      </section>
    }
    @if (page === "version") {
      <section>
        <h2>
          Version {{ detail()?.summary?.version }} ·
          {{ detail()?.summary?.status }}
        </h2>
        <pre>{{ detail()?.summary | json }}</pre>
        <label
          >Raw workflow JSON<textarea
            [(ngModel)]="document"
            (ngModelChange)="dirty = true"
            rows="24"
            [readOnly]="detail()?.summary?.status !== 'DRAFT'"
          ></textarea>
        </label>
        <div class="actions">
          <button
            (click)="saveDocument()"
            [disabled]="busy() || detail()?.summary?.status !== 'DRAFT'"
          >
            Save draft</button
          ><button (click)="reloadEditor()" [disabled]="busy()">
            Reload current document</button
          ><a [href]="'/console/api/versions/' + id + '/export'">Export JSON</a>
        </div>
        <div class="actions">
          <button (click)="lifecycle('validate')" [disabled]="busy() || dirty">
            Validate</button
          ><button (click)="lifecycle('publish')" [disabled]="busy() || dirty">
            Publish</button
          ><button (click)="lifecycle('retire')" [disabled]="busy() || dirty">
            Retire
          </button>
        </div>
        <h3>Validation evidence for stored content</h3>
        @if (dirty) {
          <p>
            Unsaved editor text has not been validated. Save explicitly before
            validation or publication.
          </p>
        } @else {
          <p>
            Details omitted: {{ validationReport()?.detailsOmitted || false }}
          </p>
          @for (d of validationReport()?.diagnostics || []; track $index) {
            <article>
              <strong>{{ d.severity }} · {{ d.code }}</strong>
              <p>{{ d.stage }} · {{ d.pointer || "/" }}</p>
              <p>{{ d.message }}</p>
            </article>
          }
        }
        <details>
          <summary>Stored validation JSON</summary>
          <pre>{{ detail()?.validationDetailsJson }}</pre>
        </details>
        <details>
          <summary>Latest sanitized operation response</summary>
          <pre>{{ lastResponse() | json }}</pre>
        </details>
      </section>
    }
    @if (page === "catalog") {
      <section>
        <h2>{{ editing ? "Edit catalog metadata" : "Create task type" }}</h2>
        <label>Code<input [(ngModel)]="code" [readOnly]="!!editing" /></label
        ><label>Name<input [(ngModel)]="name" /></label
        ><label
          >Description<textarea [(ngModel)]="description"></textarea></label
        ><label class="check"
          ><input type="checkbox" [(ngModel)]="selectable" /> Selectable for
          authoring</label
        ><button (click)="saveCatalog()" [disabled]="busy()">
          Save task type</button
        ><button (click)="editing = null" [disabled]="busy()">New entry</button>
      </section>
      <section>
        <label
          >Filter<select [(ngModel)]="filter" (change)="load()">
            <option>ALL</option>
            <option>SELECTABLE</option>
            <option>NONSELECTABLE</option>
          </select></label
        ><ng-container *ngTemplateOutlet="list"></ng-container>
      </section>
    }
    @if (page === "requests") {
      <section>
        <h2>Create a retained sandbox request</h2>
        <label>Name<input [(ngModel)]="name" /></label
        ><button (click)="createRequest()" [disabled]="busy()">
          Create request
        </button>
      </section>
      <section>
        <h2>Start a published definition</h2>
        <p>
          A new action allocates a new UUID. The original input remains in
          Command recovery. Published choices remain available when their
          catalog entry is disabled.
        </p>
        <label
          >Definition family ID<input
            [(ngModel)]="familyId"
            inputmode="numeric" /></label
        ><button (click)="loadPublished()" [disabled]="busy()">
          Load published versions</button
        ><button
          (click)="loadPublished(true)"
          [disabled]="busy() || !publishedNext"
        >
          Next published page</button
        ><label
          >Exact published version<select [(ngModel)]="versionId">
            <option value="">Choose version</option>
            @for (v of publishedRows(); track v.id) {
              <option [value]="v.id">
                {{ v.key }} v{{ v.version }} · ID {{ v.id }}
              </option>
            }
          </select></label
        ><label
          >Request ID<input
            [(ngModel)]="requestId"
            inputmode="numeric" /></label
        ><label
          >Expected request version<input
            [(ngModel)]="requestToken"
            inputmode="numeric" /></label
        ><button (click)="start()" [disabled]="busy() || !versionId">
          Start new command
        </button>
      </section>
      <section><ng-container *ngTemplateOutlet="list"></ng-container></section>
    }
    @if (page === "request") {
      <section>
        <pre>{{ detail() | json }}</pre>
        <button (click)="selectRequest(detail())" [disabled]="busy()">
          Use in start form
        </button>
      </section>
    }
    @if (page === "instance") {
      <section>
        <h2>Current instance</h2>
        <pre>{{ detail() | json }}</pre>
        <div class="actions">
          <a [routerLink]="['/instance-tasks', id]">Tasks</a
          ><a [routerLink]="['/history', id]">Ordered history</a
          ><button (click)="load()" [disabled]="busy()">
            Refresh current state
          </button>
        </div>
      </section>
    }
    @if (page === "task") {
      <section>
        <h2>Stored assignment</h2>
        <pre>{{ detail() | json }}</pre>
        <a [routerLink]="['/instance', detail()?.instanceId]">Instance</a> ·
        <a [routerLink]="['/activity', detail()?.activityId]">Activity</a>
      </section>
      <section>
        <h2>Pinned completion contract</h2>
        <pre>{{ contract() | json }}</pre>
        <label
          >Outcome<select [(ngModel)]="outcome">
            <option value="">Choose outcome</option>
            @for (o of contract()?.outcomes || []; track o.key) {
              <option [value]="o.key">
                {{ o.label || o.key
                }}{{ o.requiresComment ? " · comment required" : "" }}
              </option>
            }
          </select></label
        ><label
          >Comment<textarea [(ngModel)]="comment" maxlength="2000"></textarea>
        </label>
        <p>Expected instance token: {{ contract()?.instanceLockVersion }}</p>
        <button
          (click)="complete()"
          [disabled]="busy() || !outcome || detail()?.status !== 'OPEN'"
        >
          Complete with new command</button
        ><button (click)="load()" [disabled]="busy()">
          Refresh current task
        </button>
      </section>
      <section>
        <h2>Authoritative submission</h2>
        <pre>{{ submission() | json }}</pre>
      </section>
    }
    @if (page === "commands") {
      <section>
        <h2>Original command envelope</h2>
        <p>
          Retried commands keep their UUID, input and original token. Replay
          results describe the original command; refresh the instance separately
          for current state. Buffers are cleared on logout and are not stored in
          the browser.
        </p>
        <label
          >Attempted command<select
            [ngModel]="api.buffer.command?.body?.['commandId']"
            (ngModelChange)="selectCommand($event)"
          >
            @for (c of api.buffer.entries; track c.body["commandId"]) {
              <option [value]="c.body['commandId']">
                {{ c.kind }} · {{ c.body["commandId"] }}
              </option>
            }
          </select></label
        >
        <pre>{{ api.buffer.command | json }}</pre>
        <div class="actions">
          <button
            (click)="retry(false)"
            [disabled]="busy() || !api.buffer.command"
          >
            Retry original command</button
          ><button
            (click)="retry(true)"
            [disabled]="busy() || !api.buffer.command"
          >
            Find result (replay only)</button
          ><button (click)="copyEnvelope()" [disabled]="busy()">
            Copy envelope</button
          ><button (click)="downloadEnvelope()" [disabled]="busy()">
            Download envelope</button
          ><button (click)="clearCommand()" [disabled]="busy()">
            Clear all buffered commands
          </button>
        </div>
        <label
          >Paste a saved command envelope<textarea
            [(ngModel)]="envelope"
            rows="10"
          ></textarea></label
        ><button (click)="restoreEnvelope()" [disabled]="busy()">
          Restore envelope
        </button>
        <h3>Original result / recovery</h3>
        <pre>{{ result() | json }}</pre>
        @if (commandExecution()) {
          <div class="actions">
            <a [routerLink]="['/instance', commandExecution().instance.id]"
              >Read current instance</a
            ><a [routerLink]="['/task', commandExecution().task.id]"
              >Read current task/submission</a
            ><a [routerLink]="['/history', commandExecution().instance.id]"
              >Read current history</a
            >
          </div>
        }
      </section>
    }
    @if (page === "events") {
      <section>
        <h2>Durable local delivery</h2>
        <p>
          Dispatch runs only on request. Redelivery records another attempt
          while retaining one inbox effect.
        </p>
        <button (click)="dispatch()" [disabled]="busy()">
          Dispatch up to {{ pageSize }} pending events</button
        ><label
          >Status<select [(ngModel)]="filter" (change)="load()">
            <option>ALL</option>
            <option>PENDING</option>
            <option>DELIVERED</option>
          </select></label
        ><ng-container *ngTemplateOutlet="list"></ng-container>
      </section>
    }
    @if (page === "event") {
      <section>
        <pre>{{ detail() | json }}</pre>
        <button (click)="redeliver()" [disabled]="busy()">
          Redeliver locally</button
        ><a [routerLink]="['/inbox-event', id]">Inspect inbox event</a>
      </section>
    }
    @if (
      [
        "audit",
        "inbox",
        "my-tasks",
        "instance-tasks",
        "history",
        "submissions",
      ].includes(page)
    ) {
      <section><ng-container *ngTemplateOutlet="list"></ng-container></section>
    }
    @if (
      [
        "audit-event",
        "history-event",
        "inbox-event",
        "activity",
        "capabilities",
      ].includes(page)
    ) {
      <section>
        <pre>{{ detail() | json }}</pre>
        @if (page === "activity") {
          <a [routerLink]="['/submissions', id]">Accepted submissions</a>
        }
      </section>
    }
    <ng-template #list
      ><div class="actions">
        <label
          >Page size<select [(ngModel)]="pageSize" (change)="load()">
            <option [ngValue]="1">1</option>
            <option [ngValue]="10">10</option>
            <option [ngValue]="50">50</option>
            <option [ngValue]="100">100</option>
          </select></label
        ><button (click)="load()" [disabled]="busy()">First page</button
        ><button (click)="nextPage()" [disabled]="!next || busy()">
          Next page
        </button>
      </div>
      @if (!rows().length) {
        <p>No records in this page.</p>
      }
      @for (row of rows(); track $index) {
        <article>
          <div class="row-head">
            <strong
              >{{
                row.name ||
                  row.definitionKey ||
                  row.eventType ||
                  row.operation ||
                  row.taskKey ||
                  row.eventId ||
                  "Record"
              }}
              #{{ row.id || row.historySequence || row.taskId }}</strong
            ><button (click)="open(row)" [disabled]="busy()">Inspect</button>
          </div>
          <pre>{{ row | json }}</pre>
        </article>
      }
    </ng-template>
  `,
})
export class ConsolePage implements OnInit {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  page = "";
  id = "";
  sequence = "";
  title = "Workflow console";
  error = signal("");
  notice = signal("");
  busy = signal(false);
  rows = signal<any[]>([]);
  detail = signal<any>(null);
  contract = signal<any>(null);
  submission = signal<any>(null);
  result = this.api.commandResult;
  next: string | null = null;
  pageSize = 50;
  filter = "ALL";
  document = "";
  dirty = false;
  name = "";
  description = "";
  code = "SANDBOX_REVIEW";
  selectable = true;
  editing: any = null;
  versionId = "";
  familyId = "";
  publishedRows = signal<any[]>([]);
  publishedNext: string | null = null;
  requestId = "";
  requestToken = "0";
  outcome = "";
  comment = "";
  envelope = "";
  lookupKey = "";
  lookupVersion = "1";
  lastResponse = signal<any>(null);
  validationReport(): any {
    const evidence = this.detail()?.validationDetailsJson;
    if (!evidence) return null;
    try {
      return JSON.parse(evidence).report;
    } catch {
      return null;
    }
  }
  commandExecution(): any {
    return (
      this.result()?.result?.execution ||
      this.result()?.result?.result?.execution ||
      null
    );
  }
  ngOnInit(): void {
    void this.activate();
    this.router.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((e) => {
        if (e instanceof NavigationEnd) void this.activate();
      });
  }
  async activate(): Promise<void> {
    const parts = this.router.url.split("?")[0].split("/");
    this.page = parts[1] || "my-tasks";
    this.id = parts[2] || "";
    this.sequence = parts[3] || "";
    if (this.page === "requests") {
      const params = this.router.parseUrl(this.router.url).queryParamMap;
      const requestId = params.get("requestId");
      const requestVersion = params.get("requestVersion");
      if (requestId !== null && requestVersion !== null) {
        this.requestId = requestId;
        this.requestToken = requestVersion;
      }
    }
    this.title = this.page
      .split("-")
      .map((s) => s[0].toUpperCase() + s.slice(1))
      .join(" ");
    this.dirty = false;
    this.filter = ["definition"].includes(this.page) ? "" : "ALL";
    await this.load();
  }
  async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set("");
    try {
      await action();
    } catch (e) {
      this.error.set(String(e instanceof Error ? e.message : e));
    } finally {
      this.busy.set(false);
    }
  }
  async load(cursor?: string): Promise<void> {
    await this.run(async () => {
      if (!this.api.session()) await this.api.initialize();
      const routes: Record<string, string> = {
        definitions: "/definitions",
        definition: `/definitions/${this.id}/versions`,
        catalog: "/task-types",
        requests: "/sandbox-requests",
        audit: "/audit",
        events: "/events",
        inbox: "/inbox",
        "my-tasks": "/tasks/mine",
        "instance-tasks": `/instances/${this.id}/tasks`,
        history: `/instances/${this.id}/history`,
        submissions: `/activities/${this.id}/submissions`,
      };
      if (routes[this.page]) {
        const params = new URLSearchParams({ limit: String(this.pageSize) });
        const cursorName =
          this.page === "history"
            ? "afterSequence"
            : this.page === "submissions"
              ? "afterTaskId"
              : "afterId";
        if (cursor) params.set(cursorName, cursor);
        if (["catalog", "events"].includes(this.page))
          params.set(
            this.page === "catalog" ? "filter" : "status",
            this.filter,
          );
        if (this.page === "definition" && this.filter)
          params.set("status", this.filter);
        const p = await this.api.call(routes[this.page] + "?" + params);
        this.rows.set(p.items);
        this.next =
          p.nextAfterId ?? p.nextAfterSequence ?? p.nextAfterTaskId ?? null;
        if (this.page === "definition") {
          const d = await this.api.call("/definitions/" + this.id);
          this.detail.set(d);
          this.name = d.name;
          this.description = d.description || "";
        }
        return;
      }
      const exact: Record<string, string> = {
        version: `/versions/${this.id}`,
        instance: `/instances/${this.id}`,
        task: `/tasks/${this.id}`,
        activity: `/activities/${this.id}`,
        request: `/sandbox-requests/${this.id}`,
        event: `/events/${this.id}`,
        "inbox-event": `/inbox/${this.id}`,
        "audit-event": `/audit/${this.id}`,
        "history-event": `/instances/${this.id}/history/${this.sequence}`,
        capabilities: "/capabilities",
      };
      if (exact[this.page]) {
        const d = await this.api.call(exact[this.page]);
        this.detail.set(d);
        if (this.page === "version") {
          this.document = d.documentJson;
          this.dirty = false;
        }
        if (this.page === "task") {
          this.contract.set(await this.api.call(`/tasks/${this.id}/contract`));
          this.submission.set(
            await this.api.call(`/tasks/${this.id}/submission`),
          );
        }
      }
    });
  }
  nextPage(): void {
    if (this.next) void this.load(this.next);
  }
  open(row: any): void {
    const pages: Record<string, string> = {
      definitions: "definition",
      definition: "version",
      requests: "request",
      audit: "audit-event",
      events: "event",
      inbox: "inbox-event",
      "my-tasks": "task",
      "instance-tasks": "task",
      submissions: "task",
    };
    if (this.page === "catalog") {
      this.editing = row;
      this.code = row.code;
      this.name = row.name;
      this.description = row.description || "";
      this.selectable = row.selectable;
      return;
    }
    if (this.page === "history") {
      void this.router.navigate([
        "/history-event",
        this.id,
        row.historySequence,
      ]);
      return;
    }
    void this.router.navigate([
      "/" + pages[this.page],
      ["events", "inbox"].includes(this.page)
        ? row.eventId
        : row.id || row.taskId,
    ]);
  }
  async upload(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    await this.run(async () => {
      if (file.size > 4 * 1024 * 1024)
        throw new Error("Document exceeds 4 MiB upload limit");
      this.document = rawDocument(await file.text());
      this.dirty = true;
    });
  }
  async lookup(): Promise<void> {
    await this.run(async () => {
      const r = await this.api.call(
        "/versions/lookup?" +
          new URLSearchParams({
            key: this.lookupKey,
            version: this.lookupVersion,
          }),
      );
      await this.router.navigate(["/version", r.summary.id]);
    });
  }
  async importDraft(): Promise<void> {
    await this.run(async () => {
      const r = await this.api.call(
        "/versions/import",
        "POST",
        rawDocument(this.document),
        null,
        true,
      );
      this.notice.set(
        r.changed ? "Draft imported" : "Existing identical content reused",
      );
      await this.router.navigate(["/version", r.after.id]);
    });
  }
  async saveDocument(): Promise<void> {
    await this.run(async () => {
      const r = await this.api.call(
        `/versions/${this.id}/document`,
        "PUT",
        rawDocument(this.document),
        this.detail().summary.lockVersion,
        true,
      );
      this.detail.update((d) => ({
        ...d,
        summary: r.after,
        validationDetailsJson: r.changed ? null : d.validationDetailsJson,
      }));
      this.lastResponse.set(r);
      this.dirty = false;
      this.notice.set("Draft saved");
    });
  }
  reloadEditor(): void {
    if (!this.dirty || confirm("Discard unsaved document text and reload?"))
      void this.load();
  }
  async lifecycle(action: string): Promise<void> {
    await this.run(async () => {
      const r = await this.api.call(
        `/versions/${this.id}/${action}`,
        "POST",
        undefined,
        this.detail().summary.lockVersion,
      );
      this.lastResponse.set(r);
      this.detail.update((d) => ({
        ...d,
        summary: r.change?.after || r.after,
      }));
      this.notice.set(
        r.report
          ? `Validation ${r.report.valid ? "PASSED" : "FAILED"}; published=${r.published}`
          : `Retirement changed=${r.changed}`,
      );
    });
    if (!this.error()) await this.load();
  }
  async metadata(): Promise<void> {
    await this.run(async () => {
      const r = await this.api.call(
        `/definitions/${this.id}`,
        "PUT",
        { name: this.name, description: this.description || null },
        this.detail().lockVersion,
      );
      this.detail.set(r.after);
      this.notice.set(r.changed ? "Metadata saved" : "No change");
    });
  }
  async saveCatalog(): Promise<void> {
    await this.run(async () => {
      const body: any = {
        name: this.name,
        description: this.description || null,
        selectable: this.selectable,
      };
      if (!this.editing) body.code = this.code;
      const change = await this.api.call(
        this.editing ? `/task-types/${this.editing.id}` : "/task-types",
        this.editing ? "PUT" : "POST",
        body,
        this.editing?.lockVersion,
      );
      this.editing = change.after;
      this.notice.set("Catalog saved");
    });
    if (!this.error()) await this.load();
  }
  async createRequest(): Promise<void> {
    await this.run(async () => {
      const r = await this.api.call("/sandbox-requests", "POST", {
        name: this.name,
      });
      this.requestId = r.id;
      this.requestToken = r.lockVersion;
      this.notice.set("Request " + r.id + " created");
    });
    if (!this.error()) await this.load();
  }
  selectRequest(row: any): void {
    void this.router.navigate(["/requests"], {
      queryParams: { requestId: row.id, requestVersion: row.lockVersion },
    });
  }
  async start(): Promise<void> {
    await this.run(async () => {
      this.api.buffer.remember({
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
  async loadPublished(next = false): Promise<void> {
    await this.run(async () => {
      const params = new URLSearchParams({
        status: "PUBLISHED",
        limit: String(this.pageSize),
      });
      if (next && this.publishedNext) params.set("afterId", this.publishedNext);
      const p = await this.api.call(
        `/definitions/${exactDecimal(this.familyId)}/versions?${params}`,
      );
      this.publishedRows.set(p.items);
      this.publishedNext = p.nextAfterId;
      this.versionId = "";
    });
  }
  async complete(): Promise<void> {
    await this.run(async () => {
      this.api.buffer.remember({
        kind: "complete",
        body: {
          commandId: crypto.randomUUID(),
          instanceId: this.detail().instanceId,
          taskId: this.id,
          outcomeKey: this.outcome,
          comment: this.comment || null,
        },
        expectedToken: this.contract().instanceLockVersion,
      });
      await this.issue(false);
    });
  }
  async issue(replay: boolean): Promise<void> {
    await this.api.execute(this.api.buffer.command!, replay);
    this.notice.set(
      replay
        ? "Replay-only lookup returned; no new command was executed."
        : "Command returned after commit. Inspect current state separately.",
    );
    await this.router.navigate(["/commands"]);
  }
  async retry(replay: boolean): Promise<void> {
    await this.run(() => this.issue(replay));
  }
  async copyEnvelope(): Promise<void> {
    await this.run(async () => {
      await navigator.clipboard.writeText(this.api.buffer.export());
      this.notice.set(
        "Envelope copied; contains command input. Keep it private.",
      );
    });
  }
  downloadEnvelope(): void {
    const url = URL.createObjectURL(
      new Blob([this.api.buffer.export()], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "workflow-command.json";
    a.click();
    URL.revokeObjectURL(url);
  }
  restoreEnvelope(): void {
    try {
      const restored = this.api.buffer.restore(this.envelope);
      this.result.set(
        this.api.observations.get(restored.body["commandId"]!) || null,
      );
      this.error.set("");
      this.notice.set("Envelope restored. Find result does not execute it.");
    } catch (e) {
      this.error.set(String(e));
    }
  }
  clearCommand(): void {
    this.api.clearCommands();
    this.envelope = "";
  }
  selectCommand(id: string): void {
    this.api.buffer.select(id);
    this.result.set(this.api.observations.get(id) || null);
  }
  async dispatch(): Promise<void> {
    await this.run(async () => {
      this.notice.set(
        JSON.stringify(
          await this.api.call("/events/dispatch", "POST", {
            limit: this.pageSize,
          }),
        ),
      );
    });
    if (!this.error()) await this.load();
  }
  async redeliver(): Promise<void> {
    await this.run(async () => {
      this.notice.set(
        JSON.stringify(
          await this.api.call(`/events/${this.id}/redeliver`, "POST"),
        ),
      );
    });
    if (!this.error()) await this.load();
  }
}
@Component({
  selector: "app-root",
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: ` <aside>
      <a class="brand" routerLink="/my-tasks"
        >Workflow<span>Local sandbox console</span></a
      >
      <p>{{ api.session()?.actorRef }}</p>
      <nav>
        <a routerLink="/my-tasks">My tasks</a
        ><a routerLink="/commands">Command recovery</a>
        @if (api.admin) {
          <a routerLink="/definitions">Definitions</a
          ><a routerLink="/catalog">Task types</a
          ><a routerLink="/requests">Requests & start</a
          ><a routerLink="/audit">Management audit</a
          ><a routerLink="/events">Event outbox</a
          ><a routerLink="/inbox">Local inbox</a
          ><a routerLink="/capabilities">Capabilities</a>
        }
      </nav>
      <button (click)="api.logout()">Sign out</button>
    </aside>
    <main>
      <p class="development">
        Development console · writes persist in the configured database
      </p>
      @if (failing()) {
        <div class="error">Startup failure mode active: {{ flags() }}</div>
      }
      <router-outlet></router-outlet>
    </main>`,
})
class App {
  api = inject(ConsoleApi);
  constructor() {
    void this.api.initialize().catch(() => (location.href = "/console/login"));
  }
  flags(): string {
    return Object.entries(this.api.capabilities()?.failureFlags || {})
      .filter(([, v]) => v)
      .map(([k]) => k)
      .join(", ");
  }
  failing(): boolean {
    return !!this.flags();
  }
}
bootstrapApplication(App, {
  providers: [
    provideRouter(
      [
        { path: "", redirectTo: "my-tasks", pathMatch: "full" },
        { path: ":page/:id/:sequence", component: ConsolePage },
        { path: ":page/:id", component: ConsolePage },
        { path: ":page", component: ConsolePage },
      ],
      withHashLocation(),
    ),
  ],
}).catch(console.error);
