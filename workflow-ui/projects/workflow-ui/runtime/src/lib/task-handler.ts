import { InjectionToken, type Provider, type Type } from '@angular/core';
import type { TaskContract } from '@workflow-engine/ui';

export interface WorkflowTaskDecision {
  readonly outcomeKey: string;
  readonly comment: string;
  readonly resultJson?: string;
}

export interface WorkflowTaskRejection {
  readonly message?: string;
  readonly fieldErrors?: readonly {path: string; message: string}[];
}

export type WorkflowEditableTaskContract = TaskContract & {
  taskType: string;
  outcomes: (TaskContract['outcomes'][number] & {key: string})[];
};

export interface WorkflowTaskHandlerInputs {
  contract: WorkflowEditableTaskContract;
  editable: boolean;
  /** Resolves after shared handling, including when the decision is rejected. */
  submitDecision: (decision: WorkflowTaskDecision) => Promise<void | WorkflowTaskRejection>;
}

export type WorkflowTaskEditorRegistration = Readonly<Record<string, Type<WorkflowTaskHandlerInputs>>>;

export const WORKFLOW_TASK_EDITORS = new InjectionToken<ReadonlyMap<string, Type<WorkflowTaskHandlerInputs>>>(
  'Workflow task editors',
);

/** Register standalone components by exact pinned task type in this injector. */
export function provideWorkflowTaskEditors(registration: WorkflowTaskEditorRegistration): Provider {
  return { provide: WORKFLOW_TASK_EDITORS, useValue: new Map(Object.entries(registration)) };
}
