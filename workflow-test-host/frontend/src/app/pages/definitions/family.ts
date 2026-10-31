import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { DefinitionView, VersionSummary } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-family",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./family.html",
})
export class FamilyPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Workflow family",
    eyebrow: "DEFINITION MANAGEMENT",
    description: "Manage family metadata and browse every retained version.",
    icon: "workflow",
  };
  rows = signal<VersionSummary[]>([]);
  next: string | null = null;
  pageSize = 50;
  id = "";
  detail = signal<DefinitionView | null>(null);
  name = "";
  description = "";
  filter = "";
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.id = params.get("id")!;
        void this.load();
      });
  }
  async load(cursor?: string) {
    const id = this.id;
    await this.run(async () => {
      const [p, d] = await Promise.all([
        this.api.listVersions(id, this.filter, cursor, this.pageSize),
        this.api.definition(id),
      ]);
      if (this.destroyRef.destroyed || this.id !== id) return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
      this.detail.set(d);
      this.name = d.name;
      this.description = d.description || "";
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    void this.router.navigate(["/version", row.id]);
  }
  async metadata() {
    await this.run(async () => {
      const r = await this.api.saveMetadata(
        this.id,
        this.name,
        this.description || null,
        this.detail()!.lockVersion,
      );
      this.detail.set(r.after);
      this.notice.set(r.changed ? "Metadata saved" : "No change");
    });
  }
}
