import { DestroyRef, Injectable, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommandBuffer } from "../command-buffer";
import { ConsoleApi } from "./console-api";
import { ConsoleSession } from "./console-session";
import type { Observation, Execution } from "./dtos";
/** Page-session command state: original immutable inputs survive route destruction. */
@Injectable({ providedIn: "root" })
export class CommandRecovery {
  private readonly api = inject(ConsoleApi);
  private readonly session = inject(ConsoleSession);
  readonly buffer = new CommandBuffer();
  readonly result = signal<Observation | null>(null);
  readonly observations = new Map<string, Observation>();
  private generation = 0;
  constructor() {
    this.buffer.setActor(this.session.session()?.actorRef || null);
    this.session.identityChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((actor) => {
        if (this.buffer.actor !== actor) {
          this.clear();
          this.buffer.setActor(actor);
        }
      });
  }
  clear() {
    this.generation++;
    this.buffer.clear();
    this.observations.clear();
    this.result.set(null);
  }
  select(id: string) {
    this.buffer.select(id);
    this.result.set(this.observations.get(id) || null);
  }
  restore(text: string) {
    const e = this.buffer.restore(text);
    this.result.set(this.observations.get(e.body["commandId"]!) || null);
  }
  execution(): Execution | null {
    const r = this.result()?.result;
    if (!r) return null;
    return "execution" in r ? r.execution : r.result?.execution || null;
  }
  async execute(replay: boolean) {
    const envelope = this.buffer.command;
    if (!envelope) throw new Error("No command selected");
    const actor = this.buffer.actor,
      id = envelope.body["commandId"]!;
    const generation = this.generation;
    try {
      const result = await this.api.executeCommand(envelope, replay);
      this.record(actor, generation, id, {
        outcome: replay
          ? "REPLAY_ONLY_LOOKUP"
          : "replayed" in result && result.replayed
            ? "REPLAYED_ORIGINAL_RESULT"
            : "NEW_COMMITTED_RESULT",
        result,
      });
      return result;
    } catch (error) {
      const status = (
        error as {
          httpStatus?: number;
        }
      ).httpStatus;
      this.record(actor, generation, id, {
        outcome: status && status < 500 ? "REJECTED" : "UNKNOWN_OUTCOME",
        message: String(error),
      });
      throw error;
    }
  }
  private record(
    actor: string | null,
    generation: number,
    id: string,
    observation: Observation,
  ) {
    if (this.buffer.actor !== actor || this.generation !== generation) return;
    this.observations.set(id, observation);
    this.result.set(observation);
  }
}
