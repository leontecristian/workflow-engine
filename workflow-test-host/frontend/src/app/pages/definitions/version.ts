import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import { prettyDocument, rawDocument } from "../../command-buffer";
import type {
  VersionDetails,
  DefinitionChange,
  DefinitionCheck,
  ValidationReport,
} from "../../data/dtos";
import { DefinitionGraphComponent } from "./definition-graph";
import { applyFragment, type FragmentEdit } from "./definition-fragment";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-version",
  standalone: true,
  imports: [...PAGE_IMPORTS, DefinitionGraphComponent],
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
  savedDocument = signal("");
  dirty = false;
  lastResponse = signal<DefinitionChange | DefinitionCheck | null>(null);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        if (this.id !== params.get("id")) this.lastResponse.set(null);
        this.savedDocument.set("");
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
      this.document = prettyDocument(d.documentJson);
      this.savedDocument.set(this.document);
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
    await this.run(async () => {
      const savedText = this.document;
      const r = await this.api.saveDocument(
        this.id,
        rawDocument(savedText),
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
      this.savedDocument.set(savedText);
      this.dirty = this.document !== savedText;
      this.notice.set("Draft saved");
    });
  }
  reloadEditor(): void {
    if (!this.dirty || confirm("Discard unsaved document text and reload?"))
      void this.load();
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
