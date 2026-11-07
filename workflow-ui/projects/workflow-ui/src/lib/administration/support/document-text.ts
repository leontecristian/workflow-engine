export function rawDocument(value: string): string {
  return value;
}
/** Format whitespace only, retaining number literals, string escapes and key order. */
export function prettyDocument(value: string): string {
  JSON.parse(value);
  const tokens = value.match(/"(?:\\.|[^"\\])*"|[^\s]/g)!;
  let depth = 0;
  let result = "";
  const newline = () => "\n" + "  ".repeat(depth);
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (token === "{" || token === "[") {
      result += token;
      if (tokens[i + 1] !== (token === "{" ? "}" : "]")) {
        depth++;
        result += newline();
      }
    } else if (token === "}" || token === "]") {
      if (tokens[i - 1] !== (token === "}" ? "{" : "[")) {
        depth--;
        result += newline();
      }
      result += token;
    } else if (token === ",") result += token + newline();
    else if (token === ":") result += ": ";
    else result += token;
  }
  return result;
}
