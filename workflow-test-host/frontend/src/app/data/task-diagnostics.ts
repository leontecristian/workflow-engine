import { Injectable } from '@angular/core';
import { parseWorkflowHttpJson } from '@workflow-engine/ui/http';
import { CONSOLE_WORKFLOW_API } from './workflow-http-session';

export interface TaskDiagnostics {
  task: { id: string; activityId: string; instanceId: string };
  taskJson: string;
  contractJson: string;
  submissionJson?: string;
  resultJson?: string;
}

@Injectable({providedIn:'root'})
export class ConsoleTaskDiagnostics {
  async readTaskDiagnostics(id: string, signal: AbortSignal): Promise<TaskDiagnostics> {
    // An inspection failure is local to the disclosure, even if the server returns 401.
    const response = await fetch(CONSOLE_WORKFLOW_API + '/tasks/' + encodeURIComponent(id) + '/diagnostics',
      { credentials: 'same-origin', signal });
    if (!response.ok) throw new Error('Task diagnostics are unavailable.');
    const { value, objectAt } = parseWorkflowHttpJson<{ task: TaskDiagnostics['task']; submission?: object | null }>(
      await response.text(), 16 * 1024 * 1024);
    const taskJson = objectAt(['task']);
    const contractJson = objectAt(['contract']);
    if (!taskJson || !contractJson) throw new Error('Invalid diagnostics response.');
    return {
      task: value.task,
      taskJson,
      contractJson,
      submissionJson: value.submission ? objectAt(['submission']) : undefined,
      resultJson: value.submission ? objectAt(['submission', 'result']) : undefined,
    };
  }
}
