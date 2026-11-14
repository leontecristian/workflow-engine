import { Component, signal } from "@angular/core";


import { AdministrationState } from "../support/administration-state";
import type { DefinitionView, VersionSummary } from "@workflow-engine/ui";
import { Input } from "@angular/core";
import { PAGE_IMPORTS } from "../support/page-imports";
import { ConsoleRecords } from "../support/records";
import type { MatSelect } from "@angular/material/select";

@Component({
  selector: "workflow-definition-family",
  standalone: true,
  imports: [...PAGE_IMPORTS, ConsoleRecords],
  styleUrls: ["../support/administration.scss", "./family.scss"],
  templateUrl: "./family.html",
})
export class WorkflowDefinitionFamilyComponent extends AdministrationState {

  readonly meta = {
    title: "Workflow Definition",
    eyebrow: "DEFINITION MANAGEMENT",
    description: "Manage family metadata and browse every retained version.",
    icon: "account_tree",
  };
  rows = signal<VersionSummary[]>([]);
  next: string | null = null;
  pageSize = 50;
  private pageCursors: (string | undefined)[] = [undefined];
  pageIndex = 0;
  @Input({required:true}) definitionId="";
  get id(){return this.definitionId;}
  detail = signal<DefinitionView | null>(null);
  name = "";
  description = "";
  filter = "";
  protected clearContent(){this.rows.set([]);this.next=null;this.pageCursors=[undefined];this.pageIndex=0;this.detail.set(null);this.name="";this.description="";}
  async load(cursor?: string, refresh=false) {
    await this.loadPage(cursor, [cursor], 0, refresh);
  }
  private async loadPage(cursor: string | undefined, cursors: (string | undefined)[], index: number,
    refresh=false, size=this.pageSize, filter=this.filter) {
    if (this.busy()) return;
    const id = this.id;
    await this.run(async (signal,current) => {
      const [p, d] = await Promise.all([
        this.api.listVersions(id, filter, cursor, size, signal),
        this.api.definition(id,signal),
      ]);
      if (!current()) return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
      this.pageCursors = cursors;
      this.pageIndex = index;
      this.pageSize = size;
      this.filter = filter;
      this.detail.set(d);
      this.name = d.name;
      this.description = d.description || "";
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
  async changeFilter(filter: string, select?: MatSelect) {
    await this.loadPage(undefined, [undefined], 0, false, this.pageSize, filter);
    if (select) select.value = this.filter;
  }
  open(row: { id: string }) {
    this.navigate({kind:"version",id:row.id});
  }
  async metadata() {
    await this.run(async (signal,current) => {
      const r = await this.api.saveMetadata(
        this.id,
        this.name,
        this.description || null,
        this.detail()!.lockVersion, signal,
      );
      if(!current())return;
      this.detail.set(r.after);
      this.notice.set(r.changed ? "Metadata saved" : "No change");
    });
  }
}
