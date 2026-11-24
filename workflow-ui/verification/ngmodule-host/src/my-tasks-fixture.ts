import 'zone.js';
import { Component, ENVIRONMENT_INITIALIZER, Injectable, NgModule, inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { BrowserModule, platformBrowser } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterOutlet, provideRouter } from '@angular/router';
import { WORKFLOW_RUNTIME_READS, WORKFLOW_VIEWER_NAVIGATION } from '@workflow-engine/ui';
import type { AssignedTaskRow, TaskAdministrationFilters, TaskAssignment, TaskContract, TaskPage, TaskSubmission, ViewerDestination, WorkflowRuntimeReads } from '@workflow-engine/ui';
import { WorkflowAssignedTasksComponent, WorkflowTaskAdministrationComponent, WorkflowTaskComponent } from '@workflow-engine/ui/runtime';

const instanceId = '9007199254740993';
const reviewDate = '2026-11-22T12:00:00.1234567Z';
const closedAt = '2026-11-24T08:00:00Z';
const shared: TaskAssignment = { kind: 'GROUP', ref: 'Finance' };

function inboxRow(id: string, nodeName: string, createdAt: string, status = 'OPEN', assignment = shared): AssignedTaskRow {
  return {
    id, nodeName, status, createdAt, taskTypeName: 'Review task',
    assignmentLabel: assignment.ref,
    ownershipLabel: assignment.kind === 'USER' ? 'Assigned to you' : 'Unclaimed',
    closedAt: status === 'OPEN' ? null : closedAt,
  };
}

const completed = inboxRow('99', 'Finance review completed', '2026-11-21T12:00:00Z', 'COMPLETED');
const cancelled = inboxRow('98', 'Finance review cancelled', '2026-11-20T12:00:00Z', 'CANCELLED', { kind: 'USER', ref: 'reviewer' });
const reviewerRows = [
  inboxRow('2', 'International finance review with a long task name and an unbroken reference abcdefghijklmnopqrstuvwxyz', '2026-11-24T12:00:00Z'),
  ...Array.from({ length: 18 }, (_, index) => inboxRow(String(index + 31), 'Finance review ' + (index + 1), '2026-11-23T12:00:00Z')),
  inboxRow('29', 'Tied review 29', reviewDate),
  inboxRow('28', 'Tied review 28', reviewDate),
  inboxRow('27', 'Tied review 27', reviewDate),
  completed,
  cancelled,
];
reviewerRows.sort((one, two) => two.createdAt.localeCompare(one.createdAt) || Number(two.id) - Number(one.id));
const peerRows = [inboxRow('101', 'Peer open review', '2026-11-24T11:00:00Z')];

function inboxAdministrationRows() {
  return reviewerRows.slice(0, 1).map(row => ({ ...row, instanceId }));
}

async function readInboxFixture(
  filters: TaskAdministrationFilters, cursor: string | undefined, limit: number,
  signal: AbortSignal, mode: string, peer: boolean, revoked: boolean,
): Promise<TaskPage<AssignedTaskRow>> {
  await new Promise(resolve => setTimeout(resolve, mode === 'inbox-delayed' ? 850 : 30));
  if (signal.aborted && mode !== 'inbox-delayed') throw new DOMException('Aborted', 'AbortError');
  if (mode === 'inbox-failed') throw Error('Fixture read failed.');

  const rows = (peer ? peerRows : reviewerRows).filter(row => {
    const assignmentKind = row === cancelled ? 'USER' : 'GROUP';
    return (!revoked || row !== completed) &&
      (filters.status === 'ALL' || filters.status === row.status) &&
      (!filters.taskName || row.nodeName!.toLowerCase().includes(filters.taskName.toLowerCase())) &&
      (!filters.taskType || filters.taskType === 'REVIEW') &&
      (!filters.assignmentKind || filters.assignmentKind === assignmentKind) &&
      (!filters.assignmentRef || filters.assignmentRef === row.assignmentLabel) &&
      (!filters.assigneeRef || row === cancelled && filters.assigneeRef === 'reviewer') &&
      (!filters.instanceId || filters.instanceId === instanceId);
  });
  const offset = cursor === 'fixture-empty' ? 0 : Number(cursor ?? 0);
  if (!cursor && mode === 'inbox-empty') return { items: [], nextCursor: 'fixture-empty' };
  return { items: rows.slice(offset, offset + limit), nextCursor: offset + limit < rows.length ? String(offset + limit) : null };
}

function inboxTask(id: string) {
  const row = [...reviewerRows, ...peerRows].find(task => task.id === id);
  if (!row) throw Error('Unexpected fixture task ' + id);
  return row;
}

