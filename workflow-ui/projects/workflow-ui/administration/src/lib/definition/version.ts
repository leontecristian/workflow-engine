import { Component, Injector, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import type { MatDialogRef } from "@angular/material/dialog";

import { AdministrationState } from "../support/administration-state";
import { prettyDocument, rawDocument } from "@workflow-engine/ui";
import type {
  VersionDetails,
  DefinitionChange,
  DefinitionCheck,
  ValidationReport,
} from "@workflow-engine/ui";
import { DefinitionGraphComponent } from "./definition-graph";
import { applyFragment, type FragmentEdit } from "./definition-fragment";
import { Input } from "@angular/core";
import { PAGE_IMPORTS } from "../support/page-imports";

import type { ReloadDefinitionDialog } from "./reload-dialog";
@Component({
  selector: "workflow-definition-version",
  standalone: true,
  imports: [...PAGE_IMPORTS, DefinitionGraphComponent],
  styleUrl: "../support/administration.scss",
  templateUrl: "./version.html",
})
export class WorkflowDefinitionVersionComponent extends AdministrationState {

  private readonly injector = inject(Injector);
  private openingReloadDialog = false;
  private reloadDialog?: MatDialogRef<ReloadDefinitionDialog, boolean>;
  readonly meta = {
    title: "Definition version",
    eyebrow: "AUTHORING WORKSPACE",
    description:
      "Edit raw content, inspect validation and manage publication explicitly.",
    icon: "code",
  };
  @Input({required:true}) versionId="";
  get id(){return this.versionId;}
  detail = signal<VersionDetails | null>(null);
  document = "";
  savedDocument = signal("");
  dirty = false;
  lastResponse = signal<DefinitionChange | DefinitionCheck | null>(null);
  protected override onInvalidate(){this.reloadDialog?.close();this.reloadDialog=undefined;this.openingReloadDialog=false;}
  protected clearContent(){this.detail.set(null);this.document="";this.savedDocument.set("");this.dirty=false;this.lastResponse.set(null);}
  async load(_cursor?:string,refresh=false) {
    const id = this.id;
    await this.run(async (signal,current) => {
      const d = await this.api.version(id,signal);
      if (!current()) return;
      this.detail.set(d);
      this.document = prettyDocument(d.documentJson);
      this.savedDocument.set(this.document);
      this.dirty = false;
    },refresh);
  }
  validationReport(): ValidationReport | null {
    const evidence = this.detail()?.validationDetailsJson;
    if (!evidence) return null;
    try {
      return (
        JSON.parse(evidence) as {
          report: ValidationReport;
        }
      ).report;
    } catch {
      return null;
    }
  }
  prettyPrint(): void {
    this.error.set("");
    this.notice.set("");
    try {
      const formatted = prettyDocument(this.document);
      if (formatted !== this.document) {
        this.document = formatted;
        if (this.detail()?.summary.status === "DRAFT") this.dirty = true;
      }
    } catch {
      this.error.set("Cannot pretty print invalid JSON. Fix the document and try again.");
    }
  }
  async saveDocument() {
    await this.run(async (signal,current) => {
      const savedText = this.document;
      const r = await this.api.saveDocument(
        this.id,
        rawDocument(savedText),
        this.detail()!.summary.lockVersion, signal,
      );
      if(!current())return;
      this.detail.update((d) =>
        d
          ? {
              ...d,
              summary: r.after,
              validationDetailsJson: r.changed ? null : d.validationDetailsJson,
            }
          : d,
      );
      this.lastResponse.set(r);
      this.savedDocument.set(savedText);
      this.dirty = this.document !== savedText;
      this.notice.set("Draft saved");
    });
  }
  async reloadEditor(): Promise<void> {
    if (this.busy() || this.reloadDialog || this.openingReloadDialog) return;
    if (!this.dirty) {
      void this.load();
      return;
    }
    const id = this.id;
    const current=this.capture();
    this.openingReloadDialog = true;
    try {
      const { MatDialog, ReloadDefinitionDialog } = await import("./reload-dialog");
      if (!current() || this.id !== id) return;
      const dialog = this.injector.get(MatDialog).open<ReloadDefinitionDialog, undefined, boolean>(
        ReloadDefinitionDialog,
        { width: "440px", maxWidth: "calc(100vw - 32px)", autoFocus: "first-tabbable" },
      );
      this.reloadDialog = dialog;
      dialog.afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((discard) => {
        if(this.reloadDialog===dialog)this.reloadDialog = undefined;
        if (discard === true && current() && this.id === id) void this.load();
      });
    } catch {
      if (current() && this.id === id)
        this.error.set("Cannot open the reload confirmation. Your unsaved text is unchanged. Try again.");
    } finally {
      if(current())this.openingReloadDialog = false;
    }
  }
  applySelectedObject(edit: FragmentEdit): void {
    this.error.set("");
    if (this.busy() || this.detail()?.summary.status !== "DRAFT") return;
    try {
      this.document = applyFragment(this.document, edit);
      this.dirty = true;
      this.notice.set("Selected JSON applied to the editor. Save draft to persist it.");
    } catch (failure) {
      this.error.set(failure instanceof Error ? failure.message : "Cannot apply the selected JSON.");
    }
  }
  async exportDocument(){await this.run(async(signal,current)=>{const text=await this.api.exportVersion(this.id,signal);if(!current())return;const url=URL.createObjectURL(new Blob([text],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download='workflow-version-'+this.id+'.json';link.click();URL.revokeObjectURL(url);});}
  async lifecycle(action: "validate" | "publish" | "retire") {
    const success=await this.run(async (signal,current) => {
      const token = this.detail()!.summary.lockVersion;
      if (action === "retire") {
        const r = await this.api.retireVersion(this.id, token,signal);
        if(!current())return;
        this.lastResponse.set(r);
        this.detail.update((d) => (d ? { ...d, summary: r.after } : d));
        this.notice.set("Retirement changed=" + r.changed);
      } else {
        const r = await this.api.checkVersion(this.id, action, token,signal);
        if(!current())return;
        this.lastResponse.set(r);
        this.detail.update((d) => (d ? { ...d, summary: r.change.after } : d));
        this.notice.set(
          "Validation " +
            (r.report.valid ? "PASSED" : "FAILED") +
            "; published=" +
            r.published,
        );
      }
    });
    if (success) await this.load(undefined,true);
  }
}
