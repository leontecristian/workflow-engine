import type { TaskView, WorkflowRuntimeReads } from '@workflow-engine/ui';

/** Discover authorized current work across every page; receipts remain immutable snapshots. */
export async function currentWork(api: WorkflowRuntimeReads, instanceId: string, signal: AbortSignal): Promise<TaskView[]> {
  const tasks: TaskView[] = [];
  const seen = new Set<string>();
  let cursor: string | undefined;
  do {
    const page = await api.instanceTasks(instanceId, cursor, 50, signal);
    if (signal.aborted) return [];
    tasks.push(...page.items.filter(task => task.status === 'OPEN'));
    const next = page.nextCursor;
    if (next != null && (typeof next !== 'string' || !next || seen.has(next))) {
      throw new Error('Invalid current-task page cursor.');
    }
    if (next) seen.add(next);
    cursor = next ?? undefined;
  } while (cursor !== undefined);
  return tasks;
}
