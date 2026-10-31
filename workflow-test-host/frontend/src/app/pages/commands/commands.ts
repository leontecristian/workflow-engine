import { Component, inject } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import { CommandRecovery } from "../../data/command-recovery";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-commands",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./commands.html",
})
export class CommandsPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Command recovery",
    eyebrow: "RELIABLE EXECUTION",
    description:
      "Keep the original request intact. Retry explicitly or find its immutable result.",
    icon: "replay",
  };
  commands = inject(CommandRecovery);
  result = this.commands.result;
  envelope = "";
  ngOnInit() {}
  commandExecution() {
    return this.commands.execution();
  }
  async retry(replay: boolean): Promise<void> {
    await this.run(() => this.issue(replay));
  }
  async copyEnvelope(): Promise<void> {
    await this.run(async () => {
      await navigator.clipboard.writeText(this.commands.buffer.export());
      this.notice.set(
        "Envelope copied; contains command input. Keep it private.",
      );
    });
  }
  downloadEnvelope(): void {
    const url = URL.createObjectURL(
      new Blob([this.commands.buffer.export()], { type: "application/json" }),
    );
    const a = document.createElement("a");
    a.href = url;
    a.download = "workflow-command.json";
    a.click();
    URL.revokeObjectURL(url);
  }
  restoreEnvelope() {
    try {
      this.commands.restore(this.envelope);
      this.error.set("");
      this.notice.set("Envelope restored. Find result does not execute it.");
    } catch (e) {
      this.error.set(String(e));
    }
  }
  clearCommand(): void {
    this.commands.clear();
    this.envelope = "";
  }
  selectCommand(id: string) {
    this.commands.select(id);
  }
  async issue(replay: boolean) {
    await this.commands.execute(replay);
    this.notice.set(
      replay
        ? "Replay-only lookup returned; no new command was executed."
        : "Command returned after commit. Inspect current state separately.",
    );
    await this.router.navigate(["/commands"]);
  }
}
