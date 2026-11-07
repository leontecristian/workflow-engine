import { Component, signal } from '@angular/core';
import type { TaskView } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
@Component({selector:"workflow-assigned-tasks",standalone:true,imports:[...PAGE_IMPORTS],styleUrl:"./runtime.scss",templateUrl:"./assigned-tasks.html"})
export class WorkflowAssignedTasksComponent extends RuntimeState {
 protected clearContent(){this.rows.set([]);this.next=null;}
  get meta() {return {
    title: this.messages.assignedTitle,
    eyebrow: "YOUR WORKSPACE",
    description:
      "Your eligible open tasks, read from the workflow engine.",
    icon: "tasks",
    listTitle: "Assigned to you",
    listDescription:
      "Tasks available to your authenticated account appear here.",
  };}
  rows = signal<TaskView[]>([]);
  next: string | null = null;
  pageSize = 50;
  async load(cursor?: string) {
    this.clearContent();
    await this.run(async (signal,current) => {
      const p = await this.api.assignedTasks(cursor, this.pageSize,signal);
      if(!current())return;
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
