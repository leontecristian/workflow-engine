import { test } from 'node:test';
import assert from 'node:assert/strict';
import '@angular/compiler';
import { CommandRecovery } from '@workflow-engine/ui';
import { component, ConsoleSession, core, loadTs, routeAt, router, rxjs, tick } from '../../../../unit/component-loader.mjs';

const { TaskPage } = loadTs(new URL('./task.ts', import.meta.url));
const { ConsoleTaskDiagnostics } = loadTs(new URL('../../data/task-diagnostics.ts', import.meta.url));

function setup(t) {
  const session = { admin: true, viewerScope: core.signal({}), identityChanges: new rxjs.Subject() };
  const reads = [];
  const { page, route } = component(t, TaskPage, {}, routeAt({ id: '7' }), undefined, [
    { provide: ConsoleSession, useValue: session },
    { provide: CommandRecovery, useValue: { buffer: {} } },
    { provide: ConsoleTaskDiagnostics, useValue: { readTaskDiagnostics(id, signal) {
      return new Promise((resolve, reject) => reads.push({ id, signal, resolve, reject }));
    } } },
  ]);
  page.ngOnInit();
  return { page, route, reads, session };
}

test('sandbox inspection starts on expansion and keeps a failed read local until retry', async t => {
  const { page, reads, session } = setup(t);
  assert.equal(page.inspection().open, false);
  assert.equal(reads.length, 0);

  page.toggleInspection(true);
  page.toggleInspection(true);
  assert.equal(reads.length, 1);
  reads[0].reject(new Error('Read rejected'));
  await tick();
  assert.equal(page.inspection().failed, true);
  assert.ok(session.viewerScope());

  const retry = page.loadInspection();
  const data = { task: { id: '7' } };
  reads[1].resolve(data);
  await retry;
  assert.equal(page.inspection().data, data);
});

test('switching task or session closes inspection and ignores responses that arrive after abort', async t => {
  const { page, route, reads, session } = setup(t);
  page.toggleInspection(true);
  route.paramMap.next(router.convertToParamMap({ id: '8' }));
  assert.ok(reads[0].signal.aborted);
  assert.equal(page.inspection().open, false);

  page.toggleInspection(true);
  reads[0].resolve({ task: { id: '7' } });
  await tick();
  assert.equal(page.inspection().data, undefined);
  assert.equal(page.inspection().loading, true);
  assert.equal(reads[1].id, '8');

  session.admin = false;
  session.viewerScope.set({});
  session.identityChanges.next('reviewer');
  assert.ok(reads[1].signal.aborted);
  reads[1].resolve({ task: { id: '8' } });
  await tick();
  assert.equal(page.inspection().data, undefined);
  assert.equal(page.inspection().open, false);
  page.toggleInspection(true);
  assert.equal(reads.length, 2);
});
