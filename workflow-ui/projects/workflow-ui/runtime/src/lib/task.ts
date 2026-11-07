import { Component, inject, signal } from '@angular/core';
import { CommandRecovery, WORKFLOW_RUNTIME_COMMANDS } from '@workflow-engine/ui';
import type { TaskView, TaskContract, TaskSubmission } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
@Component({selector:"workflow-task",standalone:true,imports:[...PAGE_IMPORTS],styleUrl:"./runtime.scss",templateUrl:"./task.html"})
export class WorkflowTaskComponent extends RuntimeState {
 protected clearContent(){this.detail.set(null);this.contract.set(null);this.submission.set(null);this.comment="";this.outcome="";}
  get meta() {return {
    title: this.messages.taskTitle,
    eyebrow: "ASSIGNED WORK",
    description:
      "Inspect the stored assignment, complete your task and read its accepted submission.",
    icon: "tasks",
  };}
  detail = signal<TaskView | null>(null);
  contract = signal<TaskContract | null>(null);
  submission = signal<TaskSubmission | null>(null);
  outcome = "";
  comment = "";
  commands = inject(CommandRecovery);
  readonly commandProvider=inject(WORKFLOW_RUNTIME_COMMANDS,{optional:true});
  protected onScope(){this.commands.setScope(this.sessionScope);}
  private completing=false;
  protected clearFailedRead(){if(!this.completing)this.clearContent();}
  async load() {
    this.clearContent();
    const id = this.id;
    await this.run(async (signal,current) => {
      const [d, c, s] = await Promise.all([
        this.api.task(id,signal),
        this.api.taskContract(id,signal),
        this.api.taskSubmission(id,signal),
      ]);
      if (!current()) return;
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
    if (this.busy() || !this.commandProvider || !this.detail() || !this.contract()) return;
    this.completing=true;
    await this.run(async (signal,current) => {
      const selected = this.contract()?.outcomes.find(o => o.key === outcomeKey);
      if (!selected || this.detail()?.status !== "OPEN") throw new Error("Choose an available outcome on an open task.");
      if(this.comment.length>2000)throw new Error("Comment exceeds 2000 characters.");
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
      await this.commands.execute(false,signal);
      if(current())this.navigate({kind:"commands"});
    });
    this.completing=false;
  }
}
