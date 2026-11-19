export type WorkflowTaskFormValueKind = 'string' | 'integer' | 'number' | 'boolean' | 'object' | 'array' | 'null';
export type WorkflowTaskFormFormat = 'date' | 'date-time' | 'uuid';

export interface WorkflowTaskFormLimits {
  readonly minLength?: string;
  readonly maxLength?: string;
  readonly minItems?: string;
  readonly maxItems?: string;
  readonly minFields?: string;
  readonly maxFields?: string;
  readonly minimum?: string;
  readonly maximum?: string;
  readonly exclusiveMinimum?: string;
  readonly exclusiveMaximum?: string;
}

export interface WorkflowTaskFormChoice {
  readonly label: string;
  /** A JSON value encoded as text to preserve exact numeric choices. */
  readonly value: string;
}

export interface WorkflowTaskFormRule {
  readonly label?: string;
  readonly help?: string;
  readonly hint?: string;
  readonly kinds?: readonly WorkflowTaskFormValueKind[];
  readonly allowed?: readonly string[];
  readonly matches?: string;
  readonly requiredFields?: readonly string[];
  readonly fields?: readonly {readonly key: string; readonly rule: WorkflowTaskFormRule}[];
  readonly closed?: boolean;
  readonly item?: WorkflowTaskFormRule;
  readonly limits?: WorkflowTaskFormLimits;
  readonly format?: WorkflowTaskFormFormat;
  readonly all?: readonly WorkflowTaskFormRule[];
  readonly alternatives?: readonly WorkflowTaskFormRule[];
  readonly dependencies?: readonly WorkflowTaskFormDependency[];
}

export interface WorkflowTaskFormDependency {
  readonly when: WorkflowTaskFormRule;
  readonly then: WorkflowTaskFormRule;
  readonly otherwise?: WorkflowTaskFormRule;
}

export interface WorkflowTaskFormField {
  readonly kind: Exclude<WorkflowTaskFormValueKind, 'null'> | 'choice' | 'fixed';
  readonly key?: string;
  readonly label: string;
  readonly help?: string;
  readonly hint?: string;
  readonly required?: boolean;
  readonly nullable?: boolean;
  readonly inferred?: boolean;
  readonly format?: WorkflowTaskFormFormat;
  readonly choices?: readonly WorkflowTaskFormChoice[];
  readonly fixed?: string;
  readonly fields?: readonly WorkflowTaskFormField[];
  readonly item?: WorkflowTaskFormField;
  readonly selectorLabel?: string;
  readonly selectorKey?: string;
  readonly alternatives?: readonly {readonly label: string; readonly field: WorkflowTaskFormField}[];
  readonly limits?: WorkflowTaskFormLimits;
  readonly dependencies?: readonly WorkflowTaskFormDependency[];
  readonly rule?: WorkflowTaskFormRule;
}

type WireDependency = {
  readonly when: WireRule;
  readonly then: WireRule;
  readonly otherwise?: WireRule;
};

type WireText = string | {readonly textId: number; readonly text?: string};
type WithWireText<T, Keys extends keyof T> = {[Key in keyof T]: Key extends Keys ? WireText : T[Key]};
type WireChoice = WithWireText<WorkflowTaskFormChoice, 'label' | 'value'>;

type WireRule = number | (Omit<WithWireText<WorkflowTaskFormRule, 'label' | 'help' | 'hint' | 'matches'>,
  'fields' | 'item' | 'all' | 'alternatives' | 'dependencies' | 'allowed' | 'requiredFields'> & {
  readonly ruleId?: number;
  readonly fields?: readonly {readonly key: WireText; readonly rule: WireRule}[];
  readonly allowed?: readonly WireText[];
  readonly requiredFields?: readonly WireText[];
  readonly item?: WireRule;
  readonly all?: readonly WireRule[];
  readonly alternatives?: readonly WireRule[];
  readonly dependencies?: readonly WireDependency[];
});

export type WorkflowTaskFormWireField = number | (Omit<WithWireText<WorkflowTaskFormField,
  'key' | 'label' | 'help' | 'hint' | 'fixed' | 'selectorKey' | 'selectorLabel'>,
  'fields' | 'item' | 'alternatives' | 'dependencies' | 'rule' | 'choices'> & {
  readonly formId?: number;
  readonly choices?: readonly WireChoice[];
  readonly fields?: readonly WorkflowTaskFormWireField[];
  readonly item?: WorkflowTaskFormWireField;
  readonly alternatives?: readonly {readonly label: WireText; readonly field: WorkflowTaskFormWireField}[];
  readonly dependencies?: readonly WireDependency[];
  readonly rule?: WireRule;
});

class FormDescriptors<Source extends object, Result> {
  private readonly byId = new Map<number, Source>();
  private readonly resolved = new WeakMap<Source, Result>();
  private readonly resolving = new WeakSet<Source>();

  add(id: number | undefined, source: Source): void {
    if (id === undefined) return;
    if (this.byId.has(id)) throw new Error('Duplicate task form descriptor.');
    this.byId.set(id, source);
  }

