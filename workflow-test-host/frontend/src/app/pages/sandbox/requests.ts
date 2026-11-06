import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { ConsoleApi } from "../../data/console-api";
import { CommandRecovery } from "../../data/command-recovery";
import { exactDecimal, parseScalarInputs } from "../../command-buffer";
import type { DefinitionView, VersionSummary, SandboxRequestSummary } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-requests",
  standalone: true,
  imports: [...PAGE_IMPORTS, MatAutocompleteModule],
  templateUrl: "./requests.html",
  styles: [
    `.start-form { display: flex; flex-direction: column; gap: 16px; }
     .start-form > button { align-self: flex-start; }
     .start-form .field-note { margin: 0; }`,
  ],
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
  rows = signal<SandboxRequestSummary[]>([]);
  next: string | null = null;
  pageSize = 50;
  name = "";
  requestDataText = "";
  familyId = "";
  workflowInput: string | DefinitionView = "";
  workflows = signal<DefinitionView[]>([]);
  workflowNext: string | null = null;
  workflowsLoading = signal(false);
  workflowError = signal("");
  versionsLoading = signal(false);
  versionError = signal("");
  private versionGeneration = 0;
  private destroyed = false;
  versionId = "";
  requestId = "";
  requestToken = "0";
  inputsText = "{}";
  publishedRows = signal<VersionSummary[]>([]);
  publishedNext: string | null = null;
  commands = inject(CommandRecovery);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      this.versionGeneration++;
    });
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
    void this.loadWorkflows();
  }
  workflowLabel = (workflow: DefinitionView | string | null): string =>
    typeof workflow === "object" && workflow
      ? `${workflow.name} · ${workflow.key}`
      : workflow || "";
  matchingWorkflows(): DefinitionView[] {
    const query = typeof this.workflowInput === "string"
      ? this.workflowInput.trim().toLowerCase() : "";
    return this.workflows().filter((workflow) =>
      `${workflow.name} ${workflow.key}`.toLowerCase().includes(query),
    );
  }
  workflowEdited(value: string | DefinitionView) {
    this.workflowInput = value;
    this.familyId = "";
    this.versionGeneration++;
    this.versionId = "";
    this.publishedRows.set([]);
    this.publishedNext = null;
    this.versionsLoading.set(false);
    this.versionError.set("");
  }
  async selectWorkflow(workflow: DefinitionView) {
    this.workflowInput = workflow;
    this.familyId = workflow.id;
    await this.loadPublished();
  }
  async loadWorkflows(next = false) {
    if (this.workflowsLoading() || (next && !this.workflowNext)) return;
    this.workflowsLoading.set(true);
    this.workflowError.set("");
    try {
      const page = await this.api.listDefinitions(
        next ? this.workflowNext || undefined : undefined, 50,
      );
      if (this.destroyed) return;
      this.workflows.set(next ? [...this.workflows(), ...page.items] : page.items);
      this.workflowNext = page.nextAfterId;
    } catch (error) {
      if (!this.destroyed) this.workflowError.set(
        error instanceof Error ? error.message : String(error),
      );
    } finally {
      if (!this.destroyed) this.workflowsLoading.set(false);
    }
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
      const r = await this.api.createRequest(this.name, this.requestDataText.trim() ? this.requestDataText : null);
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
          inputs: parseScalarInputs(this.inputsText),
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
    if (!this.familyId || (next && (!this.publishedNext || this.versionsLoading()))) return;
    const generation = ++this.versionGeneration;
    const familyId = this.familyId;
    const cursor = next ? this.publishedNext || undefined : undefined;
    if (!next) {
      this.versionId = "";
      this.publishedRows.set([]);
      this.publishedNext = null;
    }
    this.versionsLoading.set(true);
    this.versionError.set("");
    try {
      const p = await this.api.listVersions(
        exactDecimal(familyId),
        "PUBLISHED",
        cursor,
        50,
      );
      if (generation !== this.versionGeneration || this.destroyed) return;
      this.publishedRows.set(next ? [...this.publishedRows(), ...p.items] : p.items);
      this.publishedNext = p.nextAfterId;
      if (!next && p.items.length === 1 && !p.nextAfterId) this.versionId = p.items[0].id;
    } catch (error) {
      if (generation === this.versionGeneration && !this.destroyed) this.versionError.set(
        error instanceof Error ? error.message : String(error),
      );
    } finally {
      if (generation === this.versionGeneration && !this.destroyed) this.versionsLoading.set(false);
    }
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
