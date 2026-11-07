import { Component, signal } from '@angular/core';
import type { InstanceSummary } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
@Component({selector:"workflow-instances",standalone:true,imports:[...PAGE_IMPORTS],styleUrl:"./runtime.scss",templateUrl:"./instances.html"})
export class WorkflowInstancesComponent extends RuntimeState {
 protected clearContent(){this.rows.set([]);this.next=null;}
  get meta() {return {
    title: this.messages.instancesTitle,
    eyebrow: "WORKFLOW MONITORING",
    description:
      "Monitor running workflows, inspect their current step and open their execution evidence.",
    icon: "workflow",
    listTitle: "Workflow instances",
  };}
  rows = signal<InstanceSummary[]>([]);
  next: string | null = null;
  pageSize = 50;
  filter = "ACTIVE";
  async load(cursor?: string) {
    this.clearContent();
    await this.run(async (signal,current) => {
      const p = await this.api.listInstances(
        this.filter,
        cursor,
        this.pageSize, signal,
      );
      if(!current())return;
      this.rows.set(p.items);
      this.next = p.nextAfterId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: { id: string }) {
    this.navigate({kind:"instance",instanceId:row.id});
  }
}
