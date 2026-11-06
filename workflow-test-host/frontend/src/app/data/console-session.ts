import { Injectable, signal } from "@angular/core";
import { Subject } from "rxjs";
import type { Capabilities, Session } from "./dtos";
@Injectable({ providedIn: "root" })
export class ConsoleSession {
  readonly session = signal<Session | null>(null);
  readonly capabilities = signal<Capabilities | null>(null);
  readonly identityChanges = new Subject<string | null>();
  private initializing: Promise<void> | null = null;
  get admin() {
    return !!this.session()?.roles.includes("CONSOLE_ADMIN");
  }
  initialize(): Promise<void> {
    if (!this.initializing)
      this.initializing = this.load().finally(() => (this.initializing = null));
    return this.initializing;
  }
  private async load() {
    const session = await this.call<Session>("/session");
    this.session.set(session);
    this.identityChanges.next(session.actorRef);
    this.capabilities.set(
      this.admin ? await this.call<Capabilities>("/capabilities") : null,
    );
  }
  async call<T>(
    path: string,
    method = "GET",
    body?: unknown,
    token?: string | null,
    raw = false,
  ): Promise<T> {
    if (!this.session() && path !== "/session") await this.initialize();
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
    let value: T;
    try {
      value = text ? JSON.parse(text) : null;
    } catch {
      throw new Error(
        "Response unavailable. Command outcome may be unknown; use Find result.",
      );
    }
    if (!response.ok) {
      if (response.status === 401) this.clear();
      const v = value as {
        code?: string;
        diagnostics?: unknown[];
        correlationId?: string;
      } | null;
      throw Object.assign(
        new Error(
          `${response.status} ${v?.code || "REQUEST_FAILED"} ${JSON.stringify(v?.diagnostics || [])} [${v?.correlationId || ""}]`,
        ),
        { httpStatus: response.status },
      );
    }
    if (value && typeof value === "object") Object.defineProperty(value, "exactJson", {value: text});
    return value;
  }
  clear() {
    this.session.set(null);
    this.capabilities.set(null);
    this.identityChanges.next(null);
  }
  async logout() {
    const session = this.session();
    const headers: Record<string, string> = {};
    if (session) headers[session.csrfHeaderName] = session.csrfToken;
    this.clear();
    await fetch("/console/logout", {
      method: "POST",
      headers,
      credentials: "same-origin",
    });
    location.href = "/console/login";
  }
}
