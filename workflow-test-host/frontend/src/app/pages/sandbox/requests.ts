import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { ConsoleSession } from '../../data/console-session';
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
  private readonly session=inject(ConsoleSession);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  private hadSession=this.session.viewerScope()!==null;
  private operationGeneration=0;
  private currentScope(scope:object|null){return !this.destroyed&&scope!==null&&scope===this.session.viewerScope();}
  run = async(action:()=>Promise<void>)=>{
    const scope=this.session.viewerScope(),generation=++this.operationGeneration;
    if(!this.currentScope(scope))return;
    this.busy.set(true);this.error.set('');
    try {await action();}
    catch(error){if(this.currentScope(scope)&&generation===this.operationGeneration)this.error.set(error instanceof Error?error.message:String(error));}
    finally {if(this.currentScope(scope)&&generation===this.operationGeneration)this.busy.set(false);}
  };
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
    this.session.identityChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(()=>{
      const firstSession=!this.hadSession&&this.session.viewerScope()!==null;
      const initialRequest=firstSession?[this.requestId,this.requestToken]:null;
      if(this.session.viewerScope()!==null)this.hadSession=true;
      this.versionGeneration++;this.operationGeneration++;this.busy.set(false);this.workflowsLoading.set(false);this.versionsLoading.set(false);
      this.name='';this.requestDataText='';this.workflowInput='';this.familyId='';this.versionId='';this.requestId='';this.requestToken='0';this.inputsText='{}';
      this.rows.set([]);this.workflows.set([]);this.publishedRows.set([]);this.next=null;this.workflowNext=null;this.publishedNext=null;
      this.error.set('');this.notice.set('');this.versionError.set('');this.workflowError.set('');
      if(initialRequest){[this.requestId,this.requestToken]=initialRequest;}
      if(this.session.viewerScope()!==null){void this.load();void this.loadWorkflows();}
    });
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      this.versionGeneration++;this.operationGeneration++;this.busy.set(false);this.workflowsLoading.set(false);this.versionsLoading.set(false);
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
    const scope=this.session.viewerScope();
    if(!this.currentScope(scope))return;
    if (this.workflowsLoading() || (next && !this.workflowNext)) return;
    this.workflowsLoading.set(true);
    this.workflowError.set("");
    try {
      const page = await this.api.listDefinitions(
        next ? this.workflowNext || undefined : undefined, 50,
      );
      if (!this.currentScope(scope)) return;
      this.workflows.set(next ? [...this.workflows(), ...page.items] : page.items);
      this.workflowNext = page.nextAfterId;
    } catch (error) {
      if (this.currentScope(scope)) this.workflowError.set(
        error instanceof Error ? error.message : String(error),
      );
    } finally {
      if (this.currentScope(scope)) this.workflowsLoading.set(false);
    }
  }
  async load(cursor?: string) {
    const scope=this.session.viewerScope();
    await this.run(async () => {
      const p = await this.api.listRequests(cursor, this.pageSize);
      if(!this.currentScope(scope))return;
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
    const scope=this.session.viewerScope();
    await this.run(async () => {
      const r = await this.api.createRequest(this.name, this.requestDataText.trim() ? this.requestDataText : null);
      if(!this.currentScope(scope))return;
      this.requestId = r.id;
      this.requestToken = r.lockVersion;
      this.notice.set("Request " + r.id + " created");
    });
    if (this.currentScope(scope)&&!this.error()) await this.load();
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
    const scope=this.session.viewerScope();
    try { await this.commands.execute(replay); }
    catch(error){if(this.destroyed||scope!==this.session.viewerScope())return;throw error;}
    if(this.destroyed||scope!==this.session.viewerScope())return;
    this.notice.set(
      replay
        ? "Replay-only lookup returned; no new command was executed."
        : "Command returned after commit. Inspect current state separately.",
    );
    await this.router.navigate(["/commands"]);
  }
}
