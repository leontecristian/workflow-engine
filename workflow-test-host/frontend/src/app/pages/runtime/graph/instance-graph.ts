import { Component, input } from "@angular/core";
import { RouterLink } from "@angular/router";
import type { InstanceGraph, InstanceGraphNode } from "../../../data/dtos";

@Component({
  selector: "console-instance-graph",
  standalone: true,
  imports: [RouterLink],
  templateUrl: "./instance-graph.html",
  styleUrl: "./instance-graph.scss",
})
export class InstanceGraphComponent {
  graph = input.required<InstanceGraph>();

  nodeX(index: number): number {
    return 24 + index * 310;
  }
  edgeStart(key: string): number {
    return this.nodeX(this.graph().nodes.findIndex((n) => n.key === key)) + 250;
  }
  edgeEnd(key: string): number {
    return this.nodeX(this.graph().nodes.findIndex((n) => n.key === key));
  }
  route(node: InstanceGraphNode): string[] {
    return node.taskId
      ? ["/task", node.taskId]
      : ["/history", this.graph().instance.id];
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
    return `${node.name} (${node.key}), ${this.label(node)}. Open ${node.taskId ? "task" : "history"}.`;
  }
}
