import { test } from 'node:test';
import assert from 'node:assert/strict';
import { runtimeType } from '../../../../unit/runtime-component.mjs';

const { DefaultTaskHandlerComponent } = runtimeType('default-task-handler');
const choice = value => ({ label: value, value: JSON.stringify(value) });
const field = (key, kind, extra = {}) => ({ key, label: key, kind, ...extra });
const object = fields => ({ kind: 'object', label: 'Result', fields });
const form = (mode, descriptor, rejection) => {
  const decisions = [];
  const editor = new DefaultTaskHandlerComponent();
  editor.contract = {
    taskType: 'SANDBOX_REVIEW',
    outcomes: [{ key: 'COMPLETE', label: 'Complete', requiresComment: false }],
    resultContract: { mode, form: descriptor },
  };
  editor.editable = true;
  editor.submitDecision = async decision => { decisions.push(decision); return rejection; };
  return { editor, decisions };
};
const child = (view, key) => view.children.find(field => field.field.key === key);
const canComplete = editor => editor.canSubmit(editor.contract.outcomes[0]);

test('Reject focuses Comment and blocks a whitespace-only submission', async () => {
  const { editor, decisions } = form('FORBIDDEN');
  const reject = { key: 'REJECT', label: 'Reject', requiresComment: true };
  editor.contract = { ...editor.contract, outcomes: [reject] };
  editor.comment = ' \t\n';
  let focuses = 0;
  const commentField = { focus() { focuses++; } };

  assert.equal(editor.canAttempt(reject), true);
  assert.equal(editor.canSubmit(reject), false);
  await editor.submit('REJECT', commentField);
  assert.equal(focuses, 1);
  assert.equal(editor.missingRejectComment, true);
  assert.deepEqual(decisions, []);

  editor.comment = '  Missing approval evidence.\n';
  assert.equal(editor.missingRejectComment, false);
  assert.equal(editor.canSubmit(reject), true);
  await editor.submit('REJECT', commentField);
  assert.equal(decisions.length, 1);
  assert.equal(decisions[0].outcomeKey, 'REJECT');
  assert.equal(decisions[0].comment, editor.comment);
});

test('amount review sends zero and an empty Note, preserves exact numbers and validates at the corresponding control', async () => {
  const { editor, decisions } = form('REQUIRED', object([
    field('approvedAmount', 'number', { required: true, limits: { minimum: '0' } }),
    field('note', 'string', { required: true, limits: { maxLength: '500' } }),
  ]));
  const amount = () => child(editor.activeView, 'approvedAmount');
  const note = () => child(editor.activeView, 'note');
  assert.equal(canComplete(editor), false);
  editor.writeText(amount(), '-0.0000000001');
  assert.match(amount().issue, /0 or more/);
  editor.writeText(amount(), '0');
  assert.equal(note().text, '');
  assert.equal(canComplete(editor), true);
  editor.comment = 'A separate comment';
  await editor.submit('COMPLETE');
  assert.equal(decisions[0].resultJson, '{"approvedAmount":0,"note":""}');
  assert.equal(decisions[0].comment, 'A separate comment');

  editor.writeText(note(), '😀'.repeat(501));
  assert.match(note().issue, /500 characters/);
  assert.equal(canComplete(editor), false);
  editor.writeText(note(), '  Kept exactly\n');
  editor.writeText(amount(), '9007199254740993.00');
  await editor.submit('COMPLETE');
  assert.equal(decisions[1].resultJson, '{"approvedAmount":9007199254740993.00,"note":"  Kept exactly\\n"}');
});

test('optional result distinguishes omission, empty group, explicit null, and false', async () => {
  const { editor, decisions } = form('OPTIONAL', object([
    field('note', 'choice', {
      nullable: true,
      choices: [choice('A')],
      rule: { kinds: ['string', 'null'], allowed: ['"A"', 'null'] },
    }),
    field('confirmed', 'boolean'),
    field('emptyGroup', 'object'),
  ]));
  await editor.submit('COMPLETE');
  assert.equal(Object.hasOwn(decisions[0], 'resultJson'), false);

  editor.toggleResult(true);
  await editor.submit('COMPLETE');
  assert.equal(decisions[1].resultJson, '{}');
  const note = child(editor.activeView, 'note');
  assert.equal(note.canBeNull, true);
  editor.setPresence(note, 'null');
  editor.choose(child(editor.activeView, 'confirmed'), 'false');
  editor.setPresence(child(editor.activeView, 'emptyGroup'), 'present');
  await editor.submit('COMPLETE');
  assert.equal(decisions[2].resultJson, '{"note":null,"confirmed":false,"emptyGroup":{}}');

  editor.toggleResult(false);
  await editor.submit('COMPLETE');
  assert.equal(Object.hasOwn(decisions[3], 'resultJson'), false);
});

