import type { WorkflowTaskFormValueKind } from '@workflow-engine/ui';

const numberToken = /^(-?)(0|[1-9]\d*)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/u;

/** Keep the source spelling for transport and compare by mathematical value. */
export class FormNumber {
  private constructor(
    readonly text: string,
    private readonly sign: number,
    private readonly digits: string,
    private readonly exponent: bigint,
  ) {}

  static parse(text: string): FormNumber | undefined {
    if (text.length > 32768) return undefined;
    const match = numberToken.exec(text);
    if (!match || match[0] !== text) return undefined;
    const [, negative, whole, fraction = '', exponent = '0'] = match;
    const coefficient = (whole + fraction).replace(/^0+/u, '');
    if (!coefficient) return new FormNumber(text, 0, '', 0n);
    const digits = coefficient.replace(/0+$/u, '');
    const power = BigInt(exponent) + BigInt(coefficient.length - digits.length - fraction.length);
    return new FormNumber(text, negative ? -1 : 1, digits, power);
  }

  get integer(): boolean { return this.exponent >= 0n; }

  compare(other: FormNumber): number {
    if (this.sign !== other.sign) return this.sign - other.sign;
    const leftOrder = BigInt(this.digits.length) + this.exponent;
    const rightOrder = BigInt(other.digits.length) + other.exponent;
    if (leftOrder !== rightOrder) return (leftOrder < rightOrder ? -1 : 1) * this.sign;
    const width = Math.max(this.digits.length, other.digits.length);
    const left = this.digits.padEnd(width, '0');
    const right = other.digits.padEnd(width, '0');
    return (left < right ? -1 : left > right ? 1 : 0) * this.sign;
  }
}

export type FormValue = null | string | boolean | FormNumber | FormValue[] | FormObject;
export interface FormObject { [key: string]: FormValue; }

export function isFormObject(value: FormValue | undefined): value is FormObject {
  return !!value && typeof value === 'object' && !Array.isArray(value) && !(value instanceof FormNumber);
}

function decodeLiteral(value: unknown): FormValue {
  if (typeof value === 'string') return value.startsWith('s') ? value.slice(1) : FormNumber.parse(value.slice(1))!;
  if (Array.isArray(value)) return value.map(decodeLiteral);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [key.slice(1), decodeLiteral(child)]));
  }
  return value as boolean | null;
}

export function readFormLiteral(text: string): FormValue {
  // Tag strings and number tokens before parsing so JSON.parse never rounds a contract value.
  const tagged = text.replace(/"(?:[^"\\]|\\.)*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/gu,
    token => token.startsWith('"') ? '"s' + token.slice(1) : '"n' + token + '"');
  return decodeLiteral(JSON.parse(tagged));
}

export function writeFormValue(value: FormValue): string {
  if (value instanceof FormNumber) return value.text;
  if (Array.isArray(value)) return '[' + value.map(writeFormValue).join(',') + ']';
  if (isFormObject(value)) {
    return '{' + Object.entries(value).map(([key, child]) => JSON.stringify(key) + ':' + writeFormValue(child)).join(',') + '}';
  }
  return JSON.stringify(value);
}

export function matchesKind(value: FormValue, kind: WorkflowTaskFormValueKind): boolean {
  switch (kind) {
    case 'number': return value instanceof FormNumber;
    case 'integer': return value instanceof FormNumber && value.integer;
    case 'object': return isFormObject(value);
    case 'array': return Array.isArray(value);
    case 'null': return value === null;
    default: return typeof value === kind;
  }
}

export function sameFormValue(left: FormValue, right: FormValue): boolean {
  if (left instanceof FormNumber) return right instanceof FormNumber && left.compare(right) === 0;
  if (Array.isArray(left)) {
    return Array.isArray(right) && left.length === right.length && left.every((value, i) => sameFormValue(value, right[i]));
  }
  if (isFormObject(left)) {
    return isFormObject(right) && Object.keys(left).length === Object.keys(right).length && Object.keys(left)
      .every(key => Object.hasOwn(right, key) && sameFormValue(left[key], right[key]));
  }
  return left === right;
}
