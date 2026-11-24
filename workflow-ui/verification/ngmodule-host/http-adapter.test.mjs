import '@angular/compiler';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PLATFORM_ID, createEnvironmentInjector, ɵINJECTOR_SCOPE } from '@angular/core';
import {
  CommandRecovery, freezeEnvelope, WORKFLOW_ADMINISTRATION_API, WORKFLOW_VIEWER_READS,
  WORKFLOW_RUNTIME_READS, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_CANCELLATION, WORKFLOW_TASK_REASSIGNMENT,
} from '@workflow-engine/ui';
import {
  provideWorkflowAdministrationHttp, provideWorkflowViewerHttp, provideWorkflowRuntimeReadsHttp,
  provideWorkflowRuntimeCommandsHttp, provideWorkflowCancellationHttp, provideWorkflowTaskReassignmentHttp,
} from '@workflow-engine/ui/http';

const id = '9007199254740993';
const token = '9007199254740999';
const cursor = 'opaque /+?&%2F=';
const raw = ' { "large":9007199254740993, "decimal":12500.00, "nested":[100e-2,null] } ';
const commandId = '12345678-1234-1234-1234-123456789abc';
const precondition = 'AbC_opaque-001';
const adminMount = '/fixture/administration';
const readMount = '/fixture/reads';
const commandMount = '/fixture/commands';

function host(t, ...adapters) {
  const injector = createEnvironmentInjector([
    // Set the application scope that bootstrap normally installs, without a DOM or network.
    { provide: ɵINJECTOR_SCOPE, useValue: 'root' },
    { provide: PLATFORM_ID, useValue: 'browser' },
    provideHttpClient(withNoXsrfProtection(), withInterceptors([
      (request, next) => next(request.clone({ setHeaders: {
        'X-Fixture-Session': 'host-interceptor',
        ...(request.method !== 'GET' && { 'X-Fixture-CSRF': 'host-token' }),
      } })),
    ])),
    provideHttpClientTesting(),
    ...adapters,
  ], null);
  const backend = injector.get(HttpTestingController);
  t.after(() => {
    try { backend.verify(); }
    finally { injector.destroy(); }
  });
  function request(method, path, query = {}) {
    const request = backend.expectOne(candidate =>
      new URL(candidate.urlWithParams, 'http://fixture.invalid').pathname === path, path);
    assert.equal(request.request.method, method);
    assert.deepEqual(Object.fromEntries(new URLSearchParams(request.request.urlWithParams.split('?')[1])), query);
    assert.equal(request.request.headers.get('X-Fixture-Session'), 'host-interceptor');
    assert.equal(request.request.headers.get('X-Fixture-CSRF'), method === 'GET' ? null : 'host-token');
    return request;
  }
  return { injector, backend, request };
}

const signal = () => new AbortController().signal;
const bodyOf = request => JSON.parse(request.request.serializeBody());
const deliver = (request, body) => request.flush(JSON.stringify(body));
const fault = (request, status, body) => request.flush(body, { status, statusText: 'Fixture response' });

test('A1: administration registers independently and composes with a custom viewer', async t => {
  const customViewer = { history: () => Promise.resolve({ items: [], nextCursor: null }) };
  const { injector, backend, request } = host(t,
    provideWorkflowAdministrationHttp(adminMount + '/'),
    { provide: WORKFLOW_VIEWER_READS, useValue: customViewer },
  );
  const api = injector.get(WORKFLOW_ADMINISTRATION_API);
  assert.equal(injector.get(WORKFLOW_VIEWER_READS), customViewer);
  for (const capability of [
    WORKFLOW_RUNTIME_READS, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_CANCELLATION, WORKFLOW_TASK_REASSIGNMENT,
  ]) assert.equal(injector.get(capability, null), null);
  backend.expectNone(() => true, 'registering and resolving providers is inert');
  const pending = api.listDefinitions(undefined, 2, signal());
  const result = { items: [{ id, name: 'Fixture definition' }], nextAfterId: id };
  deliver(request('GET', adminMount + '/definitions', { limit: '2', order: 'DESC' }), result);
  assert.deepEqual(await pending, result);
});

