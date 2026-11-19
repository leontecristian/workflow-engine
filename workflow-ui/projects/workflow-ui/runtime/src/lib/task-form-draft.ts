import type {
  WorkflowTaskFormChoice, WorkflowTaskFormField, WorkflowTaskFormFormat, WorkflowTaskFormLimits, WorkflowTaskFormRule,
} from '@workflow-engine/ui';
import { activeRules, atItemLimit, directIssue, formatHelp, listGuidance, matchesRule } from './task-form-rules';
import { FormNumber, type FormValue, isFormObject, readFormLiteral, sameFormValue, writeFormValue } from './task-form-values';

export class TaskAnswer {
  private static nextId = 0;
  readonly descriptionId = `workflow-field-${TaskAnswer.nextId++}-description`;
  presence: 'present' | 'null' | 'omitted' | undefined;
  text: string | undefined;
  literal: string | undefined;
  alternative: number | undefined;
  readonly items: TaskAnswer[] = [];
  readonly fields = new Map<string, TaskAnswer>();
  readonly branches = new Map<number, TaskAnswer>();

  field(key: string): TaskAnswer {
    let answer = this.fields.get(key);
    if (!answer) this.fields.set(key, answer = new TaskAnswer());
    return answer;
  }

  branch(index: number): TaskAnswer {
    let answer = this.branches.get(index);
    if (!answer) this.branches.set(index, answer = new TaskAnswer());
    return answer;
  }

  moveItem(index: number, offset: number): void {
    const to = index + offset;
    if (index < 0 || index >= this.items.length || to < 0 || to >= this.items.length) return;
    const [item] = this.items.splice(index, 1);
    this.items.splice(to, 0, item);
  }
}

export interface TaskFieldView {
  readonly field: WorkflowTaskFormField;
  readonly answer: TaskAnswer;
  readonly path: string;
  readonly required: boolean;
  readonly included: boolean;
  readonly isNull: boolean;
  readonly canBeNull: boolean;
  readonly kind: WorkflowTaskFormField['kind'];
  readonly text: string;
  readonly selectedChoice: string | undefined;
  readonly choices: readonly WorkflowTaskFormChoice[];
  readonly fixed: string | undefined;
  readonly format: WorkflowTaskFormFormat | undefined;
  readonly guidance: readonly string[];
  readonly children: readonly TaskFieldView[];
  readonly branch?: TaskFieldView;
  readonly atItemLimit: boolean;
  readonly issue: string | undefined;
  readonly valid: boolean;
  readonly value: FormValue | undefined;
}

const booleanChoices: readonly WorkflowTaskFormChoice[] = [
  { label: 'Yes', value: 'true' }, { label: 'No', value: 'false' },
];

function readableChoice(value: FormValue): string {
  if (value === null) return 'No value';
  if (typeof value === 'string') return value || 'Empty text';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (Array.isArray(value)) return value.length ? value.map(readableChoice).join(', ') : 'Empty list';
  if (isFormObject(value)) {
    return Object.entries(value).map(([key, child]) => `${key} · ${readableChoice(child)}`).join(', ') || 'Empty group';
  }
  return value.text;
}

function objectValue(children: readonly {field: WorkflowTaskFormField; value: FormValue | undefined}[]): FormValue {
  return Object.fromEntries(children.flatMap(({field, value}) => value === undefined ? [] : [[field.key!, value]]));
}

