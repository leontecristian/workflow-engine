import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import { rawDocument } from "../../command-buffer";
import type { DefinitionView } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-definitions",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./definitions.html",
})
export class DefinitionsPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
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
  ngOnInit() {
    void this.load();
  }
  async load(cursor?: string) {
    await this.run(async () => {
      const p = await this.api.listDefinitions(cursor, this.pageSize);
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    void this.router.navigate(["/definition", row.id]);
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
  async lookup() {
    await this.run(async () => {
      const r = await this.api.lookupVersion(
        this.lookupKey,
        this.lookupVersion,
      );
      await this.router.navigate(["/version", r.summary.id]);
    });
  }
  async importDraft() {
    await this.run(async () => {
      const r = await this.api.importDraft(rawDocument(this.document));
      this.notice.set(
        r.changed ? "Draft imported" : "Existing identical content reused",
      );
      await this.router.navigate(["/version", r.after.id]);
    });
  }
}
