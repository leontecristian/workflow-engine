import { parseTree, findNodeAtLocation, type Node } from "jsonc-parser";

export type ObjectKind = "nodes" | "transitions";
export interface FragmentEdit { source: string; kind: ObjectKind; key: string; replacement: string; }
function tree(source: string): Node {
  if (source.length > 4 * 1024 * 1024) throw new Error("JSON exceeds the editor size limit.");
  JSON.parse(source); // Strict JSON, without accepting comments or trailing commas.
  const errors: import("jsonc-parser").ParseError[] = [];
  const root = parseTree(source, errors, { disallowComments: true, allowTrailingComma: false });
  if (!root || errors.length || root.type !== "object") throw new Error("Enter a valid JSON object.");
  const pending = [root];
  while (pending.length) {
    const node = pending.pop()!;
    if (node.type === "object") {
      const keys = (node.children ?? []).map(property => property.children![0].value);
      if (new Set(keys).size !== keys.length) throw new Error("Duplicate JSON properties are not allowed.");
    }
    pending.push(...(node.children ?? []));
  }
  return root;
}
function target(source: string, kind: ObjectKind, key: string): Node {
  const array = findNodeAtLocation(tree(source), [kind]);
  if (array?.type !== "array") throw new Error("Selected collection is unavailable.");
  const matches = (array.children ?? []).filter(node => node.type === "object" && findNodeAtLocation(node, ["key"])?.value === key);
  if (matches.length !== 1) throw new Error("Selected object is missing or ambiguous. Save and reselect it.");
  return matches[0];
}
export function selectedObjectJson(source: string, kind: ObjectKind, key: string): string {
  const node = target(source, kind, key);
  return source.slice(node.offset, node.offset + node.length);
}
export function applyFragment(current: string, edit: FragmentEdit): string {
  if (current !== edit.source) throw new Error("The full JSON has changed. Save or reload it, then reselect the object.");
  const replacement = tree(edit.replacement);
  if (findNodeAtLocation(replacement, ["key"])?.value !== edit.key)
    throw new Error("Keep the selected object's key unchanged. Rename keys and references in the full JSON editor.");
  const node = target(current, edit.kind, edit.key);
  const result = current.slice(0, node.offset) + edit.replacement + current.slice(node.offset + node.length);
  if (result.length > 4 * 1024 * 1024) throw new Error("JSON exceeds the editor size limit.");
  return result;
}