test('A2: administration keeps filters, lock tokens, exact documents and lifecycle wire shapes', async t => {
  const { injector, request } = host(t, provideWorkflowAdministrationHttp(adminMount));
  const api = injector.get(WORKFLOW_ADMINISTRATION_API);
  const abort = signal();
  const taskType = { name: 'Review', description: null, selectable: true };
  const newType = { ...taskType, code: 'FIXTURE' };
  const metadata = { name: 'Family', description: null };
  const key = 'task /+?&%2F=';
  const family = adminMount + '/definitions/' + id;
  const version = adminMount + '/versions/' + id;
  const at = '2026-11-20T00:00:00Z';
  const changed = { before: null, after: {
    id, lockVersion: token, definitionId: '1', key, version: 1, schemaVersion: 1,
    status: 'DRAFT', contentRevision: '1', contentHash: 'fixture', canonicalizationVersion: '1',
    createdAt: at, createdBy: 'admin', updatedAt: at, updatedBy: 'admin',
    publishedAt: null, publishedBy: null, retiredAt: null, retiredBy: null, validation: null,
  }, changed: true };
  const taskTypeChange = { ...changed, after: { ...newType, id, lockVersion: token } };
  const definitionChange = { ...changed, after: { ...metadata, id, key, lockVersion: token, createdAt: at, createdBy: 'admin' } };
  const report = { valid: false, diagnostics: [{ code: 'INVALID', pointer: '/nodes' }], detailsOmitted: false };
  const operations = [
    ['GET', adminMount + '/task-types', () => api.listTaskTypes(key, id, 5, abort),
      { query: { filter: key, afterId: id, limit: '5' } }],
    ['POST', adminMount + '/task-types', () => api.createTaskType(newType, abort), { body: newType, response: taskTypeChange }],
    ['PUT', adminMount + '/task-types/' + id, () => api.updateTaskType(id, taskType, token, abort),
      { body: taskType, lock: token, response: taskTypeChange }],
    ['GET', adminMount + '/definitions', () => api.listDefinitions(id, 2, abort),
      { query: { limit: '2', order: 'DESC', afterId: id }, response: { items: [], nextAfterId: null } }],
    ['GET', family, () => api.definition(id, abort)],
    ['PUT', family, () => api.saveMetadata(id, metadata.name, metadata.description, token, abort),
      { body: metadata, lock: token, response: definitionChange }],
    ['GET', family + '/versions', () => api.listVersions(id, 'DRAFT', id, 5, abort),
      { query: { status: 'DRAFT', afterId: id, limit: '5' } }],
    ['GET', version, () => api.version(id, abort)],
    ['GET', adminMount + '/versions/lookup', () => api.lookupVersion(key, '17', abort),
      { query: { key, version: '17' } }],
    ['POST', adminMount + '/versions/import', () => api.importDraft(raw, abort), { rawBody: raw }],
    ['PUT', version + '/document', () => api.saveDocument(id, raw, token, abort), { rawBody: raw, lock: token }],
    ['POST', version + '/validate', () => api.checkVersion(id, 'validate', token, abort),
      { lock: token, response: { change: changed, report, published: false } }],
    ['POST', version + '/publish', () => api.checkVersion(id, 'publish', token, abort),
      { lock: token, response: { change: changed, report: { ...report, valid: true, diagnostics: [] }, published: true } }],
    ['POST', version + '/retire', () => api.retireVersion(id, token, abort), { lock: token }],
    ['POST', version + '/drafts', () => api.createDraftFromVersion(id, token, abort), { body: {}, lock: token }],
    ['DELETE', version, () => api.deleteDraft(id, token, abort),
      { body: null, lock: token, response: { before: changed.after, operation: 'DELETE_DRAFT' } }],
  ];
  for (const [method, path, invoke, expected = {}] of operations) {
    const pending = invoke();
    const call = request(method, path, expected.query);
    assert.equal(call.request.headers.get('X-Expected-Lock-Version'), expected.lock ?? null);
    if (expected.rawBody) {
      assert.equal(call.request.headers.get('Content-Type'), 'application/json');
      assert.equal(call.request.serializeBody(), expected.rawBody);
    } else if ('body' in expected) {
      assert.deepEqual(bodyOf(call), expected.body);
    } else {
      assert.equal(call.request.body, null);
    }
    const response = expected.response ?? (method === 'GET' ? {} : changed);
    deliver(call, response);
    assert.deepEqual(await pending, response);
  }
  const exported = api.exportVersion(id, abort);
  request('GET', version + '/export').flush(raw);
  assert.equal(await exported, raw);

  const stale = api.saveDocument(id, raw, token, abort);
  const envelope = { code: 'STALE_VERSION', diagnostics: [{ pointer: '/version' }], correlationId: 'fixture-stale' };
  fault(request('PUT', version + '/document'), 409, JSON.stringify(envelope));
  await assert.rejects(stale, { httpStatus: 409, ...envelope });
});

