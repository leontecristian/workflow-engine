import type { InstanceGraph } from "@workflow-engine/ui";
import { layoutGraph } from "@workflow-engine/ui";

export interface DefinitionTransition {
  key: string;
  from: string;
  to: string;
  outcome?: string;
  condition: string | null;
  default: boolean;
}

/** Display projection only. Never rewrites the source or evaluates its conditions. */
export function definitionTopology(source: string) {
  try {
    if (!source || source.length > 4 * 1024 * 1024) return null;
    const doc = JSON.parse(source);
    if (doc?.schemaVersion !== 1 || !Array.isArray(doc.nodes) || !Array.isArray(doc.transitions)
        || !doc.nodes.length || doc.nodes.length > 200 || doc.transitions.length > 500) return null;
    const key = (value: unknown): value is string => typeof value === "string" && /^[A-Za-z][A-Za-z0-9._-]{0,99}$/.test(value);
    const text = (value: unknown): value is string => typeof value === "string" && value.length > 0 && value.length <= 1000;
    if (doc.nodes.some((n: any) => !n || !key(n.key) || !text(n.name) || !text(n.type))
        || doc.nodes.filter((n: any) => n.type === "START").length !== 1
        || doc.transitions.some((e: any) => !e || !key(e.key) || !key(e.from) || !key(e.to)
          || (e.outcome !== undefined && !key(e.outcome)))) return null;
    const graph: Pick<InstanceGraph, "nodes" | "edges"> = {
      nodes: doc.nodes.map((n: any) => ({ key: n.key, name: n.name, type: n.type, state: "PENDING", activityId: null, taskId: null })),
      edges: doc.transitions.map((e: any) => ({ key: e.key, from: e.from, to: e.to, outcome: e.outcome ?? null, traversed: false })),
    };
    const transitions: DefinitionTransition[] = doc.transitions.map((e: any) => ({
      key: e.key as string, from: e.from as string, to: e.to as string,
      outcome: e.outcome as string | undefined,
      condition: typeof e.condition === "string" ? e.condition : null,
      default: e.default === true,
    }));
    return { graph, transitions, layout: layoutGraph(graph) };
  } catch { return null; }
}
