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
          this.detail.set(null);
          this.contract.set(null);
          this.submission.set(null);
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
  commentBlank(): boolean {
    // Java String.isBlank whitespace; preserve the submitted text exactly.
    return /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u.test(this.comment);
  }
  async complete(outcomeKey: string): Promise<void> {
    if (this.busy()) return;
    await this.run(async () => {
      const selected = this.contract()?.outcomes.find(o => o.key === outcomeKey);
      if (!selected || this.detail()?.status !== "OPEN") throw new Error("Choose an available outcome on an open task.");
      this.outcome = selected.key;
      if (selected.requiresComment && this.commentBlank()) throw new Error("A comment is required for " + selected.label + ".");
      this.commands.buffer.remember({
        kind: "complete",
        body: {
          commandId: crypto.randomUUID(),
          instanceId: this.detail()!.instanceId,
          taskId: this.id,
          outcomeKey: selected.key,
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