test('list items retain separate conditional answers, alternate drafts, order and field-specific rejection', async () => {
  const branch = (kind, reference) => ({
    ...object([
      field('kind', 'fixed', { fixed: JSON.stringify(kind), required: true }),
      field(reference, 'string', { required: true, limits: { minLength: '1' } }),
      field('decision', 'choice', { required: true, choices: [choice('APPROVE'), choice('REJECT')] }),
      field('reason', 'string'),
    ]),
    dependencies: [{
      when: { requiredFields: ['decision'], fields: [{ key: 'decision', rule: { matches: '"REJECT"' } }] },
      then: { requiredFields: ['reason'], fields: [{ key: 'reason', rule: { limits: { minLength: '1' } } }] },
    }],
  });
  const { editor, decisions } = form('REQUIRED', object([
    field('items', 'array', {
      required: true, limits: { minItems: '1', maxItems: '2' },
      item: {
        kind: 'object', label: 'Item', selectorLabel: 'Review type', selectorKey: 'kind',
        alternatives: [{ label: 'Booking', field: branch('BOOKING', 'bookingId') },
          { label: 'Resource', field: branch('RESOURCE', 'resourceId') }],
      },
    }),
  ]), { fieldErrors: [
    { path: '/items/1/reason', message: 'Explain this rejection.' },
    { path: '/unknown', message: 'Review the overall result.' },
  ] });
  const list = () => child(editor.activeView, 'items');
  const item = index => list().children[index];
  const answer = (index, key) => child(item(index).branch, key);
  assert.match(list().issue, /at least 1 items/);
  editor.addItem(list());
  editor.addItem(list());
  assert.equal(list().atItemLimit, true);
  editor.chooseShape(item(0), 0);
  editor.chooseShape(item(1), 1);
  editor.writeText(answer(0, 'bookingId'), 'B-1');
  editor.writeText(answer(1, 'resourceId'), 'R-1');
  editor.choose(answer(0, 'decision'), '"REJECT"');
  editor.choose(answer(1, 'decision'), '"APPROVE"');
  assert.equal(answer(0, 'reason').required, true);
  assert.match(answer(0, 'reason').issue, /at least 1 characters/);
  assert.equal(answer(1, 'reason').included, false);

  editor.writeText(answer(0, 'reason'), 'Room unavailable');
  editor.choose(answer(0, 'decision'), '"APPROVE"');
  assert.equal(answer(0, 'reason').required, false);
  assert.equal(answer(0, 'reason').text, 'Room unavailable');
  assert.equal(answer(0, 'reason').included, true);

  editor.chooseShape(item(0), 1);
  editor.writeText(answer(0, 'resourceId'), 'R-2');
  editor.choose(answer(0, 'decision'), '"APPROVE"');
  editor.chooseShape(item(0), 0);
  assert.equal(answer(0, 'bookingId').text, 'B-1');
  assert.equal(answer(0, 'reason').text, 'Room unavailable');
  editor.chooseShape(item(0), 1);
  assert.equal(answer(0, 'resourceId').text, 'R-2');
  editor.moveItem(list(), 0, 1);
  assert.equal(answer(0, 'resourceId').text, 'R-1');
  assert.equal(canComplete(editor), true);
  await editor.submit('COMPLETE');
  assert.equal(decisions[0].resultJson,
    '{"items":[{"kind":"RESOURCE","resourceId":"R-1","decision":"APPROVE"},{"kind":"RESOURCE","resourceId":"R-2","decision":"APPROVE"}]}');
  assert.ok(editor.fieldErrors(answer(1, 'reason')).includes('Explain this rejection.'));
  assert.ok(editor.formErrors.includes('Review the overall result.'));
  editor.setPresence(answer(1, 'reason'), 'present');
  editor.writeText(answer(1, 'reason'), 'Corrected');
  assert.equal(editor.fieldErrors(answer(1, 'reason')).length, 0);
  assert.equal(answer(1, 'resourceId').text, 'R-2');
});

test('a condition can add a typed nested group with its own fixed and required fields', async () => {
  const descriptor = {
    ...object([
      field('decision', 'boolean', { required: true }),
      field('payload', 'string', { inferred: true, nullable: true }),
    ]),
    dependencies: [{
      when: { requiredFields: ['decision'], fields: [{ key: 'decision', rule: { matches: 'true' } }] },
      then: {
        requiredFields: ['payload'],
        fields: [{ key: 'payload', rule: {
          kinds: ['object'], closed: true, requiredFields: ['code', 'amount'], fields: [
            { key: 'code', rule: { kinds: ['string'], matches: '"AGREED"' } },
            { key: 'amount', rule: { all: [{ label: 'Approved amount', help: 'Use the reviewed value', hint: '9007199254740993' },
              { kinds: ['integer'], limits: { minimum: '9007199254740993' } }] } },
          ],
        } }],
      },
    }],
  };
  const { editor, decisions } = form('REQUIRED', descriptor);
  const payload = () => child(editor.activeView, 'payload');
  assert.equal(canComplete(editor), false);
  editor.choose(child(editor.activeView, 'decision'), 'true');
  assert.equal(payload().kind, 'object');
  assert.equal(child(payload(), 'code').fixed, 'AGREED');
  assert.equal(child(payload(), 'amount').field.label, 'Approved amount');
  assert.equal(child(payload(), 'amount').field.help, 'Use the reviewed value');
  assert.equal(child(payload(), 'amount').field.hint, '9007199254740993');
  editor.writeText(child(payload(), 'amount'), '9007199254740992.0');
  assert.match(child(payload(), 'amount').issue, /9007199254740993/);
  editor.writeText(child(payload(), 'amount'), '900719925474099300e-2');
  assert.equal(canComplete(editor), true);
  await editor.submit('COMPLETE');
  assert.equal(decisions[0].resultJson, '{"decision":true,"payload":{"code":"AGREED","amount":900719925474099300e-2}}');
});

