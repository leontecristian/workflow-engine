import { Component, signal } from "@angular/core";


import { AdministrationState } from "../support/administration-state";
import type { DefinitionView, VersionSummary } from "@workflow-engine/ui";
import { Input } from "@angular/core";
import { PAGE_IMPORTS } from "../support/page-imports";

@Component({
  selector: "workflow-definition-family",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  styleUrl: "../support/administration.scss",
  templateUrl: "./family.html",
})
export class WorkflowDefinitionFamilyComponent extends AdministrationState {

  readonly meta = {
    title: "Workflow family",
    eyebrow: "DEFINITION MANAGEMENT",
    description: "Manage family metadata and browse every retained version.",
    icon: "workflow",
  };
  rows = signal<VersionSummary[]>([]);
  next: string | null = null;
  pageSize = 50;
  @Input({required:true}) definitionId="";
  get id(){return this.definitionId;}
  detail = signal<DefinitionView | null>(null);
  name = "";
  description = "";
  filter = "";
  protected clearContent(){this.rows.set([]);this.next=null;this.detail.set(null);this.name="";this.description="";}
  async load(cursor?: string, refresh=false) {
    const id = this.id;
    await this.run(async (signal,current) => {
      const [p, d] = await Promise.all([
        this.api.listVersions(id, this.filter, cursor, this.pageSize, signal),
        this.api.definition(id,signal),
      ]);
      if (!current()) return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
      this.detail.set(d);
      this.name = d.name;
      this.description = d.description || "";
    },refresh);
  }
  nextPage() {
    if (this.next) void this.load(this.next);
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