test('A3: standard reads keep route separation, exact values, form references and empty continuations', async t => {
  const { injector, request } = host(t,
    provideWorkflowViewerHttp('/fixture/viewer/'), provideWorkflowRuntimeReadsHttp(readMount));
  const viewer = injector.get(WORKFLOW_VIEWER_READS);
  const reads = injector.get(WORKFLOW_RUNTIME_READS);
  const abort = signal();
  const viewerInstance = '/fixture/viewer/instances/' + id;
  const instance = readMount + '/instances/' + id;
  const task = readMount + '/tasks/' + id;
  const activity = readMount + '/activities/' + id;
  const graph = { instance: { id }, nodes: [{ key: 'review', taskId: id }], edges: [] };
  const history = { items: [{ historySequence: token }], nextAfterSequence: token };
  const event = { summary: history.items[0], detailsJson: raw };
  const filters = { status: 'OPEN', taskName: '  Review 100% & legal  ', taskType: 'FIXTURE',
    assignmentKind: 'GROUP', assignmentRef: 'Finance & Legal', assigneeRef: 'reviewer + one', instanceId: id };
  const operations = [
    [viewerInstance, () => viewer.instance(id, abort), {}, { id }],
    [viewerInstance + '/graph', () => viewer.graph(id, abort), {}, graph],
    [viewerInstance + '/history', () => viewer.history(id, id, 20, abort), { afterSequence: id, limit: '20' }, history],
    [viewerInstance + '/history/' + token, () => viewer.historyEvent(id, token, abort), {}, event],
    [readMount + '/instances', () => reads.listInstances('ACTIVE', id, 20, abort),
      { status: 'ACTIVE', afterId: id, limit: '20' }, { items: [], nextAfterId: token }],
    [readMount + '/tasks/administration', () => reads.administrationTasks(filters, cursor, 20, abort),
      { ...filters, cursor, limit: '20' }, { items: [], nextCursor: cursor }],
    [readMount + '/tasks/mine', () => reads.assignedTasks(cursor, 20, abort),
      { cursor, limit: '20' }, { items: [{ id }], nextCursor: null }],
    [instance + '/tasks', () => reads.instanceTasks(id, cursor, 20, abort),
      { cursor, limit: '20' }, { items: [{ id }], nextCursor: null }],
    [task, () => reads.task(id, abort), {}, { id }],
    [task + '/previous-decisions', () => reads.previousDecisions(id, cursor, 20, abort),
      { cursor, limit: '20' }, { items: [{ comment: '  preserved comment  ' }], nextCursor: null }],
    [activity, () => reads.activity(id, abort), {}, { id }],
  ];
  for (const [path, invoke, query, response] of operations) {
    const pending = invoke();
    deliver(request('GET', path, query), response);
    assert.deepEqual(await pending, response);
  }

  const variableSource = `{"instanceId":"${id}","lockVersion":"${token}","items":[{"key":"data","type":"JSON","initialized":true,"value":${raw}}],"nextAfterKey":null}`;
  const variables = reads.variables(id, 'scope.a & b', token, abort);
  request('GET', instance + '/variables', { afterKey: 'scope.a & b', expectedLockVersion: token, limit: '50' })
    .flush(variableSource);
  const variablePage = await variables;
  assert.equal(variablePage.exactJson, variableSource);
  assert.equal(variablePage.lockVersion, token);

  const contract = reads.taskContract(id, abort);
  request('GET', task + '/contract').flush(`{
    "task":{"status":"OPEN"},"precondition":"${precondition}",
    "inputs":{"payload":{"type":"JSON","value":${raw}},"note":{"type":"STRING","value":null}},
    "resultContract":{"mode":"OPTIONAL","form":{"kind":"object","label":"Result","fields":[
      {"formId":4,"label":"Amount","kind":"number","help":{"textId":1,"text":"Shared help"}},
      4, {"label":"Other amount","kind":"number","help":{"textId":1}}
    ]}}
  }`);
  const hydrated = await contract;
  assert.equal(Object.hasOwn(hydrated.task, 'description'), false);
  assert.equal(hydrated.precondition, precondition);
  assert.deepEqual(hydrated.inputs.payload, { type: 'JSON', valueJson: raw.trim() });
  assert.equal(hydrated.inputs.note.valueJson, 'null');
  const [first, repeated, other] = hydrated.resultContract.form.fields;
  assert.equal(first, repeated);
  assert.equal(other.help, 'Shared help');

  const description = '  Check the booking.\nKeep <b>literal markup</b>.  ';
  const described = reads.taskContract(id, abort);
  request('GET', task + '/contract').flush(JSON.stringify({
    task: { status: 'COMPLETED', description },
  }));
  assert.equal((await described).task.description, description);

  const accepted = reads.taskSubmission(id, abort);
  const submissionText = `{"taskId":"${id}","result":${raw}}`;
  request('GET', task + '/submission').flush(submissionText);
  assert.deepEqual(await accepted, { taskId: id, resultJson: raw.trim() });
  const submissions = reads.submissions(id, id, 20, abort);
  request('GET', activity + '/submissions', { afterTaskId: id, limit: '20' })
    .flush(`{"items":[${submissionText}],"nextAfterTaskId":"${token}"}`);
  assert.deepEqual(await submissions, { items: [{ taskId: id, resultJson: raw.trim() }], nextAfterTaskId: token });
  const absent = reads.taskSubmission(id, abort);
  request('GET', task + '/submission').flush('null');
  assert.equal(await absent, null);

  const tooDeep = reads.taskSubmission(id, abort);
  request('GET', task + '/submission').flush('{"result":' + '['.repeat(128) + '0' + ']'.repeat(128) + '}');
  await assert.rejects(tooDeep, /nesting limit/);
  const concealed = viewer.graph('a/b%2F', abort);
  fault(request('GET', '/fixture/viewer/instances/a%2Fb%252F/graph'), 404, '{"correlationId":"hidden"}');
  await assert.rejects(concealed, { category: 'NOT_FOUND', correlationId: 'hidden' });
});

