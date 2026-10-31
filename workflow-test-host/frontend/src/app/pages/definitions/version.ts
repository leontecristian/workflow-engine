import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import { rawDocument } from "../../command-buffer";
import type {
  VersionDetails,
  DefinitionChange,
  DefinitionCheck,
  ValidationReport,
} from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-version",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./version.html",
})
export class VersionPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Definition version",
    eyebrow: "AUTHORING WORKSPACE",
    description:
      "Edit raw content, inspect validation and manage publication explicitly.",
    icon: "code",
  };
  id = "";
  detail = signal<VersionDetails | null>(null);
  document = "";
  dirty = false;
  lastResponse = signal<DefinitionChange | DefinitionCheck | null>(null);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        if (this.id !== params.get("id")) this.lastResponse.set(null);
        this.id = params.get("id")!;
        void this.load();
      });
  }
  async load() {
    const id = this.id;
    await this.run(async () => {
      const d = await this.api.version(id);
      if (this.destroyRef.destroyed || this.id !== id) return;
      this.detail.set(d);
      this.document = d.documentJson;
      this.dirty = false;
    });
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
  async saveDocument() {
    await this.run(async () => {
      const r = await this.api.saveDocument(
        this.id,
        rawDocument(this.document),
        this.detail()!.summary.lockVersion,
      );
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
      this.dirty = false;
      this.notice.set("Draft saved");
    });
  }
  reloadEditor(): void {
    if (!this.dirty || confirm("Discard unsaved document text and reload?"))
      void this.load();
  }
  async lifecycle(action: "validate" | "publish" | "retire") {
    await this.run(async () => {
      const token = this.detail()!.summary.lockVersion;
      if (action === "retire") {
        const r = await this.api.retireVersion(this.id, token);
        this.lastResponse.set(r);
        this.detail.update((d) => (d ? { ...d, summary: r.after } : d));
        this.notice.set("Retirement changed=" + r.changed);
      } else {
        const r = await this.api.checkVersion(this.id, action, token);
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
    if (!this.error()) await this.load();
  }
}
