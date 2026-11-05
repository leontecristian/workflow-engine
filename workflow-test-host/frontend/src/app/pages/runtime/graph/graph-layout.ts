import type { InstanceGraph, InstanceGraphEdge, InstanceGraphNode } from "../../../data/dtos";

export interface PositionedNode {
  node: InstanceGraphNode;
  x: number;
  y: number;
}
export interface RoutedEdge {
  edge: InstanceGraphEdge;
  path: string;
  labelX: number;
  labelY: number;
}
export interface GraphLayout {
  nodes: PositionedNode[];
  edges: RoutedEdge[];
  width: number;
  height: number;
}

const WIDTH = 250, HEIGHT = 104, COLUMN = 330, ROW = 170, MARGIN = 28;
const compare = (a: string, b: string) => a < b ? -1 : a > b ? 1 : 0;

/** Bounded, deterministic display layout. It never evaluates routing or changes execution state. */
export function layoutGraph(graph: Pick<InstanceGraph, "nodes" | "edges">): GraphLayout {
  if (!graph.nodes.length || graph.nodes.length > 200 || graph.edges.length > 500)
    throw new Error("Graph exceeds display bounds");
  const nodes = [...graph.nodes].sort((a, b) => compare(a.key, b.key));
  const edges = [...graph.edges].sort((a, b) => compare(a.key, b.key));
  const byKey = new Map(nodes.map(node => [node.key, node]));
  const edgeKeys = new Set(edges.map(edge => edge.key));
  if (byKey.size !== nodes.length || edgeKeys.size !== edges.length)
    throw new Error("Duplicate graph identity");
  const outgoing = new Map<string, string[]>();
  for (const edge of edges) {
    if (!byKey.has(edge.from) || !byKey.has(edge.to)) throw new Error("Unknown graph endpoint");
    outgoing.set(edge.from, [...(outgoing.get(edge.from) || []), edge.to]);
  }
  for (const targets of outgoing.values()) targets.sort(compare);
  const ranks = new Map<string, number>();
  let maxRank = -1;
  // Directed breadth-first layers terminate on cycles and retain disconnected declared nodes.
  const roots = [...nodes.filter(n => n.type === "START"), ...nodes];
  for (const root of roots) {
    if (ranks.has(root.key)) continue;
    const queue = [root.key]; ranks.set(root.key, ++maxRank);
    for (let index = 0; index < queue.length; index++) {
      const key = queue[index], rank = ranks.get(key)!;
      for (const target of outgoing.get(key) || []) {
        if (ranks.has(target)) continue;
        ranks.set(target, rank + 1); maxRank = Math.max(maxRank, rank + 1); queue.push(target);
      }
    }
  }
  const needsBackRoutes = edges.some(edge => ranks.get(edge.to)! <= ranks.get(edge.from)!);
  const top = needsBackRoutes ? 156 : 60;
  const rows = new Map<number, number>();
  const positions = new Map<string, PositionedNode>();
  for (const node of nodes) {
    const rank = ranks.get(node.key)!, row = rows.get(rank) || 0;
    rows.set(rank, row + 1);
    positions.set(node.key, { node, x: MARGIN + rank * COLUMN, y: top + row * ROW });
  }
  const parallel = new Map<string, InstanceGraphEdge[]>();
  for (const edge of edges) {
    const pair = JSON.stringify([edge.from, edge.to]);
    parallel.set(pair, [...(parallel.get(pair) || []), edge]);
  }
  const routes = edges.map((edge, index): RoutedEdge => {
    const source = positions.get(edge.from)!, target = positions.get(edge.to)!;
    const sy = source.y + HEIGHT / 2, ty = target.y + HEIGHT / 2;
    const pair = parallel.get(JSON.stringify([edge.from, edge.to]))!;
    const offset = (pair.indexOf(edge) % 5 - Math.min(pair.length - 1, 4) / 2) * 12;
    if (edge.from === edge.to) {
      const right = source.x + WIDTH + 30 + index % 5 * 6, above = source.y - 26 - index % 5 * 8;
      return { edge, path: `M ${source.x + WIDTH + 3} ${sy} H ${right} V ${above} H ${source.x + WIDTH / 2} V ${source.y - 4}`,
        labelX: source.x + WIDTH / 2, labelY: above - 10 };
    }
    if (target.x > source.x) {
      const sx = source.x + WIDTH + 3, tx = target.x - 5, middle = (sx + tx) / 2;
      return { edge, path: `M ${sx} ${sy} C ${middle} ${sy + offset} ${middle} ${ty + offset} ${tx} ${ty}`,
        labelX: middle, labelY: sy === ty ? source.y - 14 + offset : (sy + ty) / 2 - 14 + offset };
    }
    if (target.x === source.x) {
      const right = source.x + WIDTH + 30 + index % 5 * 8;
      return { edge, path: `M ${source.x + WIDTH + 3} ${sy} C ${right} ${sy} ${right} ${ty} ${target.x + WIDTH + 5} ${ty}`,
        labelX: right, labelY: (sy + ty) / 2 - 12 };
    }
    const lane = 26 + index % 8 * 12, sx = source.x - 4, tx = target.x - 5;
    return { edge, path: `M ${sx} ${sy} H ${sx - 16} V ${lane} H ${tx - 16} V ${ty} H ${tx}`,
      labelX: (sx + tx) / 2, labelY: lane - 9 };
  });
  return {
    nodes: [...positions.values()].sort((a, b) => a.x - b.x || a.y - b.y),
    edges: routes,
    width: Math.max(...[...positions.values()].map(p => p.x + WIDTH)) + 170,
    height: Math.max(...[...positions.values()].map(p => p.y + HEIGHT)) + 40,
  };
}