const completion = freezeEnvelope({
  kind: 'complete', expectedToken: precondition,
  body: { commandId, taskId: id, outcomeKey: 'APPROVE', comment: '  exact comment  ', resultJson: raw },
});
const taskSuccess = { replayed: false, status: 'ACTIVE', taskStatus: 'COMPLETED' };
const receipt = { found: true, result: { ...taskSuccess, replayed: true } };

function assertPrecondition(call, expected, isCancellation = false) {
  assert.equal(call.request.headers.get('X-Task-Precondition'), isCancellation ? null : expected);
  assert.equal(call.request.headers.get('X-Expected-Lock-Version'), isCancellation ? expected : null);
}

test('A4: execution and receipt lookup preserve frozen bodies, token families and independently selected mounts', async t => {
  const { injector, request, backend } = host(t,
    provideWorkflowRuntimeCommandsHttp(commandMount),
    provideWorkflowCancellationHttp(readMount, commandMount),
    provideWorkflowTaskReassignmentHttp(readMount + '/', commandMount + '/'),
  );
  const runtime = injector.get(WORKFLOW_RUNTIME_COMMANDS);
  const cancellation = injector.get(WORKFLOW_CANCELLATION);
  const reassignment = injector.get(WORKFLOW_TASK_REASSIGNMENT);
  const recovery = injector.get(CommandRecovery);
  const abort = signal();
  assert.equal(runtime.start, undefined);
  recovery.setScope({});
  recovery.buffer.remember(completion);
  const original = recovery.execute(false);
  const execute = request('POST', commandMount + '/tasks/' + id + '/complete');
  assertPrecondition(execute, precondition);
  assert.equal(execute.request.headers.get('Content-Type'), 'application/json');
  const { taskId, resultJson, ...decision } = completion.body;
  assert.equal(execute.request.serializeBody(), JSON.stringify(decision).slice(0, -1) + ',"result":' + resultJson + '}');
  execute.error({ type: 'error' });
  await assert.rejects(original);
  assert.equal(recovery.result().outcome, 'UNKNOWN_OUTCOME');
  assert.equal(recovery.buffer.command.body.resultJson, raw);
  backend.expectNone(() => true, 'network loss does not dispatch another mutation');

  const recover = recovery.execute(true);
  const lookup = request('POST', commandMount + '/commands/complete/replay');
  assertPrecondition(lookup, precondition);
  assert.ok(lookup.request.serializeBody().endsWith(',"result":' + resultJson + '}'));
  const { result, ...replayedDecision } = bodyOf(lookup);
  assert.deepEqual(replayedDecision, { ...decision, taskId });
  assert.equal(typeof result, 'object');
  deliver(lookup, receipt);
  assert.deepEqual(await recover, receipt);
  assert.equal(recovery.pendingIds.size, 0);

  for (const kind of ['claim', 'release', 'admin-release']) {
    const fields = { commandId, ...(kind === 'admin-release' && { reason: '  exact reason  ' }) };
    const envelope = freezeEnvelope({ kind, body: { ...fields, taskId: id }, expectedToken: precondition });
    for (const replay of [false, true]) {
      const pending = runtime.ownership(envelope, replay, abort);
      const call = request('POST', commandMount + (replay ? '/commands/' + kind + '/replay' : '/tasks/' + id + '/' + kind));
      assertPrecondition(call, precondition);
      assert.deepEqual(bodyOf(call), replay ? { ...fields, taskId: id } : fields);
      const response = replay ? receipt : taskSuccess;
      deliver(call, response);
      assert.deepEqual(await pending, response);
    }
  }

  const available = cancellation.availability(id, abort);
  const cancellationAvailability = { allowed: true, lockVersion: token };
  deliver(request('GET', readMount + '/instances/' + id + '/cancellation-availability'), cancellationAvailability);
  assert.deepEqual(await available, cancellationAvailability);
  const reason = '  stop\n😀  ';
  const cancel = freezeEnvelope({ kind: 'cancel', body: { commandId, instanceId: id, reason }, expectedToken: token });
  const cancelled = { replayed: false, execution: { commandId, operation: 'CANCEL_WORKFLOW',
    instance: { id, lockVersion: token, status: 'CANCELLED', endedAt: '2026-11-20T00:00:00Z' },
    activity: { id, status: 'CANCELLED' }, cancelledTaskCount: 1,
    firstHistorySequence: id, lastHistorySequence: token } };
  const cancelReceipt = { found: true, result: { ...cancelled, replayed: true } };
  for (const replay of [false, true]) {
    const pending = replay ? cancellation.replay(cancel, abort) : cancellation.cancel(cancel, abort);
    const call = request('POST', commandMount + (replay ? '/commands/cancel/replay' : '/instances/' + id + '/cancel'));
    assertPrecondition(call, token, true);
    assert.deepEqual(bodyOf(call), replay ? { commandId, reason, instanceId: id } : { commandId, reason });
    const response = replay ? cancelReceipt : cancelled;
    deliver(call, response);
    assert.deepEqual(await pending, response);
  }

  const reassignAvailable = reassignment.availability(id, abort);
  deliver(request('GET', readMount + '/tasks/' + id + '/reassignment-availability'), { allowed: true, precondition });
  assert.deepEqual(await reassignAvailable, { allowed: true, precondition });
  const search = ' finance/ & legal + ';
  const targetPage = reassignment.targets(id, 'GROUP', search, cursor, 20, abort);
  const targets = { items: [{ ref: 'Finance & Legal', label: 'Finance reviewers' }], nextCursor: 'another ' + cursor };
  deliver(request('GET', readMount + '/tasks/' + id + '/reassignment-targets',
    { kind: 'GROUP', query: search, cursor, limit: '20' }), targets);
  assert.deepEqual(await targetPage, targets);
  const assignment = { kind: 'GROUP', ref: targets.items[0].ref };
  const reassign = freezeEnvelope({ kind: 'reassign', expectedToken: precondition,
    body: { commandId, taskId: id, assignmentKind: assignment.kind, assignmentRef: assignment.ref, reason } });
  for (const replay of [false, true]) {
    const pending = reassignment.reassign(reassign, replay, abort);
    const call = request('POST', commandMount + (replay ? '/commands/reassign/replay' : '/tasks/' + id + '/reassign'));
    assertPrecondition(call, precondition);
    assert.deepEqual(bodyOf(call), replay ? { commandId, reason, assignment, taskId: id } : { commandId, reason, assignment });
    const response = replay ? receipt : taskSuccess;
    deliver(call, response);
    assert.deepEqual(await pending, response);
  }
});