test('conditional alternatives keep sibling requirements and limits in the selected shape', async () => {
  const branch = (kind, reference) => ({
    kinds: ['object'],
    closed: true,
    requiredFields: ['kind', reference],
    fields: [
      { key: 'kind', rule: { kinds: ['string'], matches: JSON.stringify(kind) } },
      { key: reference, rule: { kinds: ['string'], limits: { minLength: '1' } } },
      { key: 'reason', rule: { kinds: ['string'] } },
    ],
  });
  const descriptor = {
    ...object([
      field('needsReview', 'boolean', { required: true }),
      field('payload', 'string', { inferred: true, nullable: true }),
    ]),
    dependencies: [{
      when: { requiredFields: ['needsReview'], fields: [{ key: 'needsReview', rule: { matches: 'true' } }] },
      then: {
        requiredFields: ['payload'],
        fields: [{ key: 'payload', rule: { all: [{
          kinds: ['object'],
          requiredFields: ['reason'],
          fields: [{ key: 'reason', rule: { limits: { minLength: '3' } } }],
          alternatives: [branch('BOOKING', 'bookingRef'), branch('RESOURCE', 'resourceRef')],
        }] } }],
      },
    }],
  };
  const { editor, decisions } = form('REQUIRED', descriptor);
  const payload = () => child(editor.activeView, 'payload');
  const answer = key => child(payload().branch, key);
  editor.choose(child(editor.activeView, 'needsReview'), 'true');
  editor.chooseShape(payload(), 0);
  editor.writeText(answer('bookingRef'), 'B-1');
  assert.equal(answer('reason').required, true);
  assert.match(answer('reason').issue, /at least 3 characters/);
  assert.equal(canComplete(editor), false);
  editor.writeText(answer('reason'), 'Checked');
  assert.equal(canComplete(editor), true);
  await editor.submit('COMPLETE');
  assert.deepEqual(JSON.parse(decisions[0].resultJson), {
    needsReview: true, payload: { kind: 'BOOKING', bookingRef: 'B-1', reason: 'Checked' },
  });
});

test('missing form information blocks submission and replacing the task discards its draft', async () => {
  const { editor, decisions } = form('REQUIRED');
  assert.equal(editor.resultUnavailable, true);
  await editor.submit('COMPLETE');
  assert.equal(decisions.length, 0);
  const descriptor = object([field('note', 'string', { required: true })]);
  const contract = () => ({ ...editor.contract, resultContract: { mode: 'REQUIRED', form: descriptor } });
  editor.contract = contract();
  editor.writeText(child(editor.activeView, 'note'), 'old task answer');
  editor.comment = 'old task comment';
  editor.contract = contract();
  assert.equal(child(editor.activeView, 'note').text, '');
  assert.equal(editor.comment, '');
});

test('calendar dates, offset timestamps, UUIDs and primitive lists keep the entered representation', async () => {
  const { editor, decisions } = form('REQUIRED', object([
    field('date', 'string', { required: true, format: 'date' }),
    field('stamp', 'string', { required: true, format: 'date-time' }),
    field('account', 'string', { required: true, format: 'uuid' }),
    field('replies', 'array', { required: true, item: { label: 'Reply', kind: 'boolean' } }),
  ]));
  const control = key => child(editor.activeView, key);
  const stamp = '2026-02-28T14:30:00.123456789+02:00';
  const uuid = '550E8400-E29B-41D4-A716-446655440000';
  editor.writeText(control('date'), '2026-02-29');
  assert.match(control('date').issue, /YYYY-MM-DD/);
  editor.writeText(control('date'), '2028-02-29');
  editor.writeText(control('stamp'), stamp.slice(0, -6));
  assert.match(control('stamp').issue, /offset/);
  editor.writeText(control('stamp'), stamp);
  editor.writeText(control('account'), uuid);
  assert.equal(canComplete(editor), true);
  await editor.submit('COMPLETE');
  assert.equal(decisions[0].resultJson, `{"date":"2028-02-29","stamp":"${stamp}","account":"${uuid}","replies":[]}`);

  editor.addItem(control('replies'));
  assert.equal(canComplete(editor), false);
  editor.choose(control('replies').children[0], 'false');
  await editor.submit('COMPLETE');
  assert.equal(decisions[1].resultJson, `{"date":"2028-02-29","stamp":"${stamp}","account":"${uuid}","replies":[false]}`);
});
