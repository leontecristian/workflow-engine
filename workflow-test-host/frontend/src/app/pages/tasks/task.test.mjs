import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CommandRecovery, runtimeType, component, routeAt, tick } from '../../../../unit/runtime-component.mjs';
import { provideWorkflowTaskEditors, WorkflowTaskComponent } from '@workflow-engine/ui/runtime';
import { WORKFLOW_RUNTIME_COMMANDS } from '@workflow-engine/ui';

const { WorkflowTaskComponent: TaskPage } = runtimeType('task');
const success = { replayed: false, status: 'COMPLETED', taskStatus: 'COMPLETED' };
const outcome = (key = 'COMPLETE', label = 'Complete', requiresComment = false) => ({ key, label, requiresComment });
const error = (httpStatus, message = 'Rejected') => Object.assign(new Error(message), { httpStatus });
const open = (settings = {}) => ({
  task: { status: 'OPEN', ownershipLabel: 'Assigned to you' },
  precondition: 'reviewed-version',
  taskType: 'REVIEW',
  canComplete: true,
  outcomes: [outcome()],
  ...settings,
});
const closed = () => ({ task: { status: 'COMPLETED' }, outcomes: [ { label: 'Complete' } ] });
const read = (getContract = () => open()) => ({
  taskContract: async (id, signal) => getContract(id, signal),
  taskSubmission: async () => null,
});
function setup(t, { api = read(), transport = {}, providers = [], id = '7', type = TaskPage } = {}) {
  const result = component(t, type, api, routeAt({ id }), undefined, [
    CommandRecovery, { provide: WORKFLOW_RUNTIME_COMMANDS, useValue: transport }, ...providers,
  ]);
  result.page.commands.setScope(result.page.sessionScope);
  return result;
}
const submit = (page, outcomeKey = 'COMPLETE', comment = '', resultJson) =>
  page.handlerInputs().submitDecision({ outcomeKey, comment, resultJson });
const visit = async (page, id) => {
  page.id = id;
  page.ngOnChanges({ id: { currentValue: id } });
  await tick();
};

test('only exact pinned task type selects the registered editor and both paths freeze exact decisions', async t => {
  class BookingHandler {}
  const calls = [];
  const transport = { async complete(envelope) { calls.push(envelope); throw error(422); } };
  const resultJson = '  {"amount":9007199254740993.00}  ';
  const comment = '  Exact comment\n';
  let defaultType;
  for (const providers of [[], [provideWorkflowTaskEditors({ BOOKING_REVIEW: BookingHandler })]]) {
    let taskType = 'booking_review';
    const api = read(() => open({ taskType, resultContract: { mode: 'OPTIONAL' } }));
    const { page } = setup(t, { api, transport, providers, type: WorkflowTaskComponent });
    assert.equal(page.handler(), null);
    await page.load();
    defaultType ??= page.handler();
    assert.equal(page.handler(), defaultType);
    assert.notEqual(page.handler(), BookingHandler);
    await submit(page, 'COMPLETE', comment, resultJson);
    taskType = 'BOOKING_REVIEW';
    await page.load();
    assert.equal(page.handler(), providers.length ? BookingHandler : defaultType);
    await submit(page, 'COMPLETE', comment, resultJson);
  }
  assert.equal(calls.length, 4);
  for (const envelope of calls) {
    assert.equal(envelope.body.taskId, '7');
    assert.equal(envelope.body.comment, comment);
    assert.equal(envelope.body.resultJson, resultJson);
    assert.equal(envelope.expectedToken, 'reviewed-version');
    assert.ok(!Object.hasOwn(envelope.body, 'instanceId'));
  }
});

test('destroyed task view drops a late snapshot', async t => {
  let resolve;
  const { page, injector } = setup(t, { api: read(() => new Promise(r => { resolve = r; })) });
  const pending = page.load();
  injector.destroy();
  resolve(open());
  await pending;
  assert.equal(page.detail(), null);
  assert.equal(page.contract(), null);
});

