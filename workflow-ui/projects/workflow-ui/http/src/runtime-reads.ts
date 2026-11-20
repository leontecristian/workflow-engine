import { hydrateTaskForm } from '@workflow-engine/ui';
import type {
  SubmissionPage, TaskContract, TaskInput, TaskSubmission, VariablePage,
  WorkflowRuntimeReads, WorkflowTaskFormWireField,
} from '@workflow-engine/ui';
import { findNodeAtLocation, parseTree, visit, type JSONPath } from 'jsonc-parser';
import { runtimeRead } from './runtime-errors';
import { workflowUrl, type WorkflowHttpTransport } from './transport';

const LARGE_READ_LIMIT = 16 * 1024 * 1024;

/** Parse a bounded response and expose its original JSON tokens by path. */
export function parseWorkflowHttpJson<T>(text: string, limit = 1024 * 1024) {
  if (text.length > limit) throw new Error('Response JSON exceeds the size limit.');

  let depth = 0;
  const enter = () => {
    if (++depth > 128) throw new Error('Response JSON exceeds the nesting limit.');
  };
  const exit = () => { depth--; };
  visit(text, { onObjectBegin: enter, onArrayBegin: enter, onObjectEnd: exit, onArrayEnd: exit });

  const value = JSON.parse(text) as T;
  let tree: ReturnType<typeof parseTree>;
  function tokenAt(location: JSONPath, objectOnly = false): string | undefined {
    tree ??= parseTree(text);
    const node = tree && findNodeAtLocation(tree, location);
    if (!node) return undefined;
    if (objectOnly && node.type !== 'object') throw new Error('Invalid read response.');
    return text.slice(node.offset, node.offset + node.length);
  }

  return { value, tokenAt, objectAt: (location: JSONPath) => tokenAt(location, true) };
}

type WireTaskContract = Omit<TaskContract, 'inputs' | 'resultContract'> & {
  inputs?: Record<string, { type: TaskInput['type']; value: unknown }>;
  resultContract?: {
    mode: 'FORBIDDEN' | 'OPTIONAL' | 'REQUIRED';
    form?: WorkflowTaskFormWireField;
  };
};
type WireSubmission = Omit<TaskSubmission, 'resultJson'> & { result?: object };

function exactSubmission<T extends WireSubmission>(row: T, resultJson: string | undefined) {
  const { result, ...fields } = row;
  return resultJson === undefined ? fields : { ...fields, resultJson };
}

export function createWorkflowRuntimeReadsHttp(transport: WorkflowHttpTransport, basePath: string): WorkflowRuntimeReads {
  const instance = (id: string) => `/instances/${encodeURIComponent(id)}`;
  const task = (id: string) => `/tasks/${encodeURIComponent(id)}`;
  const activity = (id: string) => `/activities/${encodeURIComponent(id)}`;

  return {
    listInstances: (status, afterId, limit, signal) =>
      runtimeRead(transport.json(workflowUrl(basePath, '/instances', { status, afterId, limit }), { signal })),

    async variables(id, afterKey, expectedLockVersion, signal) {
      const url = workflowUrl(basePath, `${instance(id)}/variables`, { afterKey, expectedLockVersion, limit: 50 });
      const text = await runtimeRead(transport.text(url, { signal }));
      const { value } = parseWorkflowHttpJson<VariablePage>(text, LARGE_READ_LIMIT);
      Object.defineProperty(value, 'exactJson', { value: text });
      return value;
    },

    administrationTasks: (filters, cursor, limit, signal) =>
      runtimeRead(transport.json(workflowUrl(basePath, '/tasks/administration', { ...filters, cursor, limit }), { signal })),

    assignedTasks: (cursor, limit, signal) =>
      runtimeRead(transport.json(workflowUrl(basePath, '/tasks/mine', { cursor, limit }), { signal })),

    instanceTasks: (id, cursor, limit, signal) =>
      runtimeRead(transport.json(workflowUrl(basePath, `${instance(id)}/tasks`, { cursor, limit }), { signal })),

    task: (id, signal) => runtimeRead(transport.json(workflowUrl(basePath, task(id)), { signal })),

    async taskContract(id, signal) {
      const text = await runtimeRead(transport.text(workflowUrl(basePath, `${task(id)}/contract`), { signal }));
      const { value: { inputs, resultContract, ...contract }, tokenAt } =
        parseWorkflowHttpJson<WireTaskContract>(text, LARGE_READ_LIMIT);
      const result: TaskContract['resultContract'] = resultContract?.mode === 'FORBIDDEN'
        ? { mode: 'FORBIDDEN' }
        : resultContract && {
          mode: resultContract.mode,
          form: resultContract.form === undefined ? undefined : hydrateTaskForm(resultContract.form),
        };
      if (inputs === undefined) return { ...contract, resultContract: result };
      const exactInputs = Object.fromEntries(Object.entries(inputs).map(([key, input]) => {
        const valueJson = tokenAt(['inputs', key, 'value']);
        if (valueJson === undefined) throw new Error('Invalid read response.');
        return [key, { type: input.type, valueJson }];
      }));
      return { ...contract, resultContract: result, inputs: exactInputs };
    },

    previousDecisions: (id, cursor, limit, signal) =>
      runtimeRead(transport.json(workflowUrl(basePath, `${task(id)}/previous-decisions`, { cursor, limit }), { signal })),

    async taskSubmission(id, signal) {
      const text = await runtimeRead(transport.text(workflowUrl(basePath, `${task(id)}/submission`), { signal }));
      const { value, objectAt } = parseWorkflowHttpJson<WireSubmission | null>(text);
      if (value === null) return null;
      if (typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid read response.');
      return exactSubmission(value, objectAt(['result']));
    },

    activity: (id, signal) => runtimeRead(transport.json(workflowUrl(basePath, activity(id)), { signal })),

    async submissions(id, afterTaskId, limit, signal) {
      const url = workflowUrl(basePath, `${activity(id)}/submissions`, { afterTaskId, limit });
      const text = await runtimeRead(transport.text(url, { signal }));
      const { value, objectAt } = parseWorkflowHttpJson<
        Omit<SubmissionPage, 'items'> & { items: (WireSubmission & { taskId: string })[] }
      >(text);
      return { ...value, items: value.items.map((row, index) => exactSubmission(row, objectAt(['items', index, 'result']))) };
    },
  };
}