function fieldPath(path: string, key: string): string {
  return path + '/' + key.replace(/~/gu, '~0').replace(/\//gu, '~1');
}

function fieldLabel(key: string): string {
  const text = key.replace(/([\p{Ll}\d])(\p{Lu})/gu, '$1 $2').replace(/[_-]+/gu, ' ').trim().toLowerCase();
  return text ? text[0].toUpperCase() + text.slice(1) : 'Value';
}

function fieldRule(field: WorkflowTaskFormField): WorkflowTaskFormRule {
  return {
    ...(field.kind === 'choice' || field.kind === 'fixed' || field.inferred ? {} : {
      kinds: field.nullable ? [field.kind, 'null'] : [field.kind],
    }),
    allowed: field.choices ? [...field.choices.map(choice => choice.value), ...(field.nullable ? ['null'] : [])] : undefined,
    matches: field.fixed,
    limits: field.limits,
    format: field.format,
    dependencies: field.dependencies,
  };
}

function scalarValue(kind: WorkflowTaskFormField['kind'], answer: TaskAnswer, fixed?: string): FormValue | undefined {
  if (fixed !== undefined) return readFormLiteral(fixed);
  if (answer.literal !== undefined) return readFormLiteral(answer.literal);
  if (kind === 'number' || kind === 'integer') return FormNumber.parse(answer.text ?? '');
  if (kind === 'string') return answer.text ?? '';
  return undefined;
}

function supplied(answer: TaskAnswer, required: boolean): boolean {
  return required || answer.presence === 'present' || answer.presence === 'null';
}

function roughValue(field: WorkflowTaskFormField, answer: TaskAnswer, required: boolean): FormValue | undefined {
  if (!supplied(answer, required)) return undefined;
  if (answer.presence === 'null') return null;
  if (field.fixed !== undefined || field.kind === 'choice' || field.kind === 'boolean') return scalarValue(field.kind, answer, field.fixed);
  if (field.alternatives) {
    const selected = answer.alternative === undefined ? undefined : field.alternatives[answer.alternative];
    return selected ? roughValue(selected.field, answer.branch(answer.alternative!), true) : undefined;
  }
  if (field.kind === 'object') {
    return objectValue(field.fields?.map(child => ({field: child, value: roughValue(child, answer.field(child.key!), !!child.required)})) ?? []);
  }
  if (field.kind === 'array') {
    const item = field.item;
    return item ? answer.items.map(draft => roughValue(item, draft, true)).filter(value => value !== undefined) : [];
  }
  return scalarValue(field.kind, answer);
}

function presentChoices(field: WorkflowTaskFormField, kind: WorkflowTaskFormField['kind'], rules: readonly WorkflowTaskFormRule[]) {
  let choices = kind === 'boolean' ? booleanChoices : field.choices;
  if (!choices) choices = rules.find(rule => rule.allowed)?.allowed?.map(text => ({label: readableChoice(readFormLiteral(text)), value: text}));
  return choices?.filter(choice => {
    const value = readFormLiteral(choice.value);
    return value !== null && rules.every(rule => matchesRule(value, rule));
  }) ?? [];
}

function conjuncts(rules: readonly WorkflowTaskFormRule[]): WorkflowTaskFormRule[] {
  return rules.flatMap(rule => [rule, ...conjuncts(rule.all ?? [])]);
}

function impliedControl(key: string | undefined, label: string, rules: readonly WorkflowTaskFormRule[]): WorkflowTaskFormField {
  const parts = conjuncts(rules);
  const kinds = parts.flatMap(rule => rule.kinds ?? []);
  const kind = kinds.find(candidate => candidate !== 'null')
    ?? (parts.some(rule => rule.fields || rule.closed || rule.alternatives) ? 'object'
      : parts.some(rule => rule.item) ? 'array' : 'string');
  return { key, label: parts.find(rule => rule.label)?.label ?? label, kind, inferred: true,
    help: parts.find(rule => rule.help)?.help, hint: parts.find(rule => rule.hint)?.hint,
    nullable: !kinds.length || kinds.includes('null') };
}

function permittedKey(key: string, rules: readonly WorkflowTaskFormRule[]): boolean {
  return rules.every(rule => !rule.closed || rule.fields?.some(field => field.key === key));
}

function projectedAlternatives(rules: readonly WorkflowTaskFormRule[], field: WorkflowTaskFormField):
  Pick<WorkflowTaskFormField, 'alternatives' | 'selectorKey' | 'selectorLabel'> | undefined {
  if (field.alternatives) return field;
  const alternatives = rules.find(rule => rule.alternatives?.length)?.alternatives;
  if (!alternatives) return undefined;
  const layers = alternatives.map(rule => conjuncts([rule]));
  const keys = layers[0].flatMap(rule => rule.requiredFields ?? []);
  for (const key of keys) {
    const choices = layers.map(parts => {
      if (!parts.some(rule => rule.requiredFields?.includes(key))) return undefined;
      const propertyRules = parts.flatMap(rule => rule.fields?.filter(field => field.key === key).map(field => field.rule) ?? []);
      return conjuncts(propertyRules).find(rule => rule.matches !== undefined)?.matches;
    });
    if (choices.some(choice => choice === undefined || typeof readFormLiteral(choice) !== 'string')
      || new Set(choices).size !== choices.length) continue;
    return {
      selectorKey: key, selectorLabel: layers[0].flatMap(rule => rule.fields ?? [])
        .find(field => field.key === key)?.rule.label ?? fieldLabel(key),
      alternatives: alternatives.map((rule, index) => ({
        label: layers[index].find(part => part.label)?.label ?? fieldLabel(readFormLiteral(choices[index]!) as string),
        field: { kind: 'object', label: field.label, rule },
      })),
    };
  }
  return undefined;
}

function evaluate(
  field: WorkflowTaskFormField,
  answer: TaskAnswer,
  path: string,
  required: boolean,
  inherited: readonly WorkflowTaskFormRule[] = [],
): TaskFieldView {
  const included = supplied(answer, required);
  const rules = [fieldRule(field), ...(field.rule ? [field.rule] : []), ...inherited];
  let active = activeRules(roughValue(field, answer, required), rules);
  const seen: WorkflowTaskFormRule[][] = [];
  for (;;) {
    const view = evaluateActive(field, answer, path, required, included, active);
    const next = activeRules(view.value, rules);
    // Conditions can depend on answers introduced by other applicable rules, such as a fixed value.
    // A cycle can only be resolved by an explicit answer, so keep its controls available for editing.
    const same = (left: readonly WorkflowTaskFormRule[], right: readonly WorkflowTaskFormRule[]) =>
      left.length === right.length && left.every((rule, index) => rule === right[index]);
    if (same(active, next) || seen.some(previous => same(previous, next)) || seen.length >= rules.length + active.length) return view;
    seen.push(active);
    active = next;
  }
}

function evaluateActive(field: WorkflowTaskFormField, answer: TaskAnswer, path: string, required: boolean,
  included: boolean, active: readonly WorkflowTaskFormRule[]): TaskFieldView {
  const isNull = answer.presence === 'null';
  const canBeNull = !!field.nullable && active.every(rule => !directIssue(null, rule));
  const fixed = active.find(rule => rule.matches !== undefined)?.matches;
  const impliedKind = field.inferred ? active.flatMap(rule => rule.kinds ?? []).find(kind => kind !== 'null')
    ?? (active.some(rule => rule.fields || rule.alternatives || rule.closed) ? 'object'
      : active.some(rule => rule.item) ? 'array' : undefined) : undefined;
  const kind = fixed !== undefined ? 'fixed' : active.some(rule => rule.allowed !== undefined) ? 'choice' : impliedKind ?? field.kind;
  const options = kind === 'object' ? projectedAlternatives(active, field) : undefined;
  const displayedField = {
    ...field, ...options,
    label: active.find(rule => rule.label)?.label ?? field.label,
    help: active.find(rule => rule.help)?.help ?? field.help,
    hint: active.find(rule => rule.hint)?.hint ?? field.hint,
  };
  const choices = presentChoices(field, kind, active);
  const limits = active.flatMap(rule => rule.limits ? [rule.limits] : []);
  const format = active.find(rule => rule.format)?.format;
  const atLimit = atItemLimit(answer.items.length, limits);
  const guidance = [
    ...(format ? [formatHelp[format]] : []),
    ...(kind === 'array' ? listGuidance(limits) : []),
  ];

  let value: FormValue | undefined;
  let issue: string | undefined;
  let children: TaskFieldView[] = [];
  let branch: TaskFieldView | undefined;
  if (included) {
    if (isNull) {
      value = null;
      if (!canBeNull) issue = 'This answer needs a value.';
    } else if (options?.alternatives) {
      const selected = answer.alternative === undefined ? undefined : options.alternatives[answer.alternative];
      if (selected) {
        // The parent has already selected the active conjunctions and dependencies.
        // The selected shape inherits their other constraints without another shape selector.
        const branchRules = active.map(rule => ({ ...rule, all: undefined, dependencies: undefined, alternatives: undefined }));
        branch = evaluate(selected.field, answer.branch(answer.alternative!), path, true, branchRules);
        value = branch.value;
      } else issue = 'Choose a type for this answer.';
    } else if (kind === 'object') {
      const displayed = new Map<string, WorkflowTaskFormField | undefined>(field.fields?.map(child => [child.key!, child]));
      for (const rule of active) {
        for (const entry of rule.fields ?? []) {
          if (!displayed.has(entry.key)) displayed.set(entry.key, undefined);
        }
      }
      children = Array.from(displayed).flatMap(([key, child]) => {
        const childRules = active.flatMap(rule => rule.fields?.filter(entry => entry.key === key).map(entry => entry.rule) ?? []);
        const childRequired = !!child?.required || active.some(rule => rule.requiredFields?.includes(key));
        if (!permittedKey(key, active)) {
          if (childRequired) issue = 'Change the controlling answer to make this group available.';
          return [];
        }
        return [evaluate(child ?? impliedControl(key, fieldLabel(key), childRules), answer.field(key), fieldPath(path, key), childRequired, childRules)];
      });
      value = objectValue(children);
    } else if (kind === 'array') {
      const itemRules = active.flatMap(rule => rule.item ? [rule.item] : []);
      const item = field.item ?? impliedControl(undefined, 'Item', itemRules);
      children = answer.items.map((draft, index) => evaluate(item, draft, `${path}/${index}`, true, itemRules));
      value = children.flatMap(child => child.value === undefined ? [] : [child.value]);
    } else {
      value = scalarValue(kind, answer, fixed);
    }
    if (!issue && value === undefined && !branch) {
      issue = kind === 'integer' ? 'Enter a whole number.' : kind === 'number' ? 'Enter a number.' : 'Choose an answer.';
    }
    if (value !== undefined) {
      const candidate = value;
      issue ??= active.map(rule => directIssue(candidate, rule)).find(message => message !== undefined);
      if (value instanceof FormNumber && kind === 'integer' && !value.integer) issue ??= 'Enter a whole number.';
      if ((kind === 'choice' || kind === 'fixed' || options) && active.some(rule => !matchesRule(candidate, rule))) {
        if (branch?.valid ?? true) issue ??= 'Choose a permitted answer.';
      }
    }
  }
  const selectedChoice = value === undefined ? undefined : choices.find(choice => sameFormValue(readFormLiteral(choice.value), value!))?.value;
  return {
    field: displayedField, answer, path, required, included, isNull, canBeNull, kind, choices, selectedChoice, format, guidance, children, branch,
    atItemLimit: atLimit, issue, valid: !issue && children.every(child => child.valid) && (branch?.valid ?? true), value,
    text: answer.text ?? (typeof value === 'string' ? value : value instanceof FormNumber ? value.text : ''),
    fixed: fixed === undefined ? undefined : readableChoice(readFormLiteral(fixed)),
  };
}

export class TaskFormDraft {
  readonly root = new TaskAnswer();

  constructor(readonly field: WorkflowTaskFormField) {}

  view(): TaskFieldView { return evaluate(this.field, this.root, '', true); }

  result(view = this.view()): string | undefined {
    return view.valid && isFormObject(view.value) ? writeFormValue(view.value) : undefined;
  }
}
