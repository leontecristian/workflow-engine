import { Component, signal } from '@angular/core';
import type { TaskView } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
@Component({selector:"workflow-instance-tasks",standalone:true,imports:[...PAGE_IMPORTS],styleUrl:"./runtime.scss",templateUrl:"./instance-tasks.html"})
export class WorkflowInstanceTasksComponent extends RuntimeState {
 protected clearContent(){this.rows.set([]);this.next=null;}
  get meta() {return {
    title: this.messages.instanceTasksTitle,
    eyebrow: "RUNTIME INSPECTION",
    description: "Inspect open and completed assignments for this instance.",
    icon: "tasks",
    listTitle: "Tasks",
    listDescription:
      "Select a task to inspect its contract and accepted submission.",
  };}
  rows = signal<TaskView[]>([]);
  next: string | null = null;
  pageSize = 50;
  async load(cursor?: string) {
    this.clearContent();
    const id = this.id;
    await this.run(async (signal,current) => {
      const p = await this.api.instanceTasks(id, cursor, this.pageSize,signal);
      if (!current()) return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    this.navigate({kind:"task",id:row.id});
  }
}
