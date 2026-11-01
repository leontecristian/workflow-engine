export type Envelope = Readonly<{
  kind: "start" | "complete";
  body: Readonly<Record<string, string | null>>;
  expectedToken: string | null;
}>;
const decimal = /^(0|[1-9][0-9]*)$/;
export function exactDecimal(value: string, zero = false): string {
  if (
    !decimal.test(value) ||
    (!zero && value === "0") ||
    BigInt(value) > 9223372036854775807n
  )
    throw new Error("Invalid decimal identifier");
  return value;
}
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
export function freezeEnvelope(input: unknown): Envelope {
  if (!input || typeof input !== "object")
    throw new Error("Invalid command envelope");
  const obj = input as Record<string, unknown>;
  if (
    Object.keys(obj).sort().join() !== "body,expectedToken,kind" ||
    !["start", "complete"].includes(String(obj["kind"]))
  )
    throw new Error("Invalid command envelope");
  if (
    !obj["body"] ||
    typeof obj["body"] !== "object" ||
    Array.isArray(obj["body"])
  )
    throw new Error("Invalid body");
  const body = { ...(obj["body"] as Record<string, string | null>) };
  const start = obj["kind"] === "start";
  const expected = start
    ? [
        "commandId",
        "definitionVersionId",
        "expectedRequestVersion",
        "requestId",
      ]
    : ["commandId", "comment", "instanceId", "outcomeKey", "taskId"];
  if (Object.keys(body).sort().join() !== expected.sort().join())
    throw new Error("Invalid command fields");
  for (const [key, value] of Object.entries(body))
    if (typeof value !== "string" && !(key === "comment" && value === null))
      throw new Error("Invalid command value");
  if (
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(
      body["commandId"]!,
    )
  )
    throw new Error("Invalid command UUID");
  for (const key of start
    ? ["definitionVersionId", "requestId"]
    : ["instanceId", "taskId"])
    exactDecimal(body[key]!);
  if (start) {
    exactDecimal(body["expectedRequestVersion"]!, true);
    if (obj["expectedToken"] !== null) throw new Error("Unexpected token");
  } else {
    if (typeof obj["expectedToken"] !== "string")
      throw new Error("Missing token");
    exactDecimal(obj["expectedToken"], true);
  }
  return Object.freeze({
    kind: start ? "start" : "complete",
    body: Object.freeze(body),
    expectedToken: obj["expectedToken"] as string | null,
  });
}
export class CommandBuffer {
  actor: string | null = null;
  command: Envelope | null = null;
  entries: Envelope[] = [];
  setActor(actor: string | null): void {
    if (this.actor !== actor) this.clear();
    this.actor = actor;
  }
  remember(value: unknown): Envelope {
    const frozen = freezeEnvelope(value);
    const previous = this.entries.find(
      (item) => item.body["commandId"] === frozen.body["commandId"],
    );
    if (
      previous &&
      (previous.kind !== frozen.kind ||
        previous.expectedToken !== frozen.expectedToken ||
        Object.keys(previous.body).some(
          (key) => previous.body[key] !== frozen.body[key],
        ))
    )
      throw new Error(
        "This UUID already has a different original request in the command buffer",
      );
    if (!previous) this.entries.push(frozen);
    this.command = previous || frozen;
    return this.command;
  }
  select(commandId: string): void {
    this.command =
      this.entries.find((item) => item.body["commandId"] === commandId) || null;
  }
  clear(): void {
    this.command = null;
    this.entries = [];
  }
  restore(text: string): Envelope {
    return this.remember(JSON.parse(text));
  }
  export(): string {
    return JSON.stringify(this.command, null, 2);
  }
}
export function commandRequest(
  envelope: Envelope,
  replay: boolean,
): {
  path: string;
  body: Readonly<Record<string, string | null>>;
  token: string | null;
} {
  if (envelope.kind === "start")
    return {
      path: replay ? "/commands/start/replay" : "/instances/start",
      body: envelope.body,
      token: null,
    };
  const { instanceId, taskId, ...body } = envelope.body;
  return {
    path: replay
      ? "/commands/complete/replay"
      : `/instances/${instanceId}/tasks/${taskId}/complete`,
    body: replay ? envelope.body : body,
    token: envelope.expectedToken,
  };
}