test('A5: requests abort and distinguish reported failures from uncertain writes', async t => {
  const { injector, backend, request } = host(t,
    provideWorkflowAdministrationHttp(adminMount), provideWorkflowRuntimeCommandsHttp(commandMount),
    provideWorkflowCancellationHttp(readMount, commandMount));
  const admin = injector.get(WORKFLOW_ADMINISTRATION_API);
  const recovery = injector.get(CommandRecovery);
  const alreadyAborted = new AbortController();
  alreadyAborted.abort();
  await assert.rejects(admin.listDefinitions(undefined, 2, alreadyAborted.signal), { name: 'AbortError' });
  backend.expectNone(() => true, 'pre-abort does not send an HTTP request');

  const inFlight = new AbortController();
  const add = t.mock.method(inFlight.signal, 'addEventListener');
  const remove = t.mock.method(inFlight.signal, 'removeEventListener');
  const waiting = admin.listDefinitions(undefined, 2, inFlight.signal);
  const read = request('GET', adminMount + '/definitions', { limit: '2', order: 'DESC' });
  inFlight.abort();
  await assert.rejects(waiting, { name: 'AbortError' });
  assert.equal(read.cancelled, true);
  assert.equal(add.mock.callCount(), 1);
  assert.equal(remove.mock.callCount(), 1);
  assert.equal(remove.mock.calls[0].arguments[1], add.mock.calls[0].arguments[1]);

  const denied = admin.definition(id, signal());
  const failure = { code: 'FORBIDDEN', correlationId: 'denial', diagnostics: [{ pointer: '/definition' }] };
  fault(request('GET', adminMount + '/definitions/' + id), 403, JSON.stringify(failure));
  await assert.rejects(denied, { httpStatus: 403, ...failure });
  const malformed = admin.definition(id, signal());
  fault(request('GET', adminMount + '/definitions/' + id), 404, '<html>Gateway response</html>');
  await assert.rejects(malformed, { httpStatus: 404 });

  const unreadable = admin.saveDocument(id, raw, token, signal());
  request('PUT', adminMount + '/versions/' + id + '/document').flush('invalid JSON');
  await assert.rejects(unreadable, /inspect stored state/i);
  backend.expectNone(() => true, 'administration does not retry an unreadable write');

  const truncated = admin.saveDocument(id, raw, token, signal());
  deliver(request('PUT', adminMount + '/versions/' + id + '/document'),
    { changed: true, after: { id, lockVersion: token } });
  await assert.rejects(truncated, { status: 200, message: /inspect stored state/i });

  recovery.setScope({});
  recovery.buffer.remember(completion);
  const pending = recovery.execute(false);
  const runtimeFailure = { code: 'RESULT_VALIDATION_FAILED', correlationId: 'invalid-result',
    diagnostics: [{ path: '/approvedAmount', message: 'Amount exceeds the allowed maximum.' }] };
  fault(request('POST', commandMount + '/tasks/' + id + '/complete'), 503, JSON.stringify(runtimeFailure));
  await assert.rejects(pending, { httpStatus: 503, code: runtimeFailure.code, correlationId: runtimeFailure.correlationId,
    fieldErrors: runtimeFailure.diagnostics });
  assert.equal(recovery.result().outcome, 'REJECTED');
  assert.equal(recovery.pendingIds.size, 0);
  const unknown = recovery.execute(false);
  request('POST', commandMount + '/tasks/' + id + '/complete').flush('invalid JSON');
  await assert.rejects(unknown);
  assert.equal(recovery.result().outcome, 'UNKNOWN_OUTCOME');
  assert.equal(recovery.buffer.command.body.commandId, commandId);
  assert.ok(recovery.pendingIds.has(commandId));

  const cancellationId = '12345678-1234-1234-1234-123456789abd';
  recovery.buffer.remember({ kind: 'cancel', expectedToken: token,
    body: { commandId: cancellationId, instanceId: id, reason: 'Cancelled by fixture' } });
  const truncatedCancellation = recovery.execute(false);
  deliver(request('POST', commandMount + '/instances/' + id + '/cancel'), {
    replayed: false, execution: { commandId: cancellationId, operation: 'CANCEL_WORKFLOW',
      instance: {}, activity: {}, cancelledTaskCount: 1, firstHistorySequence: id, lastHistorySequence: token },
  });
  await assert.rejects(truncatedCancellation, { status: 200 });
  assert.equal(recovery.result().outcome, 'UNKNOWN_OUTCOME');
  assert.ok(recovery.pendingIds.has(cancellationId));
  backend.expectNone(() => true, 'an incomplete success receipt does not trigger another mutation');
});