test('pending completion freezes one task-scoped request and blocks other actions', async t => {
  let resolve;
  const transport = { complete: () => new Promise(r => { resolve = r; }) };
  const api = read(() => open({ outcomes: [outcome('APPROVE'), outcome('REJECT', 'Reject', true)] }));
  const { page } = setup(t, { id: '9223372036854775807', api, transport });
  await page.load();
  const callback = page.handlerInputs().submitDecision;
  const decision = { outcomeKey: 'APPROVE', comment: 'Original' };
  const pending = callback(decision);
  decision.comment = 'Changed';
  assert.equal(page.handlerInputs().editable, false);
  assert.equal(page.handlerInputs().submitDecision, callback);
  await callback({ outcomeKey: 'REJECT', comment: 'Different' });
  const [original] = page.commands.buffer.entries;
  assert.equal(page.commands.buffer.entries.length, 1);
  assert.equal(original.body.taskId, '9223372036854775807');
  assert.equal(original.body.comment, 'Original');
  assert.equal(original.body.outcomeKey, 'APPROVE');
  assert.equal(original.expectedToken, 'reviewed-version');
  assert.ok(Object.isFrozen(original));
  resolve(success);
  await pending;
});

test('outcomes enforce their own comment and result requirements before dispatch', async t => {
  let calls = 0;
  const transport = { async complete() { calls++; throw error(422); } };
  const api = read(() => open({ outcomes: [outcome('REJECT', 'Reject', true), outcome('APPROVE', 'Approve')] }));
  const { page } = setup(t, { transport, api });
  await page.load();
  await submit(page, 'REJECT', ' \t\n');
  assert.match(page.error(), /comment is required/);
  await submit(page, 'unknown');
  assert.match(page.error(), /available outcome/);
  await submit(page, 'APPROVE', 'x'.repeat(2001));
  assert.match(page.error(), /2000 characters/);
  await submit(page, 'APPROVE', '', '{}');
  assert.match(page.error(), /accept a result/);
  assert.equal(calls, 0);
  await submit(page, 'REJECT', '  Exact reason\n');
  await submit(page, 'APPROVE');
  assert.equal(calls, 2);
  assert.equal(page.commands.buffer.entries[0].body.comment, '  Exact reason\n');
  assert.equal(page.commands.buffer.entries[1].body.comment, null);
});

test('editing uses the displayed ownership and version from the same snapshot', async t => {
  let finish;
  const { page } = setup(t, { api: read(() => new Promise(resolve => { finish = resolve; })) });
  page.api.task = () => assert.fail('A separate exact read could display a different version');
  const pending = page.load();
  assert.equal(page.handler(), null);
  const snapshot = open({ precondition: 'version-reviewed', task: { status: 'OPEN', ownershipLabel: 'Claimed by you' } });
  finish(snapshot);
  await pending;
  assert.equal(page.detail(), snapshot.task);
  assert.equal(page.handlerInputs().contract.precondition, 'version-reviewed');
});

test('task and session replacement invalidate old callbacks and destruction prevents new reads', async t => {
  const calls = [];
  let reads = 0;
  const sameContract = open();
  const { page, injector } = setup(t, {
    api: read(() => { reads++; return sameContract; }),
    transport: { async complete(envelope) { calls.push(envelope); throw error(422); } },
  });
  await page.load();
  const first = page.handlerInputs().submitDecision;
  await visit(page, '8');
  const second = page.handlerInputs().submitDecision;
  page.sessionScope = {};
  page.ngOnChanges({ sessionScope: { currentValue: page.sessionScope } });
  await tick();
  const current = page.handlerInputs().submitDecision;
  for (const stale of [first, second]) await stale({ outcomeKey: 'COMPLETE', comment: '' });
  assert.equal(calls.length, 0);
  await current({ outcomeKey: 'COMPLETE', comment: '' });
  assert.equal(calls[0].body.taskId, '8');
  injector.destroy();
  await current({ outcomeKey: 'COMPLETE', comment: '' });
  assert.equal(calls.length, 1);
  await visit(page, '9');
  assert.equal(reads, 3);
});