function inboxTaskContract(id: string): TaskContract {
  return {
    task: inboxTask(id), previousDecisionsAvailable: false, claimRequired: false, canClaim: false,
    canRelease: false, canAdminRelease: false, canComplete: false, outcomes: [],
  };
}

function inboxTaskSubmission(id: string): TaskSubmission | null {
  return id === completed.id ? {
    outcomeLabel: 'Approved', actorLabel: 'Fixture reviewer', occurredAt: closedAt,
    comment: 'Reviewed the retained evidence.',
  } : null;
}

@Injectable()
class InboxFixtureState {
  mode = 'success';
  reads = 0;
  peer = false;
  revoked = false;
  scope: object | null = {};

  replaceSession() {
    this.peer = true;
    this.mode = 'success';
    this.scope = {};
  }
}

@Injectable()
class InboxFixtureReads implements WorkflowRuntimeReads {
  private readonly state = inject(InboxFixtureState);

  assignedTasks(filters: TaskAdministrationFilters, cursor: string | undefined, limit: number, signal: AbortSignal) {
    this.state.reads++;
    return readInboxFixture(filters, cursor, limit, signal, this.state.mode, this.state.peer, this.state.revoked);
  }

  async administrationTasks() { return { items: inboxAdministrationRows(), nextCursor: null }; }
  async task(id: string) { return inboxTask(id); }
  async taskContract(id: string) { return inboxTaskContract(id); }
  async taskSubmission(id: string) { return inboxTaskSubmission(id); }
  listInstances(): never { throw Error('Outside inbox fixture'); }
  variables(): never { throw Error('Outside inbox fixture'); }
  instanceTasks(): never { throw Error('Outside inbox fixture'); }
  previousDecisions(): never { throw Error('Outside inbox fixture'); }
  activity(): never { throw Error('Outside inbox fixture'); }
  submissions(): never { throw Error('Outside inbox fixture'); }
}

@Injectable()
class InboxFixtureNavigation {
  private readonly router = inject(Router);
  destination(intent: ViewerDestination) {
    return intent.kind === 'task' ? this.router.createUrlTree(['/alternate', 'task', intent.id]) : null;
  }
}

@Component({
  selector: 'fixture-task-destination',
  standalone: false,
  template: `<workflow-task [id]="id" [sessionScope]="state.scope" />`,
})
class InboxFixtureDestination {
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  readonly state = inject(InboxFixtureState);
}

@Component({
  selector: 'fixture-root',
  standalone: false,
  template: `
    <div class="layout-evidence">
      <nav class="fixture-controls" aria-label="Inbox fixture controls">
        <button (click)="state.mode = 'success'">Normal responses</button>
        <button (click)="state.mode = 'inbox-delayed'">Delayed responses</button>
        <button (click)="state.mode = 'inbox-empty'">Empty first page</button>
        <button (click)="state.mode = 'inbox-failed'">Failed responses</button>
        <button (click)="state.revoked = true">Revoke completed read</button>
        <button (click)="state.replaceSession()">Replace with peer session</button>
        <button (click)="state.scope = null">Invalidate inbox session</button>
        <button (click)="administration = !administration">{{ administration ? 'My Tasks view' : 'Task Administration view' }}</button>
        <output>Inbox reads {{ state.reads }}</output>
      </nav>
      @if (!router.url.startsWith('/alternate/task/')) {
        @if (administration) { <workflow-task-administration [sessionScope]="state.scope" /> }
        @else { <workflow-assigned-tasks [sessionScope]="state.scope" [messages]="messages" /> }
      }
      <router-outlet />
    </div>
  `,
})
class InboxFixtureApp {
  readonly state = inject(InboxFixtureState);
  readonly router = inject(Router);
  administration = false;
  readonly messages = new URLSearchParams(location.search).get('myTasksEvidence') === 'long'
    ? { assignedTitle: 'My Tasks for international finance and operations reviewers' }
    : {};
}

@NgModule({
  declarations: [InboxFixtureApp, InboxFixtureDestination],
  imports: [BrowserModule, RouterOutlet, WorkflowAssignedTasksComponent, WorkflowTaskAdministrationComponent, WorkflowTaskComponent],
  providers: [
    InboxFixtureState,
    { provide: WORKFLOW_RUNTIME_READS, useClass: InboxFixtureReads },
    { provide: WORKFLOW_VIEWER_NAVIGATION, useClass: InboxFixtureNavigation },
    { provide: ENVIRONMENT_INITIALIZER, multi: true,
      useValue: () => inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined', 'mat-ligature-font') },
    provideRouter([{ path: 'alternate/task/:id', component: InboxFixtureDestination }]),
  ],
  bootstrap: [InboxFixtureApp],
})
class InboxFixtureModule {}

platformBrowser().bootstrapModule(InboxFixtureModule).catch(console.error);
