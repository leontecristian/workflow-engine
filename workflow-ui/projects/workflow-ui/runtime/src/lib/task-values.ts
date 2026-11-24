import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import type { TaskInput } from '@workflow-engine/ui';
import { parseTree, type Node, type ParseError } from 'jsonc-parser';

interface BusinessValue {
  label?: string;
  text?: string;
  kind?: 'empty-text' | 'null' | 'empty-object' | 'empty-array' | 'unavailable';
  children?: BusinessValue[];
  fields?: BusinessFields;
  table?: RecordTable;
}

interface RecordTable {
  columns: { label: string; numeric: boolean }[];
  rows: BusinessValue[][];
}

interface BusinessFields {
  rows: BusinessValue[];
  groups: BusinessValue[];
}

interface InputSection {
  value: BusinessValue;
  level: number;
  path: string[];
  items?: InputSection[][];
}

function isGroup(value: BusinessValue): boolean {
  return !!(value.fields || value.children || value.table);
}

function groupFields(values: BusinessValue[]): BusinessFields {
  const rows: BusinessValue[] = [];
  const groups: BusinessValue[] = [];
  for (const value of values) {
    (isGroup(value) ? groups : rows).push(value);
  }
  return { rows, groups };
}

function decimalDisplay(token: string): string {
  if (!token.includes('.') || /[eE]/.test(token)) return token;
  let end = token.length;
  while (token[end - 1] === '0') end--;
  if (token[end - 1] === '.') end--;
  return token.slice(0, end);
}

function recordTable(source: string, items: Node[]): RecordTable | undefined {
  if (items.length < 2) return;

  const records: Map<string, Node>[] = [];
  let keys: string[] = [];
  for (const item of items) {
    const properties = item.children ?? [];
    if (item.type !== 'object' || properties.length < 1 || properties.length > 6) return;

    const record = new Map<string, Node>();
    for (const property of properties) {
      const [key, value] = property.children!;
      if (record.has(key.value) || value.type === 'object' || value.type === 'array') return;
      record.set(key.value, value);
    }
    if (!records.length) keys = [...record.keys()];
    else if (record.size !== keys.length || !keys.every(key => record.has(key))) return;
    records.push(record);
  }

  const cells = records.map(record => keys.map(key => record.get(key)!));
  return {
    columns: keys.map((key, index) => ({
      label: fieldLabel(key, true),
      numeric: cells.some(row => row[index].type === 'number')
        && cells.every(row => row[index].type === 'number' || row[index].type === 'null'),
    })),
    rows: cells.map(row => row.map(value => businessValue(source, value, true))),
  };
}

/** Read numeric values from their source tokens to avoid a lossy JavaScript number conversion. */
function businessValue(source: string, node: Node, compact: boolean, label?: string): BusinessValue {
  if (node.type === 'object' || node.type === 'array') {
    const nodes = node.children ?? [];
    if (compact && node.type === 'array') {
      const table = recordTable(source, nodes);
      if (table) return { label, table };
    }
    const children = nodes.map((child, index) => {
      if (node.type === 'array') {
        const item = businessValue(source, child, compact, String(index + 1));
        if (compact && isGroup(item)) item.label = `Item ${index + 1}`;
        return item;
      }
      const [key, value] = child.children!;
      return businessValue(source, value, compact, fieldLabel(key.value, compact));
    });
    const placeholder = children.length ? undefined : node.type === 'object'
      ? { text: 'Empty group', kind: 'empty-object' as const }
      : { text: 'Empty list', kind: 'empty-array' as const };
    if (compact && node.type === 'object') return { label, fields: groupFields(children), ...placeholder };
    return compact || children.length ? { label, children, ...placeholder } : { label, ...placeholder };
  }
  if (node.type === 'number') {
    const token = source.slice(node.offset, node.offset + node.length);
    return { label, text: compact ? decimalDisplay(token) : token };
  }
  if (node.type === 'null') return { label, text: 'No value', kind: 'null' };
  if (node.type === 'string') {
    return node.value === '' ? { label, text: 'Empty text', kind: 'empty-text' } : { label, text: node.value };
  }
  return { label, text: node.value ? 'Yes' : 'No' };
}

function inputSections(group: BusinessValue, level: number, parentPath: string[] = []): InputSection[] {
  const sections: InputSection[] = [];
  function append(value: BusinessValue, depth: number, path: string[]): void {
    sections.push({
      value,
      level: depth,
      path: isGroup(value) ? (path.length ? path : [value.label!]) : [],
      items: value.children?.map(item => inputSections(item, depth + 1, [...path, item.label!])),
    });
    for (const child of value.fields?.groups ?? []) {
      append(child, depth + 1, [...path, child.label!]);
    }
  }
  append(group, level, parentPath);
  return sections;
}

function fieldLabel(key: string, compact: boolean): string {
  const words = key.replace(/([a-z\d])([A-Z])/g, '$1 $2').replace(/[_.-]+/g, ' ');
  if (!compact) return words.charAt(0).toUpperCase() + words.slice(1).toLowerCase();
  const spaced = words.replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2').trim();
  if (!spaced) return key.trim() || 'Empty key';

  return spaced.split(/\s+/).map((word, index) => {
    if (/^[A-Z\d]{2,}$/.test(word)) return word;
    if (index === 0) return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
    return word.toLowerCase();
  }).join(' ');
}

function readValue(source: string, compact: boolean, label?: string): BusinessValue {
  const errors: ParseError[] = [];
  const root = parseTree(source, errors, { disallowComments: true });
  return root && !errors.length
    ? businessValue(source, root, compact, label)
    : { label, text: 'Value unavailable', kind: 'unavailable' };
}

@Component({
  selector: 'workflow-task-values',
  standalone: true,
  imports: [NgTemplateOutlet],
  host: { '[class.input-presentation]': 'inputs() !== undefined' },
  templateUrl: './task-values.html',
  styleUrl: './task-values.scss',
})
export class WorkflowTaskValuesComponent {
  readonly inputs = input<Readonly<Record<string, TaskInput>>>();
  readonly result = input<string>();
  readonly nested = input<BusinessValue[]>();
  protected readonly inputFields = computed(() => groupFields(
    Object.entries(this.inputs() ?? {}).map(([key, input]) => readValue(input.valueJson, true, fieldLabel(key, true))),
  ));
  protected readonly inputGroups = computed(() => this.inputFields().groups.map(group => ({
    group,
    sections: group.fields ? [] : inputSections(group, 3),
    columns: (group.fields?.groups ?? []).map(child => ({
      table: !!child.table,
      sections: inputSections(child, 4),
    })),
  })));
  protected readonly resultValues = computed(() => {
    const nested = this.nested();
    if (nested) return nested;
    const result = this.result();
    if (result === undefined) return [];
    const value = readValue(result, false);
    return value.children ?? [value];
  });
}
