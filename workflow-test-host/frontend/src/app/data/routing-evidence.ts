/** Display persisted evidence only. This module never evaluates workflow conditions. */
export interface RoutingEvidence {
  nodeKey: string;
  selectedTransitionKey: string;
  usedDefault: boolean;
  evaluations: { transitionKey: string; result: boolean; reads: { variableKey: string; type: string; value: string | boolean | null }[] }[];
}
export function routingEvidence(eventType: string | undefined, details: string | undefined): RoutingEvidence | null {
  if (eventType !== "GATEWAY_ROUTED" || !details) return null;
  try {
    const value = JSON.parse(details);
    const key = (s: unknown) => typeof s === "string" && /^[A-Za-z][A-Za-z0-9._-]{0,99}$/.test(s);
    if (!key(value?.nodeKey) || !key(value.selectedTransitionKey) || typeof value.usedDefault !== "boolean" || !Array.isArray(value.evaluations)) return null;
    for (const evaluation of value.evaluations) {
      if (!key(evaluation?.transitionKey) || typeof evaluation.result !== "boolean" || !Array.isArray(evaluation.reads)) return null;
      for (const read of evaluation.reads) {
        if (!key(read?.variableKey) || !["STRING", "LONG", "DECIMAL", "BOOLEAN", "DATE", "DATETIME"].includes(read.type)) return null;
        if (read.value !== null && (read.type === "BOOLEAN" ? typeof read.value !== "boolean" : typeof read.value !== "string")) return null;
      }
    }
    return value;
  } catch { return null; }
}
