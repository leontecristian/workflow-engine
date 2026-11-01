import { Component, computed, input, signal } from "@angular/core";
import { MatButtonModule } from "@angular/material/button";
import { layoutGraph } from "./graph-layout";
import { RouterLink } from "@angular/router";
import type { InstanceGraph, InstanceGraphNode } from "../../../data/dtos";

@Component({
  selector: "console-instance-graph",
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  templateUrl: "./instance-graph.html",
  styleUrl: "./instance-graph.scss",
})
export class InstanceGraphComponent {
  graph = input.required<InstanceGraph>();

  layout = computed(() => layoutGraph(this.graph()));
  zoom = signal(1);
  changeZoom(delta: number) {
    this.zoom.update(value => Math.min(2, Math.max(0.25, Math.round((value + delta) * 100) / 100)));
  }
  zoomPercent() { return Math.round(this.zoom() * 100); }
  arrowId(traversed: boolean) { return "workflow-" + this.graph().instance.id + (traversed ? "-visited" : "-pending"); }
  arrow(traversed: boolean) { return "url(#" + this.arrowId(traversed) + ")"; }
  nodeType(node: InstanceGraphNode): string { return this.shortName(node.type.replaceAll("_", " ")); }
  route(node: InstanceGraphNode): string[] {
    return node.taskId
      ? ["/task", node.taskId]
      : node.activityId ? ["/activity", node.activityId] : ["/history", this.graph().instance.id];
  }
  label(node: InstanceGraphNode): string {
    if (node.state === "ACTIVE") return "Current step";
    if (node.state === "COMPLETED")
      return node.key === this.graph().instance.currentNodeKey &&
        this.graph().instance.status === "COMPLETED"
        ? "Completed · final position"
        : "Completed";
    return "Not reached";
  }
  shortName(name: string): string {
    const points = Array.from(name);
    return points.length > 26 ? points.slice(0, 25).join("") + "…" : name;
  }
  accessibleName(node: InstanceGraphNode): string {
    return `${node.name} (${node.key}, ${node.type}), ${this.label(node)}. Open ${node.taskId ? "task" : node.activityId ? "activity" : "history"}.`;
  }
}
