import { Component, Injector, TemplateRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { MatDialog, MatDialogModule, MatDialogRef } from "@angular/material/dialog";
import type { MatSelect } from "@angular/material/select";

import { AdministrationState } from "../support/administration-state";
import { rawDocument } from "@workflow-engine/ui";
import type { DefinitionView } from "@workflow-engine/ui";
import { PAGE_IMPORTS } from "../support/page-imports";
import { ConsoleRecords } from "../support/records";

@Component({
  selector: "workflow-definitions",
  standalone: true,
  imports: [...PAGE_IMPORTS, MatDialogModule, ConsoleRecords],
  styleUrls: ["../support/administration.scss", "./definitions.scss"],
  templateUrl: "./definitions.html",
})
export class WorkflowDefinitionsComponent extends AdministrationState {
  private readonly injector = inject(Injector);
  private importDialog?: MatDialogRef<unknown>;
  readonly importDialogOpen = signal(false);
  readonly meta = {
    title: "Definitions",
    eyebrow: "WORKFLOW LIBRARY",
    description:
      "Author, validate and publish the workflows that power your processes.",
    icon: "account_tree",
  };
  rows = signal<DefinitionView[]>([]);
  next: string | null = null;
  pageSize = 50;
  private pageCursors: (string | undefined)[] = [undefined];
  pageIndex = 0;
  document = "";
  dirty = false;
  lookupKey = "";
  lookupVersion = "1";
  protected override onInvalidate() {
    this.importDialog?.close();
    this.importDialog = undefined;
    this.importDialogOpen.set(false);
  }
  openImport(template: TemplateRef<unknown>) {
    if (this.busy() || this.importDialog || this.sessionScope === null) return;
    try {
      const dialog = this.injector.get(MatDialog).open(template, {
        width: "800px", maxWidth: "calc(100vw - 32px)", maxHeight: "calc(100dvh - 32px)",
        autoFocus: "first-tabbable",
      });
      this.importDialog = dialog;
      this.importDialogOpen.set(true);
      dialog.afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        if (this.importDialog !== dialog) return;
        this.importDialog = undefined;
        this.importDialogOpen.set(false);
      });
    } catch {
      this.error.set("Cannot open the import form. Your document text is unchanged. Try again.");
    }
  }
  closeImport() { if (!this.busy()) this.importDialog?.close(); }
  protected clearContent(){this.rows.set([]);this.next=null;this.pageCursors=[undefined];this.pageIndex=0;this.document="";this.dirty=false;this.lookupKey="";this.lookupVersion="1";}
  async load(cursor?: string, refresh=false) {
    await this.loadPage(cursor, [cursor], 0, refresh);
  }
  private async loadPage(cursor: string | undefined, cursors: (string | undefined)[], index: number,
    refresh=false, size=this.pageSize) {
    if (this.busy()) return;
    await this.run(async (signal,current) => {
      const p = await this.api.listDefinitions(cursor, size, signal);
      if(!current())return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
      this.pageCursors = cursors;
      this.pageIndex = index;
      this.pageSize = size;
    },refresh);
  }
  async nextPage() {
    if (!this.next || this.busy()) return;
    await this.loadPage(this.next, [...this.pageCursors.slice(0, this.pageIndex + 1), this.next], this.pageIndex + 1);
  }
  async previousPage() {
    if (this.pageIndex === 0 || this.busy()) return;
    await this.loadPage(this.pageCursors[this.pageIndex - 1], this.pageCursors, this.pageIndex - 1);
  }
  async changePageSize(size: number, select?: MatSelect) {
    await this.loadPage(undefined, [undefined], 0, false, size);
    if (select) select.value = this.pageSize;
  }
  open(row: { id: string }) {
    this.navigate({kind:"definition",id:row.id});
  }
  async upload(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    input.value = "";
    const dialog = this.importDialog;
    if (dialog) dialog.disableClose = true;
    try { await this.run(async (signal,current) => {
      if (file.size > 4 * 1024 * 1024)
        throw new Error("Document exceeds 4 MiB upload limit");
      const text=await file.text();
      if(!current())return;
      this.document = rawDocument(text);
      this.dirty = true;
    }); } finally {
      if (dialog && this.importDialog === dialog) dialog.disableClose = false;
    }
  }
  async lookup() {
    await this.run(async (signal,current) => {
      const r = await this.api.lookupVersion(
        this.lookupKey,
        this.lookupVersion, signal,
      );
      if(current())this.navigate({kind:"version",id:r.summary.id});
    });
  }
  async importDraft() {
    const dialog = this.importDialog;
    if (dialog) dialog.disableClose = true;
    try { await this.run(async (signal,current) => {
      const r = await this.api.importDraft(rawDocument(this.document),signal);
      if(!current())return;
      this.notice.set(
        r.changed ? "Draft imported" : "Existing identical content reused",
      );
      this.dirty=false;
      dialog?.close();
      this.navigate({kind:"version",id:r.after.id});
    }); } finally {
      if (dialog && this.importDialog === dialog) dialog.disableClose = false;
    }
  }
}
