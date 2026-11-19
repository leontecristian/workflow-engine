import type { WorkflowTaskFormFormat, WorkflowTaskFormLimits, WorkflowTaskFormRule } from '@workflow-engine/ui';
import { FormNumber, type FormValue, isFormObject, matchesKind, readFormLiteral, sameFormValue } from './task-form-values';

const literals = new WeakMap<WorkflowTaskFormRule, {allowed?: FormValue[]; fixed?: FormValue}>();

function ruleLiterals(rule: WorkflowTaskFormRule) {
  let values = literals.get(rule);
  if (!values) {
    values = { allowed: rule.allowed?.map(readFormLiteral), fixed: rule.matches === undefined ? undefined : readFormLiteral(rule.matches) };
    literals.set(rule, values);
  }
  return values;
}

export function hasFormat(value: string, format: WorkflowTaskFormFormat): boolean {
  if (format === 'uuid') return value.length === 36 && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/iu.test(value);
  const date = /^(\d{4})-(\d\d)-(\d\d)/u.exec(value);
  if (!date) return false;
  const [, yearText, monthText, dayText] = date;
  const year = Number(yearText), month = Number(monthText), day = Number(dayText);
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const monthDays = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (month < 1 || month > 12 || day < 1 || day > monthDays[month - 1]) return false;
  return format === 'date' ? value.length === 10
    : /^t(?:[01]\d|2[0-3]):[0-5]\d:(?:[0-5]\d|60)(?:\.\d+)?(?:z|[+-](?:[01]\d|2[0-3]):[0-5]\d)(?![\s\S])/iu.test(value.slice(10));
}

export const formatHelp: Readonly<Record<WorkflowTaskFormFormat, string>> = {
  date: 'Use YYYY-MM-DD.',
  'date-time': 'Include the time and offset, for example 2026-10-31T14:30:00+02:00.',
  uuid: 'Enter a UUID in the form 550e8400-e29b-41d4-a716-446655440000.',
};

function compare(value: FormNumber, limit: string): number {
  return value.compare(FormNumber.parse(limit)!);
}

export function atItemLimit(count: number, limits: readonly WorkflowTaskFormLimits[]): boolean {
  const value = FormNumber.parse(String(count))!;
  return count >= 1000 || limits.some(limit => limit.maxItems !== undefined && compare(value, limit.maxItems) >= 0);
}

export function listGuidance(limits: readonly WorkflowTaskFormLimits[]): string[] {
  return [...new Set(limits.flatMap(limit => [
    ...(limit.minItems !== undefined ? [`Minimum ${limit.minItems}`] : []),
    ...(limit.maxItems !== undefined ? [`Maximum ${limit.maxItems}`] : []),
  ]))];
}

function countIssue(count: number, minimum: string | undefined, maximum: string | undefined, noun: string): string | undefined {
  const value = FormNumber.parse(String(count))!;
  if (minimum !== undefined && compare(value, minimum) < 0) return `Use at least ${minimum} ${noun}.`;
  if (maximum !== undefined && compare(value, maximum) > 0) return `Use at most ${maximum} ${noun}.`;
  return undefined;
}

function limitIssue(value: FormValue, limits: WorkflowTaskFormLimits): string | undefined {
  if (typeof value === 'string') return countIssue([...value].length, limits.minLength, limits.maxLength, 'characters');
  if (Array.isArray(value)) return countIssue(value.length, limits.minItems, limits.maxItems, 'items');
  if (isFormObject(value)) return countIssue(Object.keys(value).length, limits.minFields, limits.maxFields, 'fields');
  if (value instanceof FormNumber) {
    if (limits.minimum !== undefined && compare(value, limits.minimum) < 0) return `Enter ${limits.minimum} or more.`;
    if (limits.maximum !== undefined && compare(value, limits.maximum) > 0) return `Enter ${limits.maximum} or less.`;
    if (limits.exclusiveMinimum !== undefined && compare(value, limits.exclusiveMinimum) <= 0) return `Enter more than ${limits.exclusiveMinimum}.`;
    if (limits.exclusiveMaximum !== undefined && compare(value, limits.exclusiveMaximum) >= 0) return `Enter less than ${limits.exclusiveMaximum}.`;
  }
  return undefined;
}

export function directIssue(value: FormValue, rule: WorkflowTaskFormRule): string | undefined {
  if (rule.kinds && !rule.kinds.some(kind => matchesKind(value, kind))) {
    if (rule.kinds.includes('integer')) return 'Enter a whole number.';
    if (rule.kinds.includes('number')) return 'Enter a number.';
    return 'Choose a permitted value.';
  }
  const { allowed, fixed } = ruleLiterals(rule);
  if (fixed !== undefined && !sameFormValue(fixed, value)) return 'Use the fixed value for this answer.';
  if (allowed && !allowed.some(choice => sameFormValue(choice, value))) return 'Choose one of the listed answers.';
  if (typeof value === 'string' && rule.format && !hasFormat(value, rule.format)) return formatHelp[rule.format];
  return rule.limits && limitIssue(value, rule.limits);
}

export function matchesRule(value: FormValue | undefined, rule: WorkflowTaskFormRule): boolean {
  if (value === undefined || directIssue(value, rule)) return false;
  if (isFormObject(value)) {
    if (rule.requiredFields?.some(key => !Object.hasOwn(value, key))) return false;
    if (rule.closed && Object.keys(value).some(key => !rule.fields?.some(field => field.key === key))) return false;
    if (rule.fields?.some(({key, rule: field}) => Object.hasOwn(value, key) && !matchesRule(value[key], field))) return false;
  }
  if (Array.isArray(value) && rule.item && value.some(item => !matchesRule(item, rule.item!))) return false;
  if (rule.alternatives && rule.alternatives.filter(option => matchesRule(value, option)).length !== 1) return false;
  return !rule.all?.some(part => !matchesRule(value, part)) && !rule.dependencies?.some(dependency => {
    const active = matchesRule(value, dependency.when) ? dependency.then : dependency.otherwise;
    return active !== undefined && !matchesRule(value, active);
  });
}

export function activeRules(value: FormValue | undefined, rules: readonly WorkflowTaskFormRule[]): WorkflowTaskFormRule[] {
  return rules.flatMap(rule => {
    const branches = rule.dependencies?.map(dependency =>
      matchesRule(value, dependency.when) ? dependency.then : dependency.otherwise).filter(branch => branch !== undefined) ?? [];
    return [rule, ...activeRules(value, [...(rule.all ?? []), ...branches])];
  });
}
