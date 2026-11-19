import { Component, computed, input } from '@angular/core';
import type { TaskInput } from '@workflow-engine/ui';
import { parseTree, type Node } from 'jsonc-parser';

interface BusinessValue {
  label?: string;
  text?: string;
  children?: BusinessValue[];
}

/** Read numeric values from their source tokens to avoid a lossy JavaScript number conversion. */
function businessValue(source: string, node: Node, label?: string): BusinessValue {
  if (node.type === 'object' || node.type === 'array') {
    const children = (node.children ?? []).map((child, index) => {
      if (node.type === 'array') return businessValue(source, child, String(index + 1));
      const [key, value] = child.children!;
      return businessValue(source, value, fieldLabel(key.value));
    });
    return children.length ? { label, children } : { label, text: node.type === 'object' ? 'Empty group' : 'Empty list' };
  }
  const text = node.type === 'string' ? node.value === '' ? 'Empty text' : node.value
    : node.type === 'null' ? 'No value'
    : node.type === 'boolean' ? node.value ? 'Yes' : 'No'
    : source.slice(node.offset, node.offset + node.length);
  return { label, text };
}

function fieldLabel(key: string): string {
  const words = key.replace(/([a-z\d])([A-Z])/g, '$1 $2').replace(/[_.-]+/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1).toLowerCase();
}

function readValue(source: string, label?: string): BusinessValue {
  const root = parseTree(source);
  return root ? businessValue(source, root, label) : { label, text: 'Value unavailable' };
}

@Component({
  selector: 'workflow-task-values',
  standalone: true,
  template: `@for(value of values(); track $index){
    <div class="business-value">
      @if(value.label){<div class="business-label">{{value.label}}</div>}
      @if(value.children; as children){<workflow-task-values [nested]="children" />}
      @else{<div class="business-text">{{value.text}}</div>}
    </div>
  }`,
  styles: [
    ':host{display:grid;gap:10px}.business-label{font-weight:550}.business-text{white-space:pre-wrap;overflow-wrap:anywhere}',
    'workflow-task-values{margin:.4rem 0 0 .75rem;padding-left:.75rem;border-left:1px solid var(--mat-sys-outline-variant)}',
  ],
})
export class WorkflowTaskValuesComponent {
  readonly inputs = input<Readonly<Record<string, TaskInput>>>();
  readonly result = input<string>();
  readonly nested = input<BusinessValue[]>();
  readonly values = computed(() => {
    const nested = this.nested();
    if (nested) return nested;
    const result = this.result();
    if (result !== undefined) {
      const value = readValue(result);
      return value.children ?? [value];
    }
    return Object.entries(this.inputs() ?? {}).map(([key, input]) => readValue(input.valueJson, fieldLabel(key)));
  });
}
