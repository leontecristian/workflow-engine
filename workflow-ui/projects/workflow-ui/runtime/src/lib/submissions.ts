import { Component, signal } from '@angular/core';
import type { SubmissionPage } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
import { WorkflowTaskValuesComponent } from './task-values';
@Component({selector:"workflow-submissions",standalone:true,imports:[...PAGE_IMPORTS, WorkflowTaskValuesComponent],styleUrl:"./runtime.scss",templateUrl:"./submissions.html"})
export class WorkflowSubmissionsComponent extends RuntimeState {
 protected clearContent(){this.rows.set([]);this.next=null;}
  get meta() {return {
    title: this.messages.submissionsTitle,
    eyebrow: "AUTHORITATIVE EVIDENCE",
    description:
      "Read immutable submissions accepted for the selected activity.",
    icon: "description",
    listTitle: "Submissions",
    listDescription: "These are accepted facts, not editable task drafts.",
  };}
  rows = signal<SubmissionPage['items']>([]);
  next: string | null = null;
  pageSize = 50;
  async load(cursor?: string) {
    this.clearContent();
    const id = this.id;
    await this.run(async (signal,current) => {
      const p = await this.api.submissions(id, cursor, this.pageSize,signal);
      if (!current()) return;
      this.rows.set(p.items);
      this.next = p.nextAfterTaskId;
    });
  }
  nextPage() {
    if (this.next) void this.load(this.next);
  }
  open(row: SubmissionPage['items'][number]) {
    this.navigate({kind:"task",id:row.taskId});
  }
}
