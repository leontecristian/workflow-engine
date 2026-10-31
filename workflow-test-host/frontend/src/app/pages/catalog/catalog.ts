import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { TaskTypeView } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-catalog",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./catalog.html",
})
export class CatalogPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
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
  code = "SANDBOX_REVIEW";
  selectable = true;
  editing: TaskTypeView | null = null;
  ngOnInit() {
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.listTaskTypes(
        this.filter,
        cursor,
        this.pageSize,
      );
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
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
    await this.run(async () => {
      const body = {
        name: this.name,
        description: this.description || null,
        selectable: this.selectable,
      };
      const change = this.editing
        ? await this.api.updateTaskType(
            this.editing.id,
            body,
            this.editing.lockVersion,
          )
        : await this.api.createTaskType({ ...body, code: this.code });
      this.editing = change.after;
      this.notice.set("Catalog saved");
    });
    if (!this.error()) await this.load();
  }
}
