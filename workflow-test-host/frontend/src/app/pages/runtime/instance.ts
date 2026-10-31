import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { InstanceView, InstanceGraph } from "../../data/dtos";
import { InstanceGraphComponent } from "./graph/instance-graph";
import { PAGE_IMPORTS } from "../../ui/page-imports";
@Component({
  selector: "console-instance",
  standalone: true,
  imports: [...PAGE_IMPORTS, InstanceGraphComponent],
  templateUrl: "./instance.html",
})
export class InstancePage {
  api = inject(ConsoleApi);
  router = inject(Router);
  busy = signal(false);
  error = signal("");
  notice = signal("");
  private readGeneration = 0;
  readonly meta = {
    title: "Workflow instance",
    eyebrow: "CURRENT RUNTIME STATE",
    description:
      "Inspect the current position, pinned version and authoritative execution state.",
    icon: "workflow",
  };
  id = "";
  detail = signal<InstanceView | null>(null);
  graph = signal<InstanceGraph | null>(null);
  graphError = signal("");
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
  async load() {
    const id = this.id;
    const generation = ++this.readGeneration;
    const current = () =>
      !this.destroyRef.destroyed &&
      this.id === id &&
      this.readGeneration === generation;
    this.busy.set(true);
    this.error.set("");
    this.graph.set(null);
    this.graphError.set("");
    try {
      const d = await this.api.instance(id);
      if (!current()) return;
      this.detail.set(d);
      try {
        const graph = await this.api.instanceGraph(id);
        if (!current()) return;
        this.graph.set(graph);
        this.detail.set(graph.instance);
      } catch (error) {
        if (current())
          this.graphError.set(
            error instanceof Error ? error.message : String(error),
          );
      }
    } catch (error) {
      if (current())
        this.error.set(error instanceof Error ? error.message : String(error));
    } finally {
      if (current()) this.busy.set(false);
    }
  }
}
