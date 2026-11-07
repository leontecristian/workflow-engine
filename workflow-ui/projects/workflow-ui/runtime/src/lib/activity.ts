import { Component, signal } from '@angular/core';
import type { ActivityView } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
@Component({selector:"workflow-activity",standalone:true,imports:[...PAGE_IMPORTS],styleUrl:"./runtime.scss",templateUrl:"./activity.html"})
export class WorkflowActivityComponent extends RuntimeState {
 protected clearContent(){this.detail.set(null);}
  get meta() {return {
    title: this.messages.activityTitle,
    eyebrow: "RUNTIME INSPECTION",
    description: "Inspect the human activity and its completion state.",
    icon: "tasks",
  };}
  detail = signal<ActivityView | null>(null);
  async load() {
    const id = this.id;
    await this.run(async (signal,current) => {
      const d = await this.api.activity(id,signal);
      if (current()) this.detail.set(d);
    });
  }
}
