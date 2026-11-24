import { Component, inject, signal } from '@angular/core';
import { DEFAULT_ASSIGNED_TASKS_DESCRIPTION, DEFAULT_RUNTIME_MESSAGES } from '@workflow-engine/ui';
import { WorkflowAssignedTasksComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
import type { PageHeaderContent, PageHeaderSource } from '../../shell/page-header';

@Component({
  selector: 'console-assigned-tasks',
  standalone: true,
  imports: [WorkflowAssignedTasksComponent],
  templateUrl: './assigned-tasks.html',
})
export class AssignedTasksPage implements PageHeaderSource {
  readonly session = inject(ConsoleSession);
  readonly pageHeader = signal<PageHeaderContent>({
    title: DEFAULT_RUNTIME_MESSAGES.assignedTitle,
    description: DEFAULT_ASSIGNED_TASKS_DESCRIPTION,
  }).asReadonly();
}
