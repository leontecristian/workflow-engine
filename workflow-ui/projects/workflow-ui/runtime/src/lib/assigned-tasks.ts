import { Component, computed, input, signal } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { DEFAULT_ASSIGNED_TASKS_DESCRIPTION } from '@workflow-engine/ui';
import type { AssignedTaskRow, TaskAdministrationFilters } from '@workflow-engine/ui';
import { PAGE_IMPORTS } from './page-imports';
import { ConsoleRecords } from './records';
import { RuntimeState } from './runtime-state';

const FILTER_LABELS = {
  taskName: 'Task name',
  taskType: 'Task type code',
  assignmentKind: 'Assignment kind',
  assignmentRef: 'Assignment reference',
  assigneeRef: 'Assignee reference',
  instanceId: 'Instance ID',
} as const;

type AdvancedFilter = keyof typeof FILTER_LABELS;

interface InboxPage {
  items: AssignedTaskRow[];
  nextCursor: string | null;
  filters: TaskAdministrationFilters;
  index: number;
  cursors: (string | undefined)[];
}

let nextFilterPanelId = 0;

@Component({
  selector: 'workflow-assigned-tasks',
  standalone: true,
  imports: [...PAGE_IMPORTS, MatChipsModule, ConsoleRecords],
  styleUrls: ['./runtime.scss', './task-list.scss'],
  templateUrl: './assigned-tasks.html',
})
export class WorkflowAssignedTasksComponent extends RuntimeState {
  readonly externalPageHeader = input(false);
  filters: TaskAdministrationFilters = { status: 'OPEN' };
  filtersExpanded = false;
  readonly filterPanelId = `workflow-inbox-filters-${nextFilterPanelId++}`;
  readonly pageSizes = [20, 50, 100];
  pageSize = 50;

  protected readonly page = signal<InboxPage | null>(null);
  protected readonly rows = computed(() => this.page()?.items ?? []);
  protected readonly appliedChips = computed(() => {
    const filters = this.page()?.filters;
    if (!filters) return [];
    return (Object.keys(FILTER_LABELS) as AdvancedFilter[]).flatMap(key => {
      const value = filters[key];
      return value ? [{ key, label: FILTER_LABELS[key], value }] : [];
    });
  });

  protected get meta() {
    return {
      title: this.messages.assignedTitle,
      eyebrow: 'YOUR WORKSPACE',
      description: DEFAULT_ASSIGNED_TASKS_DESCRIPTION,
      icon: 'assignment_turned_in',
    };
  }

  protected clearContent() {
    this.page.set(null);
  }

  protected onScope() {
    this.filters = { status: 'OPEN' };
    this.filtersExpanded = false;
  }

  protected filtersChanged() {
    this.invalidate();
    this.clearContent();
    this.error.set('');
  }

  async load() {
    const filters = { ...this.filters };
    for (const key of Object.keys(FILTER_LABELS) as AdvancedFilter[]) {
      if (filters[key] === '') delete filters[key];
    }
    await this.loadPage(filters, [undefined], 0, this.pageSize);
  }

  protected async removeFilter(key: AdvancedFilter) {
    const page = this.page();
    if (this.busy() || !page) return;
    this.filters = { ...page.filters };
    delete this.filters[key];
    if (key === 'assignmentKind') delete this.filters.assignmentRef;
    await this.load();
  }

  protected async nextPage() {
    const page = this.page();
    if (this.busy() || !page?.nextCursor) return;
    await this.loadPage(
      page.filters,
      [...page.cursors.slice(0, page.index + 1), page.nextCursor],
      page.index + 1,
      this.pageSize,
    );
  }

  protected async previousPage() {
    const page = this.page();
    if (this.busy() || !page || page.index === 0) return;
    await this.loadPage(page.filters, page.cursors, page.index - 1, this.pageSize);
  }

  protected async changePageSize(size: number) {
    if (this.busy() || !this.pageSizes.includes(size)) return;
    this.pageSize = size;
    await this.load();
  }

  protected open(row: AssignedTaskRow) {
    this.navigate({ kind: 'task', id: row.id });
  }

  private async loadPage(
    filters: TaskAdministrationFilters,
    cursors: (string | undefined)[],
    index: number,
    size: number,
  ) {
    this.clearContent();
    await this.run(async (signal, current) => {
      if (filters.assignmentRef && !filters.assignmentKind) {
        throw Error('Choose an assignment kind to filter by reference.');
      }
      const page = await this.api.assignedTasks(filters, cursors[index], size, signal);
      if (current()) this.page.set({ ...page, filters, cursors, index });
    });
  }
}
