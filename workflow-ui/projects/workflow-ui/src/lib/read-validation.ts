import type { InstanceView, InstanceGraph, HistoryPage, HistoryEvent, HistorySummary } from './models';
import { ViewerReadError } from './contracts';

const object = (value: unknown): value is Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value);
const text = (value: unknown) => typeof value === 'string';
const nullableText = (value: unknown) => value === null || text(value);
const decimal = (value: unknown, zero = false) => typeof value === 'string'
  && (zero ? /^(0|[1-9][0-9]*)$/ : /^[1-9][0-9]*$/).test(value)
  && (value.length < 19 || value.length === 19 && value <= '9223372036854775807');
const nullableId = (value: unknown) => value === null || decimal(value);
function requireValid(valid: boolean): asserts valid { if (!valid) throw new ViewerReadError('FAILED'); }
export function validateInstance(value: unknown, id: string): asserts value is InstanceView {
  requireValid(object(value));
  requireValid(value['id'] === id && decimal(id) && decimal(value['definitionVersionId'])
    && Number.isInteger(value['definitionVersion']) && text(value['definitionKey']) && text(value['definitionHash'])
    && ['ACTIVE','COMPLETED','CANCELLED'].includes(String(value['status'])) && text(value['currentNodeKey'])
    && decimal(value['lockVersion'], true) && decimal(value['lastHistorySequence'], true)
    && ['businessType','businessId','resultKey','endedAt','endedBy','terminationReason'].every(key => nullableText(value[key]))
    && text(value['startedAt']) && text(value['startedBy']));
}
function summary(value: unknown, id: string): value is HistorySummary {
  return object(value) && value['instanceId'] === id && decimal(value['historySequence'])
    && ['eventType','commandId','actorKind','actorRef','occurredAt'].every(key => text(value[key]))
    && ['correlationId','sourceNodeKey','targetNodeKey','transitionKey'].every(key => nullableText(value[key]))
    && nullableId(value['activityId']) && nullableId(value['taskId']) && Number.isInteger(value['detailsSchemaVersion']);
}
export function validateHistory(value: unknown, id: string, after: string): asserts value is HistoryPage {
  requireValid(object(value) && Array.isArray(value['items']) && value['items'].length <= 100 && nullableId(value['nextAfterSequence']));
  let cursor = BigInt(after);
  for (const item of value['items'] as unknown[]) {
    requireValid(summary(item, id) && BigInt(item.historySequence) > cursor); cursor = BigInt(item.historySequence);
  }
  requireValid(value['nextAfterSequence'] === null || (value['items'] as unknown[]).length > 0 && BigInt(value['nextAfterSequence'] as string) === cursor);
}
export function validateEvent(value: unknown, id: string, sequence: string): asserts value is HistoryEvent {
  requireValid(object(value) && summary(value['summary'], id) && value['summary'].historySequence === sequence && text(value['detailsJson']));
}
export function validateGraph(value: unknown, id: string): asserts value is InstanceGraph {
  requireValid(object(value)); validateInstance(value['instance'], id);
  requireValid(Array.isArray(value['nodes']) && value['nodes'].length > 0 && value['nodes'].length <= 200
    && Array.isArray(value['edges']) && value['edges'].length <= 500);
  const keys = new Set<string>();
  for (const node of value['nodes'] as unknown[]) {
    requireValid(object(node) && text(node['key']) && text(node['name']) && text(node['type'])
      && ['ACTIVE','COMPLETED','PENDING'].includes(String(node['state'])) && nullableId(node['activityId']) && nullableId(node['taskId']) && !keys.has(node['key'] as string));
    keys.add(node['key'] as string);
  }
  for (const edge of value['edges'] as unknown[]) requireValid(object(edge) && text(edge['key']) && keys.has(edge['from'] as string)
    && keys.has(edge['to'] as string) && nullableText(edge['outcome']) && typeof edge['traversed'] === 'boolean');
}
