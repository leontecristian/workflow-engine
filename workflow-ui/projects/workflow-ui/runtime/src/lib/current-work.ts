import type { TaskView, WorkflowRuntimeReads } from '@workflow-engine/ui';

/** Discover authorized current work across every page; receipts remain immutable snapshots. */
export async function currentWork(api: WorkflowRuntimeReads, instanceId: string, signal: AbortSignal): Promise<TaskView[]> {
  const tasks: TaskView[] = [];
  let cursor: string | undefined;
  do {
    const page = await api.instanceTasks(instanceId, cursor, 50, signal);
    if (signal.aborted) return [];
    tasks.push(...page.items.filter(task => task.status === 'OPEN'));
    const next = page.nextAfterId;
    if (next != null && (!/^[1-9][0-9]*$/.test(next) || (cursor !== undefined && BigInt(next) <= BigInt(cursor)))) {
      throw new Error('Invalid current-task page cursor.');
    }
    cursor = next ?? undefined;
  } while (cursor !== undefined);
  return tasks;
}
