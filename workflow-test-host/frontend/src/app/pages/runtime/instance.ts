import { destroyedState } from "../../ui/page-state";
import { Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { findNodeAtLocation, parseTree } from "jsonc-parser";
import { prettyDocument } from "../../command-buffer";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { InstanceView, InstanceGraph } from "../../data/dtos";
import { InstanceGraphComponent } from "./graph/instance-graph";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { downloadJson } from "../../ui/download-json";
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
  variables = signal<import("../../data/dtos").VariablePage|null>(null);
  variableDisplays = computed(() => {
    const page = this.variables();
    if (!page) return [];
    const source = page.exactJson;
    const tree = source ? parseTree(source) : undefined;
    return page.items.map((item, index) => {
      const node = tree && findNodeAtLocation(tree, ["items", index, "value"]);
      let displayValue = "No stored value";
      if (item.initialized) {
        if (item.value === null) displayValue = "null";
        else if (item.type !== "JSON" && typeof item.value === "string") displayValue = item.value;
        else if (node && source) {
          // Slice the original response: JS number conversion would lose precision and scale.
          displayValue = prettyDocument(source.slice(node.offset, node.offset + node.length));
        } else if (typeof item.value === "boolean") displayValue = String(item.value);
        else displayValue = "Value unavailable; refresh variables.";
      }
      return { ...item, displayValue };
    });
  });
  variableError = signal("");
  effects = signal<import("../../data/dtos").Page<import("../../data/dtos").ServiceEffect>|null>(null);
  effectError = signal("");
  private variableGeneration=0;
  exportVariables() {
    const text=this.variables()?.exactJson;
    if(text) downloadJson(text,"workflow-variables.json");
  }
  async loadVariables(next=false) {
    const id=this.id, generation=++this.variableGeneration;
    const previous=this.variables(); this.variableError.set("");
    try {
      const result=await this.api.variables(id,next ? previous?.nextAfterKey || undefined : undefined,next ? previous?.lockVersion : undefined);
      if(!this.isDestroyed() && id===this.id && generation===this.variableGeneration) this.variables.set(result);
    } catch(error) {
      if(!this.isDestroyed() && id===this.id && generation===this.variableGeneration) {
        this.variables.set(null); this.variableError.set(String(error)+". Refresh variables to restart paging.");
      }
    }
  }
  async loadEffects(next=false) {
    const id=this.id; this.effectError.set("");
    try {
      const result=await this.api.serviceEffects(id,next ? this.effects()?.nextAfterId || undefined : undefined);
      if(!this.isDestroyed() && id===this.id) this.effects.set(result);
    } catch(error) { if(id===this.id) this.effectError.set(String(error)); }
  }
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isDestroyed = destroyedState(this.destroyRef);
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
      !this.isDestroyed() &&
      this.id === id &&
      this.readGeneration === generation;
    this.busy.set(true);
    this.error.set("");
    this.graph.set(null);
    this.graphError.set("");
    this.variableGeneration++; this.variables.set(null); this.effects.set(null); this.variableError.set(""); this.effectError.set("");
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
