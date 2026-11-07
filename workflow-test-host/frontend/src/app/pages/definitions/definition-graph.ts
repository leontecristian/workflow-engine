import { Component, computed, input, output, signal } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { MatInputModule } from "@angular/material/input";
import { applyFragment, selectedObjectJson, type FragmentEdit, type ObjectKind } from "./definition-fragment";
import { MatButtonModule } from "@angular/material/button";
import { definitionTopology } from "./definition-topology";

@Component({
  selector: "console-definition-graph",
  standalone: true,
  imports: [MatButtonModule, FormsModule, MatInputModule],
  templateUrl: "./definition-graph.html",
  styleUrl: "./definition-graph.scss",
  styles: [
    ".node { cursor: pointer; } .node.selected rect { stroke: #5b45b8; stroke-width: 3; }",
    ".object-json { width: 100%; box-sizing: border-box; font-family: monospace; }",
    ".edge { cursor: pointer; } .edge:focus { outline: none; }",
    ".edge:hover > path:not(.edge-hit), .edge:focus > path:not(.edge-hit), .edge.selected > path:not(.edge-hit) { stroke: #5b45b8; stroke-width: 3; }",
    ".edge .edge-hit { stroke: transparent; stroke-width: 18; pointer-events: stroke; }",
    ".transition-details pre { white-space: pre-wrap; overflow-wrap: anywhere; }",
  ],
})
export class DefinitionGraphComponent {
  document = input.required<string>();
  editable = input(false);
  fragmentApplied = output<FragmentEdit>();
  objectSelection = signal<{source: string; key: string; kind: ObjectKind} | null>(null);
  fragmentText = "";
  fragmentError = signal("");
  selectedObject = computed(() => {
    const selected = this.objectSelection();
    return selected?.source === this.document() ? selected : null;
  });
  diagram = computed(() => definitionTopology(this.document()));
  selection = signal<{ source: string; key: string } | null>(null);
  selectedTransition = computed(() => {
    const selected = this.selection();
    return selected?.source === this.document()
      ? this.diagram()?.transitions.find(transition => transition.key === selected.key) ?? null
      : null;
  });
  selectTransition(key: string) {
    this.selection.set({ source: this.document(), key });
    this.selectObject("transitions", key);
  }
  selectObject(kind: ObjectKind, key: string) {
    this.fragmentError.set("");
    try {
      this.fragmentText = selectedObjectJson(this.document(), kind, key);
      this.objectSelection.set({ source: this.document(), key, kind });
      if (kind === "nodes") this.selection.set(null);
    } catch {
      this.objectSelection.set(null);
      this.fragmentError.set("Cannot inspect this object. Check the full JSON for duplicate or invalid properties.");
    }
  }
  applyObject() {
    const selected = this.selectedObject();
    if (!selected || !this.editable()) return;
    this.fragmentError.set("");
    const edit = { ...selected, replacement: this.fragmentText };
    try {
      applyFragment(selected.source, edit);
      this.fragmentApplied.emit(edit);
    } catch (failure) {
      this.fragmentError.set(failure instanceof Error ? failure.message : "Invalid object JSON.");
    }
  }
  clearSelection() {
    this.selection.set(null);
    this.objectSelection.set(null);
    this.fragmentError.set("");
  }
  readonly defaultZoom = 0.5;
  zoom = signal(this.defaultZoom);
  changeZoom(delta: number) {
    this.zoom.update(value => Math.min(2, Math.max(0.25, Math.round((value + delta) * 100) / 100)));
  }
  zoomPercent() { return Math.round(this.zoom() * 100); }
  shortName(name: string) {
    const points = Array.from(name);
    return points.length > 26 ? points.slice(0, 25).join("") + "…" : name;
  }
}
