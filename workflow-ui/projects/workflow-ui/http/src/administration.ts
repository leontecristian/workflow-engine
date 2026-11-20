import type {
  AdministrationPage, Change, DefinitionChange, DefinitionCheck, DefinitionDeletion, DefinitionView,
  TaskTypeView, VersionDetails, VersionSummary, WorkflowAdministrationApi,
} from '@workflow-engine/ui';
import { WorkflowHttpError, workflowErrorBody, workflowUrl } from './transport';
import type { WorkflowHttpTransport } from './transport';

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function hasTextFields(value: Record<string, unknown>, fields: string[]): boolean {
  return fields.every(field => typeof value[field] === 'string');
}

function isTaskType(value: unknown): boolean {
  return isRecord(value) && hasTextFields(value, ['id', 'lockVersion', 'code', 'name'])
    && typeof value['selectable'] === 'boolean' && isNullableText(value['description']);
}

function isNullableText(value: unknown): boolean {
  return value === null || typeof value === 'string';
}

function isDefinition(value: unknown): boolean {
  return isRecord(value) && hasTextFields(value, ['id', 'lockVersion', 'key', 'name', 'createdAt', 'createdBy'])
    && isNullableText(value['description']);
}

function isVersion(value: unknown): boolean {
  return isRecord(value)
    && hasTextFields(value, ['id', 'lockVersion', 'definitionId', 'key', 'contentRevision', 'contentHash',
      'canonicalizationVersion', 'createdAt', 'createdBy', 'updatedAt', 'updatedBy'])
    && Number.isInteger(value['version']) && Number.isInteger(value['schemaVersion'])
    && ['DRAFT', 'PUBLISHED', 'RETIRED'].some(status => status === value['status']);
}

function isChange(value: unknown, validSummary: (summary: unknown) => boolean): boolean {
  return isRecord(value) && typeof value['changed'] === 'boolean' && validSummary(value['after']);
}

const isTaskTypeChange = (value: unknown) => isChange(value, isTaskType);
const isDefinitionChange = (value: unknown) => isChange(value, isDefinition);
const isVersionChange = (value: unknown) => isChange(value, isVersion);

function isCheck(value: unknown): boolean {
  return isRecord(value) && isVersionChange(value['change']) && typeof value['published'] === 'boolean'
    && isRecord(value['report']) && typeof value['report']['valid'] === 'boolean'
    && Array.isArray(value['report']['diagnostics']) && typeof value['report']['detailsOmitted'] === 'boolean';
}

function isDeletion(value: unknown): boolean {
  return isRecord(value) && value['operation'] === 'DELETE_DRAFT' && isVersion(value['before']);
}

function unreadableResponse(write: boolean): Error {
  return new Error(write
    ? 'Write response unavailable. Inspect stored state before another write.'
    : 'Invalid read response.');
}

function administrationFailure(error: unknown, write: boolean): unknown {
  if (!(error instanceof WorkflowHttpError)) return error;
  const { status } = error;
  if (status === 0) {
    const message = write
      ? 'Write outcome unknown. Keep your text and inspect stored state before another write.'
      : 'Read failed. Refresh to try again.';
    return Object.assign(new Error(message), { httpStatus: status });
  }
  if (status >= 200 && status < 300) {
    // A successful status with an unreadable body is an unknown outcome to the administration page.
    return Object.assign(unreadableResponse(write), { status });
  }
  const { code, diagnostics = [], correlationId } = workflowErrorBody(error.body);
  const correlation = correlationId ? ` [${correlationId}]` : '';
  const details = diagnostics.length ? ' ' + JSON.stringify(diagnostics) : '';
  return Object.assign(new Error(`${status} ${code ?? 'REQUEST_FAILED'}${details}${correlation}`), {
    httpStatus: status, code, diagnostics, correlationId,
  });
}

export function createWorkflowAdministrationHttp(
  transport: WorkflowHttpTransport,
  basePath: string,
): WorkflowAdministrationApi {
  const definitionPath = (id: string) => '/definitions/' + encodeURIComponent(id);
  const versionPath = (id: string) => '/versions/' + encodeURIComponent(id);
  const taskTypePath = (id: string) => '/task-types/' + encodeURIComponent(id);

  async function read<T>(path: string, signal: AbortSignal, query?: Parameters<typeof workflowUrl>[2]): Promise<T> {
    try {
      return await transport.json<T>(workflowUrl(basePath, path, query), { signal }, isRecord);
    } catch (error) {
      throw administrationFailure(error, false);
    }
  }

  async function write<T>(
    path: string, method: 'POST' | 'PUT' | 'DELETE', signal: AbortSignal,
    valid: (value: unknown) => boolean, token?: string, body?: string,
  ): Promise<T> {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token !== undefined) headers['X-Expected-Lock-Version'] = token;
    try {
      return await transport.json<T>(workflowUrl(basePath, path), { method, headers, body, signal }, valid);
    } catch (error) {
      throw administrationFailure(error, true);
    }
  }

  return {
    listTaskTypes(filter, cursor, limit, signal) {
      return read<AdministrationPage<TaskTypeView>>('/task-types', signal, { limit, filter, afterId: cursor });
    },
    createTaskType(body, signal) {
      return write<Change<TaskTypeView>>('/task-types', 'POST', signal, isTaskTypeChange, undefined, JSON.stringify(body));
    },
    updateTaskType(id, body, token, signal) {
      return write<Change<TaskTypeView>>(taskTypePath(id), 'PUT', signal, isTaskTypeChange, token, JSON.stringify(body));
    },
    listDefinitions(cursor, limit, signal) {
      return read<AdministrationPage<DefinitionView>>('/definitions', signal, { limit, order: 'DESC', afterId: cursor });
    },
    definition(id, signal) {
      return read<DefinitionView>(definitionPath(id), signal);
    },
    saveMetadata(id, name, description, token, signal) {
      return write<Change<DefinitionView>>(definitionPath(id), 'PUT', signal, isDefinitionChange, token, JSON.stringify({ name, description }));
    },
    listVersions(id, status, cursor, limit, signal) {
      return read<AdministrationPage<VersionSummary>>(definitionPath(id) + '/versions', signal, {
        limit, status: status || undefined, afterId: cursor,
      });
    },
    version(id, signal) {
      return read<VersionDetails>(versionPath(id), signal);
    },
    lookupVersion(key, version, signal) {
      return read<VersionDetails>('/versions/lookup', signal, { key, version });
    },
    importDraft(document, signal) {
      return write<DefinitionChange>('/versions/import', 'POST', signal, isVersionChange, undefined, document);
    },
    saveDocument(id, document, token, signal) {
      return write<DefinitionChange>(versionPath(id) + '/document', 'PUT', signal, isVersionChange, token, document);
    },
    checkVersion(id, action, token, signal) {
      return write<DefinitionCheck>(versionPath(id) + '/' + action, 'POST', signal, isCheck, token);
    },
    retireVersion(id, token, signal) {
      return write<DefinitionChange>(versionPath(id) + '/retire', 'POST', signal, isVersionChange, token);
    },
    createDraftFromVersion(id, token, signal) {
      return write<DefinitionChange>(versionPath(id) + '/drafts', 'POST', signal, isVersionChange, token, '{}');
    },
    deleteDraft(id, token, signal) {
      return write<DefinitionDeletion>(versionPath(id), 'DELETE', signal, isDeletion, token);
    },
    async exportVersion(id, signal) {
      try {
        return await transport.text(workflowUrl(basePath, versionPath(id) + '/export'), { signal });
      } catch (error) {
        throw administrationFailure(error, false);
      }
    },
  };
}