  resolve(reference: number | Source, hydrate: (source: Source) => Result): Result {
    const source = typeof reference === 'number' ? this.byId.get(reference) : reference;
    if (!source || this.resolving.has(source)) throw new Error('Invalid task form descriptor reference.');
    const previous = this.resolved.get(source);
    if (previous !== undefined) return previous;
    this.resolving.add(source);
    const result = hydrate(source);
    this.resolving.delete(source);
    this.resolved.set(source, result);
    return result;
  }
}

/** Resolve reusable control and rule descriptors into the named form consumed by task editors. */
export function hydrateTaskForm(form: WorkflowTaskFormWireField): WorkflowTaskFormField {
  const fields = new FormDescriptors<Exclude<WorkflowTaskFormWireField, number>, WorkflowTaskFormField>();
  const rules = new FormDescriptors<Exclude<WireRule, number>, WorkflowTaskFormRule>();
  const texts = new Map<number, string>();

  function indexText(value: WireText | undefined): void {
    if (!value || typeof value === 'string' || value.text === undefined) return;
    if (texts.has(value.textId)) throw new Error('Duplicate task form text.');
    texts.set(value.textId, value.text);
  }

  function readText(value: WireText): string;
  function readText(value: WireText | undefined): string | undefined;
  function readText(value: WireText | undefined): string | undefined {
    if (value === undefined || typeof value === 'string') return value;
    const text = texts.get(value.textId);
    if (text === undefined) throw new Error('Invalid task form text reference.');
    return text;
  }

  function indexField(field: WorkflowTaskFormWireField): void {
    if (typeof field === 'number') return;
    fields.add(field.formId, field);
    [field.key, field.label, field.help, field.hint, field.fixed, field.selectorKey, field.selectorLabel].forEach(indexText);
    field.choices?.forEach(choice => [choice.label, choice.value].forEach(indexText));
    field.fields?.forEach(indexField);
    if (field.item !== undefined) indexField(field.item);
    field.alternatives?.forEach(option => {
      indexText(option.label);
      indexField(option.field);
    });
    field.dependencies?.forEach(indexDependency);
    if (field.rule !== undefined) indexRule(field.rule);
  }

  function indexRule(rule: WireRule): void {
    if (typeof rule === 'number') return;
    rules.add(rule.ruleId, rule);
    [rule.label, rule.help, rule.hint, rule.matches].forEach(indexText);
    rule.allowed?.forEach(indexText);
    rule.requiredFields?.forEach(indexText);
    rule.fields?.forEach(field => {
      indexText(field.key);
      indexRule(field.rule);
    });
    if (rule.item !== undefined) indexRule(rule.item);
    rule.all?.forEach(indexRule);
    rule.alternatives?.forEach(indexRule);
    rule.dependencies?.forEach(indexDependency);
  }

  function indexDependency(dependency: WireDependency): void {
    indexRule(dependency.when);
    indexRule(dependency.then);
    if (dependency.otherwise !== undefined) indexRule(dependency.otherwise);
  }

  function readField(field: WorkflowTaskFormWireField): WorkflowTaskFormField {
    return fields.resolve(field, ({formId, fields, item, alternatives, dependencies, rule, choices, ...control}) => ({
      ...control,
      key: readText(control.key),
      label: readText(control.label),
      help: readText(control.help),
      hint: readText(control.hint),
      fixed: readText(control.fixed),
      selectorKey: readText(control.selectorKey),
      selectorLabel: readText(control.selectorLabel),
      choices: choices?.map(choice => ({label: readText(choice.label), value: readText(choice.value)})),
      fields: fields?.map(readField),
      item: item === undefined ? undefined : readField(item),
      alternatives: alternatives?.map(option => ({label: readText(option.label), field: readField(option.field)})),
      dependencies: dependencies?.map(readDependency),
      rule: rule === undefined ? undefined : readRule(rule),
    }));
  }

  function readRule(rule: WireRule): WorkflowTaskFormRule {
    return rules.resolve(rule, ({ruleId, fields, item, all, alternatives, dependencies, allowed, requiredFields, ...constraint}) => ({
      ...constraint,
      label: readText(constraint.label),
      help: readText(constraint.help),
      hint: readText(constraint.hint),
      matches: readText(constraint.matches),
      allowed: allowed?.map(value => readText(value)),
      requiredFields: requiredFields?.map(value => readText(value)),
      fields: fields?.map(field => ({key: readText(field.key), rule: readRule(field.rule)})),
      item: item === undefined ? undefined : readRule(item),
      all: all?.map(readRule),
      alternatives: alternatives?.map(readRule),
      dependencies: dependencies?.map(readDependency),
    }));
  }

  function readDependency(dependency: WireDependency): WorkflowTaskFormDependency {
    return {
      when: readRule(dependency.when),
      then: readRule(dependency.then),
      otherwise: dependency.otherwise === undefined ? undefined : readRule(dependency.otherwise),
    };
  }

  indexField(form);
  return readField(form);
}
