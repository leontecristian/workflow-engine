import { WORKFLOW_VIEWER_NAVIGATION, DEFAULT_VIEWER_MESSAGES } from '../contracts';
import type { WorkflowViewerMessages } from '../contracts';
import { Component, computed, input, signal, inject } from "@angular/core";
import { MatButtonModule } from "@angular/material/button";
import { layoutGraph } from "./graph-layout";
import { RouterLink } from "@angular/router";
import type { InstanceGraph, InstanceGraphNode } from "../models";

@Component({
  selector: "workflow-instance-graph",
  standalone: true,
  imports: [RouterLink, MatButtonModule],
  templateUrl: "./instance-graph.html",
  styleUrl: "./instance-graph.scss",
})
export class WorkflowInstanceGraphComponent {
  graph = input.required<InstanceGraph>();
  messages = input<WorkflowViewerMessages>(DEFAULT_VIEWER_MESSAGES);
  readonly navigation = inject(WORKFLOW_VIEWER_NAVIGATION, {optional: true});
  private readonly markerId = 'workflow-' + crypto.randomUUID();

  layout = computed(() => layoutGraph(this.graph()));
  readonly defaultZoom = 0.5;
  zoom = signal(this.defaultZoom);
  changeZoom(delta: number) {
    this.zoom.update(value => Math.min(2, Math.max(0.25, Math.round((value + delta) * 100) / 100)));
  }
  zoomPercent() { return Math.round(this.zoom() * 100); }
  arrowId(traversed: boolean) { return this.markerId + (traversed ? "-visited" : "-pending"); }
  arrow(traversed: boolean) { return "url(#" + this.arrowId(traversed) + ")"; }
  nodeType(node: InstanceGraphNode): string { return this.shortName(node.type.replaceAll("_", " ")); }
  route(node: InstanceGraphNode) {
    return this.navigation?.destination(node.taskId ? {kind:'task',id:node.taskId}
      : node.activityId ? {kind:'activity',id:node.activityId} : {kind:'history',instanceId:this.graph().instance.id}) ?? null;
  }
  activityRoute(id: string) { return this.navigation?.destination({kind:'activity',id}) ?? null; }
  label(node: InstanceGraphNode): string {
    if (node.state === "ACTIVE") return this.messages().current;
    if (node.state === "COMPLETED")
      return node.key === this.graph().instance.currentNodeKey &&
        this.graph().instance.status === "COMPLETED"
        ? this.messages().finalPosition
        : this.messages().completed;
    return this.messages().pending;
  }
  shortName(name: string): string {
    const points = Array.from(name);
    return points.length > 26 ? points.slice(0, 25).join("") + "…" : name;
  }
  accessibleName(node: InstanceGraphNode): string {
    return [node.name, node.key, node.type, this.label(node), this.route(node) ? this.messages().open : ''].join(', ');
  }
}
