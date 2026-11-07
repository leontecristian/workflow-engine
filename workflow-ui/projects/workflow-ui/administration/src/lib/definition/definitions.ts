import { Component, signal } from "@angular/core";

import { AdministrationState } from "../support/administration-state";
import { rawDocument } from "@workflow-engine/ui";
import type { DefinitionView } from "@workflow-engine/ui";
import { Input } from "@angular/core";
import { PAGE_IMPORTS } from "../support/page-imports";

@Component({
  selector: "workflow-definitions",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  styleUrl: "../support/administration.scss",
  templateUrl: "./definitions.html",
})
export class WorkflowDefinitionsComponent extends AdministrationState {

  readonly meta = {
    title: "Definitions",
    eyebrow: "WORKFLOW LIBRARY",
    description:
      "Author, validate and publish the workflows that power your processes.",
    icon: "workflow",
  };
  rows = signal<DefinitionView[]>([]);
  next: string | null = null;
  pageSize = 50;
  document = "";
  dirty = false;
  lookupKey = "";
  lookupVersion = "1";
  protected clearContent(){this.rows.set([]);this.next=null;this.document="";this.dirty=false;this.lookupKey="";this.lookupVersion="1";}
  async load(cursor?: string, refresh=false) {
    await this.run(async (signal,current) => {
      const p = await this.api.listDefinitions(cursor, this.pageSize, signal);
      if(!current())return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    },refresh);
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    this.navigate({kind:"definition",id:row.id});
  }
  async upload(event: Event): Promise<void> {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    await this.run(async (signal,current) => {
      if (file.size > 4 * 1024 * 1024)
        throw new Error("Document exceeds 4 MiB upload limit");
      const text=await file.text();
      if(!current())return;
      this.document = rawDocument(text);
      this.dirty = true;
    });
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
    await this.run(async (signal,current) => {
      const r = await this.api.importDraft(rawDocument(this.document),signal);
      if(!current())return;
      this.notice.set(
        r.changed ? "Draft imported" : "Existing identical content reused",
      );
      this.dirty=false;
      this.navigate({kind:"version",id:r.after.id});
    });
  }
}
