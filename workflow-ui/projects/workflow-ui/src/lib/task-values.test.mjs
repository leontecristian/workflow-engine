import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { ɵSIGNAL } from '@angular/core';
import { component, loadTs } from '../../../../unit/component-loader.mjs';

const { WorkflowTaskValuesComponent } = loadTs(new URL('../../runtime/src/lib/task-values.ts', import.meta.url));
const { bookingExample, businessContextInputs } = loadTs(new URL(
  '../../../../verification/ngmodule-host/src/business-context-fixture.ts', import.meta.url,
));

function setInput(input, value) {
  const node = input[ɵSIGNAL];
  node.applyValueToInputSignal(node, value);
}

test('booking fixture matches the specification and input display keeps its frozen source tokens', t => {
  const spec = readFileSync(new URL(
    '../../../../../docs/features/F037-task-business-context-renderer/spec.md', import.meta.url,
  ), 'utf8');
  assert.equal(bookingExample, spec.match(/```json\s+([\s\S]+?)\s+```/)[1].replaceAll('\r\n', '\n'));

  const { page } = component(t, WorkflowTaskValuesComponent);
  const inputs = businessContextInputs('booking');
  const original = inputs.bookingDetailsAtSubmission.valueJson;
  assert.match(original, /"durationHours": 8\.000000000000000/);
  setInput(page.inputs, inputs);

  const { rows, groups } = page.inputFields();
  assert.equal(rows.length, 1);
  assert.equal(rows[0].text, '21685');
  assert.equal(groups.length, 1);
  assert.equal(groups[0].fields.groups[2].fields.rows[0].text, '8');
  assert.equal(inputs.bookingDetailsAtSubmission.valueJson, original);
});

test('input normalization preserves exact significant digits, exponents, literal strings and unreadable state', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  setInput(page.inputs, businessContextInputs('generic'));

  const { rows, groups } = page.inputFields();
  assert.equal(rows.map(row => row.text).join('|'), '008.00|Value unavailable|No');
  assert.equal(groups.map(group => group.label).join('|'), 'Calibration|Limits');
  assert.equal(groups[0].fields.groups[0].fields.rows.map(row => row.text).join('|'),
    '9007199254740993.01|1.2300E+05|0|No|Empty text|No value');
  assert.equal(groups[1].fields.rows[0].text, '5');
});

test('recorded result still uses its exact numeric token and nested values', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  setInput(page.result, '{"approvedAmount":12.00,"evidence":{"verified":true}}');

  const [amount, evidence] = page.resultValues();
  assert.equal(amount.text, '12.00');
  assert.equal(evidence.children[0].text, 'Yes');
});

test('Business context preserves acronym labels in standalone and nested fields', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  const examples = [
    ['customerVATId', 'Customer VAT id'],
    ['IBAN', 'IBAN'],
    ['bookingID', 'Booking ID'],
    ['engagementManager', 'Engagement manager'],
    ['durationHours', 'Duration hours'],
    ['booking_ref', 'Booking ref'],
    ['', 'Empty key'],
    ['_-', '_-'],
  ];
  const details = Object.fromEntries(examples.map(([key]) => [key, 'sample']));
  setInput(page.inputs, {
    ...Object.fromEntries(examples.map(([key]) => [key, { type: 'STRING', valueJson: '"sample"' }])),
    details: { type: 'JSON', valueJson: JSON.stringify(details) },
  });

  const expectedLabels = examples.map(([, label]) => label);
  const { rows, groups } = page.inputFields();
  assert.deepEqual(Array.from(rows, row => row.label), expectedLabels);
  assert.deepEqual(Array.from(groups[0].fields.rows, row => row.label), expectedLabels);
});

test('the shared result formatter retains existing labels for acronym keys', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  setInput(page.result, '{"customerVATId":"sample","IBAN":"sample","details":{"bookingID":"sample"}}');

  const [vat, iban, details] = page.resultValues();
  assert.equal(vat.label, 'Customer vatid');
  assert.equal(iban.label, 'Iban');
  assert.equal(details.children[0].label, 'Booking id');
});

test('input placeholders have a kind distinct from literal text with the same content', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  const pairs = [
    ['No value', 'null', 'null'],
    ['Empty text', '""', 'empty-text'],
    ['Value unavailable', '{', 'unavailable'],
  ];
  setInput(page.inputs, Object.fromEntries(pairs.flatMap(([text, source], index) => [
    [`literal${index}`, { type: 'STRING', valueJson: JSON.stringify(text) }],
    [`placeholder${index}`, { type: 'JSON', valueJson: source }],
  ])));

  const { rows } = page.inputFields();
  for (const [index, [text, , kind]] of pairs.entries()) {
    const [literal, placeholder] = rows.slice(index * 2, index * 2 + 2);
    assert.equal(literal.text, text);
    assert.equal(literal.kind, undefined);
    assert.equal(placeholder.text, text);
    assert.equal(placeholder.kind, kind);
  }
});