test('late conflict from an old task leaves the new snapshot and callback intact', async t => {
  let reject;
  const reads = [];
  const { page } = setup(t, {
    api: read(id => { reads.push(id); return open(); }),
    transport: { complete: () => new Promise((_, r) => { reject = r; }) },
  });
  await page.load();
  const pending = submit(page);
  await visit(page, '8');
  const current = page.handlerInputs().submitDecision;
  reject(error(409));
  await pending;
  assert.deepEqual(reads, ['7', '8']);
  assert.equal(page.handlerInputs().submitDecision, current);
  assert.equal(page.handlerInputs().editable, true);
  assert.equal(page.error(), '');
});

test('a returned task becomes editable when its command settles behind another selection', async t => {
  const pending = new Map();
  const { page } = setup(t, { transport: {
    complete: envelope => new Promise((_, reject) => { pending.set(envelope.body.taskId, reject); }),
  } });
  await page.load();
  const first = submit(page);
  await visit(page, '8');
  const second = submit(page);
  await visit(page, '7');
  const returned = page.handlerInputs();
  assert.equal(returned.editable, false);
  assert.equal(page.commands.buffer.command.body.taskId, '8');
  await submit(page);
  assert.equal(page.commands.buffer.entries.length, 2);
  pending.get('7')(error(422));
  await first;
  assert.equal(page.commands.buffer.command.body.taskId, '8');
  assert.equal(page.handlerInputs().submitDecision, returned.submitDecision);
  assert.equal(page.handlerInputs().editable, true);
  pending.get('8')(error(422));
  await second;
});

test('superseded completion and conflict refreshes cannot interrupt a later visit to the same task', async t => {
  for (const conflict of [false, true]) {
    const reads = [];
    const api = read((id, signal) => {
      if (id !== '7') return open();
      if (!reads.length) { reads.push({ signal }); return open(); }
      return new Promise(resolve => reads.push({ signal, resolve }));
    });
    const { page } = setup(t, { api, transport: { async complete() { if (conflict) throw error(409); return success; } } });
    await page.load();
    const pending = submit(page);
    await tick();
    await visit(page, '8');
    await visit(page, '7');
    assert.equal(reads.length, 3);
    assert.equal(reads[1].signal.aborted, true);
    reads[1].resolve(closed());
    await pending;
    assert.equal(reads[2].signal.aborted, false);
    assert.equal(page.notice(), '');
    assert.equal(page.error(), '');
    reads[2].resolve(open());
    await tick();
    assert.equal(page.detail().status, 'OPEN');
    assert.equal(page.handlerInputs().editable, true);
  }
});

test('read-only, closed and ineligible tasks show context without a handler', async t => {
  for (const [transport, getContract] of [[null, open], [{}, closed], [{}, () => open({ canComplete: false })]]) {
    const { page } = setup(t, { transport, api: read(getContract) });
    await page.load();
    assert.ok(page.detail());
    assert.equal(page.handler(), null);
    assert.equal(page.commands.buffer.entries.length, 0);
  }
});

test('success refreshes, shows recorded submission and keeps onward task navigation', async t => {
  let complete = false;
  const api = read(() => complete ? closed() : open());
  api.taskSubmission = async () => ({ outcomeLabel: 'Complete', actorLabel: 'You', resultJson: '{"amount":0}' });
  const { page } = setup(t, { api, transport: { async complete() {
    complete = true;
    return { ...success, nextTaskId: '8' };
  } } });
  await page.load();
  await submit(page);
  assert.equal(page.notice(), 'Task completed.');
  assert.equal(page.detail().status, 'COMPLETED');
  assert.equal(page.submission().resultJson, '{"amount":0}');
  assert.equal(page.nextTaskId(), '8');
  assert.equal(page.handler(), null);
});

test('fresh conflicts replace stale snapshots and collective completion preserves the operation message', async t => {
  for (const collectivePending of [undefined, true, false]) {
    let complete = false;
    let reads = 0;
    const { page } = setup(t, {
      api: read(() => { reads++; return complete ? closed() : open(); }),
      transport: { async complete() {
        if (collectivePending === undefined) throw error(409);
        complete = true;
        return { ...success, collectivePending };
      } },
    });
    await page.load();
    await submit(page);
    assert.equal(reads, 2);
    if (collectivePending === undefined) assert.match(page.error(), /refreshed/);
    else assert.match(page.notice(), collectivePending ? /waiting for other tasks/ : /resolved the review/);
  }
});

