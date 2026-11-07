import { Component, signal } from "@angular/core";

import { AdministrationState } from "../support/administration-state";
import type { TaskTypeView } from "@workflow-engine/ui";
import { Input } from "@angular/core";
import { PAGE_IMPORTS } from "../support/page-imports";

@Component({
  selector: "workflow-catalog",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  styleUrl: "../support/administration.scss",
  templateUrl: "./catalog.html",
})
export class WorkflowCatalogComponent extends AdministrationState {

  readonly meta = {
    title: "Task types",
    eyebrow: "CONFIGURATION",
    description:
      "Manage the retained task catalog and its authoring selectability.",
    icon: "catalog",
  };
  rows = signal<TaskTypeView[]>([]);
  next: string | null = null;
  pageSize = 50;
  filter = "ALL";
  name = "";
  description = "";
  @Input() initialCode = "";
  code = "";
  selectable = true;
  editing: TaskTypeView | null = null;
  protected clearContent() { this.rows.set([]); this.next=null; this.editing=null; this.name=""; this.description=""; this.code=this.initialCode; this.selectable=true; }
  async load(cursor?: string, refresh=false) {
    await this.run(async (signal,current) => {
      const p = await this.api.listTaskTypes(
        this.filter,
        cursor,
        this.pageSize, signal,
      );
      if(!current())return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    }, refresh);
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: TaskTypeView) {
    this.editing = row;
    this.code = row.code;
    this.name = row.name;
    this.description = row.description || "";
    this.selectable = row.selectable;
  }
  async saveCatalog() {
    const success=await this.run(async (signal,current) => {
      const body = {
        name: this.name,
        description: this.description || null,
        selectable: this.selectable,
      };
      const change = this.editing
        ? await this.api.updateTaskType(
            this.editing.id,
            body,
            this.editing.lockVersion, signal,
          )
        : await this.api.createTaskType({ ...body, code: this.code }, signal);
      if(!current())return;
      this.editing = change.after;
      this.notice.set("Catalog saved");
    });
    if (success) await this.load(undefined,true);
  }
}
