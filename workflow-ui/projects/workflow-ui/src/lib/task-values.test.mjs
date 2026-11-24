import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { ɵSIGNAL } from '@angular/core';
import { component, loadTs } from '../../../../unit/component-loader.mjs';

const { WorkflowTaskValuesComponent } = loadTs(new URL('../../runtime/src/lib/task-values.ts', import.meta.url));
const { bookingExample, businessContextInputs, orderExample } = loadTs(new URL(
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

test('the raw F038 fixture maps reordered properties and preserves source formatting', t => {
  const spec = readFileSync(new URL(
    '../../../../../docs/features/F038-record-tables-and-path-headings/spec.md', import.meta.url,
  ), 'utf8');
  assert.equal(orderExample, spec.match(/```json\s+([\s\S]+?)\s+```/)[1].replaceAll('\r\n', '\n'));

  const { page } = component(t, WorkflowTaskValuesComponent);
  setInput(page.inputs, businessContextInputs('records'));
  const { columns } = page.inputGroups()[0];
  const table = columns[0].sections[0].value.table;
  assert.deepEqual(Array.from(table.columns, column => column.label), ['Sku', 'Quantity', 'Unit price', 'Description']);
  assert.deepEqual(Array.from(table.columns, column => column.numeric), [false, true, true, false]);
  assert.deepEqual(Array.from(table.rows, row => Array.from(row, cell => cell.text)), [
    ['A-100', '2', '300.25', 'Annual licence'],
    ['B-220', '1', '649', 'Empty text'],
  ]);
  assert.equal(table.rows[1][3].kind, 'empty-text');
  assert.deepEqual(Array.from(columns, column => column.table), [true, false, false, false]);

  setInput(page.inputs, businessContextInputs('records-root'));
  assert.equal(page.inputGroups()[0].sections[0].value.table.rows[0][2].text, '300.25');
  setInput(page.inputs, businessContextInputs('records-deep'));
  const [deepColumn] = page.inputGroups()[0].columns;
  assert.equal(deepColumn.table, false);
  assert.equal(deepColumn.sections[1].value.table.rows[1][2].text, '649');
});

test('record table eligibility uses distinct decoded keys and scalar syntax nodes', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  const cases = {
    oneKey: ['[{"a":1},{"a":null}]', true],
    sixKeys: ['[{"a":1,"b":2,"c":3,"d":4,"e":5,"f":6},{"f":1,"e":2,"d":3,"c":4,"b":5,"a":6}]', true],
    sevenKeys: ['[{"a":1,"b":2,"c":3,"d":4,"e":5,"f":6,"g":7},{"a":1,"b":2,"c":3,"d":4,"e":5,"f":6,"g":7}]', false],
    emptyRecords: ['[{},{}]', false],
    emptyArray: ['[]', false],
    oneRecord: ['[{"a":1}]', false],
    omittedKey: ['[{"a":1,"b":null},{"a":2}]', false],
    changedKey: ['[{"a":1},{"b":2}]', false],
    duplicate: [String.raw`[{"a":1,"\u0061":2},{"a":3,"b":4}]`, false],
    laterDuplicate: ['[{"a":1,"b":2},{"a":3,"a":4}]', false],
    nestedObject: ['[{"a":1},{"a":{}}]', false],
    nestedArray: ['[{"a":1},{"a":[]}]', false],
    nonRecord: ['[{"a":1},null]', false],
    collision: [String.raw`[{"a_b":1,"a-b":2},{"a-b":3,"\u0061_b":4}]`, true],
  };
  setInput(page.inputs, Object.fromEntries(Object.entries(cases).map(([key, [valueJson]]) => [
    key, { type: 'JSON', valueJson },
  ])));
  const { groups } = page.inputFields();
  for (const [index, [key, [, eligible]]] of Object.entries(cases).entries()) {
    assert.equal(!!groups[index].table, eligible, key);
  }
  const collision = groups.at(-1).table;
  assert.deepEqual(Array.from(collision.columns, column => column.label), ['A b', 'A b']);
  assert.deepEqual(Array.from(collision.rows[1], cell => cell.text), ['4', '3']);
});

test('record table alignment and value display distinguish null, zero and literal text', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  setInput(page.inputs, { records: { type: 'JSON', valueJson: String.raw`[
    {"quantity":0,"empty":null,"mixed":1,"note":"No value","exact":9007199254740993.0100,"other":false},
    {"quantity":null,"empty":null,"mixed":"2","note":"first\nsecond","exact":1.2300E+05,"other":""}
  ]` } });
  const { table } = page.inputFields().groups[0];
  assert.deepEqual(Array.from(table.columns, column => column.numeric), [true, false, false, false, true, false]);
  assert.deepEqual(Array.from(table.rows[0], cell => cell.text), ['0', 'No value', '1', 'No value', '9007199254740993.01', 'No']);
  assert.deepEqual(Array.from(table.rows[1], cell => cell.text), ['No value', 'No value', '2', 'first\nsecond', '1.2300E+05', 'Empty text']);
  assert.deepEqual(Array.from(table.rows[0], cell => cell.kind), [undefined, 'null', undefined, undefined, undefined, undefined]);
  assert.deepEqual(Array.from(table.rows[1], cell => cell.kind), ['null', 'null', undefined, undefined, undefined, 'empty-text']);
});

test('flat sections retain source order, per-object scalar rows and logical depth', t => {
  const { page } = component(t, WorkflowTaskValuesComponent);
  setInput(page.inputs, businessContextInputs('records'));
  const [, routing, contacts, shipments] = page.inputGroups()[0].columns;
  const describe = sections => Array.from(sections).flatMap(section => [
    [section.path.join(' › '), section.level, Array.from(section.value.fields?.rows ?? [], row => row.label)],
    ...Array.from(section.items ?? []).flatMap(describe),
  ]);
  assert.deepEqual(describe(routing.sections), [
    ['Routing', 4, ['Queue']],
    ['Escalation', 5, ['Level']],
    ['Escalation › Window', 6, ['Start', 'End']],
  ]);
  assert.deepEqual(describe(contacts.sections), [
    ['Contacts', 4, []],
    ['Item 1', 5, ['Name', 'Role']],
    ['Item 2', 5, ['Name']],
  ]);
  assert.deepEqual(describe(shipments.sections), [
    ['Shipments', 4, []],
    ['Item 1', 5, ['Carrier']],
    ['Item 1 › Dimensions', 6, ['Weight kg']],
    ['Item 2', 5, ['Carrier']],
    ['Item 2 › Dimensions', 6, ['Weight kg']],
  ]);

  setInput(page.inputs, { records: { type: 'JSON', valueJson: '[{},false,{"a":{"b":{"c":{"d":1}}}}]' } });
  const { items } = page.inputGroups()[0].sections[0];
  assert.equal(items.length, 3);
  assert.equal(items[0][0].value.kind, 'empty-object');
  assert.equal(items[1][0].value.text, 'No');
  assert.deepEqual(describe(items[2]), [
    ['Item 3', 4, []], ['Item 3 › A', 5, []], ['Item 3 › A › B', 6, []], ['Item 3 › A › B › C', 7, ['D']],
  ]);
});