test('uncertainty blocks another decision and recovery reuses the same frozen request', async t => {
  const calls = [];
  let complete = false;
  const { page } = setup(t, {
    api: read(() => complete ? closed() : open()),
    transport: {
      async complete(envelope) {
        calls.push(envelope);
        if (calls.length === 1) throw new Error('network');
        complete = true;
        return { ...success, replayed: true };
      },
      async replayComplete(envelope) { calls.push(envelope); return { found: false, result: null }; },
    },
  });
  await page.load();
  await submit(page);
  assert.equal(page.handlerInputs().editable, false);
  await submit(page);
  assert.equal(calls.length, 1);
  await page.recover(true);
  assert.equal(page.recoveryMiss(), true);
  assert.equal(page.uncertain, true);
  await page.recover(false);
  assert.equal(calls.length, 3);
  assert.ok(calls.every(envelope => envelope === calls[0]));
  assert.equal(page.uncertain, false);
  assert.equal(page.detail().status, 'COMPLETED');
});

test('validation preserves the form callback and returns safe field errors with the exact draft', async t => {
  const fieldErrors = [{ path: '/approvedAmount', message: 'Amount rejected.' }];
  const calls = [];
  const api = read(() => open({ resultContract: { mode: 'REQUIRED' } }));
  const { page } = setup(t, { api, transport: { async complete(envelope) {
    calls.push(envelope);
    throw Object.assign(error(422, 'Check the result.'), { fieldErrors });
  } } });
  await page.load();
  const callback = page.handlerInputs().submitDecision;
  await submit(page);
  assert.match(page.error(), /required result/);
  await submit(page, 'COMPLETE', '', 'null');
  assert.equal(calls.length, 0);
  const draft = '{"approvedAmount":9007199254740993.00}';
  const rejected = await submit(page, 'COMPLETE', '', draft);
  assert.deepEqual(rejected.fieldErrors, fieldErrors);
  assert.equal(calls[0].body.resultJson, draft);
  assert.equal(page.handlerInputs().submitDecision, callback);
  page.api.taskContract = async () => open({ resultContract: { mode: 'OPTIONAL' } });
  await page.load();
  await submit(page, 'COMPLETE', '', ' \n ');
  assert.ok(!Object.hasOwn(calls[1].body, 'resultJson'));
});

test('definitive stale receipt check unlocks an open task after uncertain delivery', async t => {
  let calls = 0;
  let version = 'old-version';
  const api = read(() => open({ precondition: version }));
  const { page } = setup(t, { api, transport: {
    async complete() {
      calls++;
      if (calls === 1) throw new Error('network');
      throw Object.assign(error(409, 'Stale'), { code: 'STALE_VERSION' });
    },
    async replayComplete() { return { found: false, result: null }; },
  } });
  await page.load();
  await submit(page);
  version = 'new-version';
  await page.recover(true);
  await page.recover(false);
  assert.equal(page.uncertain, false);
  assert.equal(page.handlerInputs().editable, true);
  assert.equal(page.handlerInputs().contract.precondition, 'new-version');
  assert.match(page.error(), /refreshed/);
  assert.equal(page.commands.buffer.entries.length, 1);
  assert.equal(page.commands.buffer.entries[0].expectedToken, 'old-version');
});

test('ownership respects available actions, refreshes after success and freezes an uncertain release', async t => {
  const calls = [];
  const api = read(() => open({ canComplete: false, canClaim: true, canRelease: false, canAdminRelease: true }));
  const { page } = setup(t, { api, transport: { async ownership(envelope) {
    calls.push(envelope);
    if (envelope.kind === 'admin-release') throw new Error('network');
    return { ...success, taskStatus: 'OPEN' };
  } } });
  await page.load();
  await page.ownership('release');
  assert.equal(calls.length, 0);
  await page.ownership('claim');
  assert.equal(page.notice(), 'Task ownership updated.');
  page.reason = '  review  ';
  await page.ownership('admin-release');
  assert.equal(calls[1].body.reason, '  review  ');
  await page.ownership('claim');
  assert.equal(calls.length, 2);
});
