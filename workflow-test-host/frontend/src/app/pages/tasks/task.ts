import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import { CommandRecovery } from "../../data/command-recovery";
import type { TaskView, TaskContract, TaskSubmission } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-task",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./task.html",
})
export class TaskPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Task details",
    eyebrow: "ASSIGNED WORK",
    description:
      "Inspect the stored assignment, complete your task and read its accepted submission.",
    icon: "tasks",
  };
  id = "";
  detail = signal<TaskView | null>(null);
  contract = signal<TaskContract | null>(null);
  submission = signal<TaskSubmission | null>(null);
  outcome = "";
  comment = "";
  commands = inject(CommandRecovery);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        if (this.id !== params.get("id")) {
          this.outcome = "";
          this.comment = "";
        }
        this.id = params.get("id")!;
        void this.load();
      });
  }
  async load() {
    const id = this.id;
    await this.run(async () => {
      const [d, c, s] = await Promise.all([
        this.api.task(id),
        this.api.taskContract(id),
        this.api.taskSubmission(id),
      ]);
      if (this.destroyRef.destroyed || this.id !== id) return;
      this.detail.set(d);
      this.contract.set(c);
      this.submission.set(s);
    });
  }
  async complete(): Promise<void> {
    await this.run(async () => {
      this.commands.buffer.remember({
        kind: "complete",
        body: {
          commandId: crypto.randomUUID(),
          instanceId: this.detail()!.instanceId,
          taskId: this.id,
          outcomeKey: this.outcome,
          comment: this.comment || null,
        },
        expectedToken: this.contract()!.instanceLockVersion,
      });
      await this.issue(false);
    });
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
