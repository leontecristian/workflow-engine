import { Injectable, signal } from "@angular/core";
import { CommandBuffer, commandRequest, type Envelope } from "./command-buffer";
@Injectable({ providedIn: "root" })
export class ConsoleApi {
  session = signal<any>(null);
  capabilities = signal<any>(null);
  buffer = new CommandBuffer();
  commandResult = signal<any>(null);
  observations = new Map<string, unknown>();
  clearCommands(): void {
    this.buffer.clear();
    this.observations.clear();
    this.commandResult.set(null);
  }
  async initialize(): Promise<void> {
    const session = await this.call("/session");
    if (this.buffer.actor !== session.actorRef) this.clearCommands();
    this.buffer.setActor(session.actorRef);
    this.session.set(session);
    if (this.admin) this.capabilities.set(await this.call("/capabilities"));
  }
  get admin(): boolean {
    return !!this.session()?.roles.includes("CONSOLE_ADMIN");
  }
  async call(
    path: string,
    method = "GET",
    body?: unknown,
    token?: string | null,
    raw = false,
  ): Promise<any> {
    const headers: Record<string, string> = {};
    if (method !== "GET") {
      headers[this.session()?.csrfHeaderName || "X-CSRF-TOKEN"] =
        this.session()?.csrfToken || "";
      if (body !== undefined) headers["Content-Type"] = "application/json";
    }
    if (token !== null && token !== undefined)
      headers["X-Expected-Lock-Version"] = token;
    let response: Response;
    try {
      response = await fetch("/console/api" + path, {
        method,
        credentials: "same-origin",
        headers,
        body:
          body === undefined
            ? undefined
            : raw
              ? (body as string)
              : JSON.stringify(body),
      });
    } catch {
      throw new Error(
        "Transport outcome unknown. Keep the original command and use Find result or Retry.",
      );
    }
    const text = await response.text();
    let value: any;
    try {
      value = text ? JSON.parse(text) : null;
    } catch {
      throw new Error(
        "Response unavailable. Command outcome may be unknown; use Find result.",
      );
    }
    if (!response.ok) {
      if (response.status === 401) {
        this.clearCommands();
        this.buffer.setActor(null);
        this.session.set(null);
      }
      const failure = new Error(
        `${response.status} ${value?.code || "REQUEST_FAILED"} ${JSON.stringify(value?.diagnostics || [])} [${value?.correlationId || ""}]`,
      );
      Object.assign(failure, { httpStatus: response.status });
      throw failure;
    }
    return value;
  }
  async execute(envelope: Envelope, replay = false): Promise<any> {
    const request = commandRequest(envelope, replay);
    const id = envelope.body["commandId"]!;
    try {
      const result = await this.call(
        request.path,
        "POST",
        request.body,
        request.token,
      );
      const observation = {
        outcome: replay
          ? "REPLAY_ONLY_LOOKUP"
          : result.replayed
            ? "REPLAYED_ORIGINAL_RESULT"
            : "NEW_COMMITTED_RESULT",
        result,
      };
      this.observations.set(id, observation);
      this.commandResult.set(observation);
      return result;
    } catch (error) {
      const status = (error as { httpStatus?: number }).httpStatus;
      const observation = {
        outcome: status && status < 500 ? "REJECTED" : "UNKNOWN_OUTCOME",
        message: String(error),
      };
      this.observations.set(id, observation);
      this.commandResult.set(observation);
      throw error;
    }
  }
  async logout(): Promise<void> {
    const headers: Record<string, string> = {};
    headers[this.session().csrfHeaderName] = this.session().csrfToken;
    this.clearCommands();
    this.buffer.setActor(null);
    this.session.set(null);
    await fetch("/console/logout", {
      method: "POST",
      headers,
      credentials: "same-origin",
    });
    location.href = "/console/login";
  }
}
