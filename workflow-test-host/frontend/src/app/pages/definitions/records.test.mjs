import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadTs, core } from '../../../../unit/component-loader.mjs';
import { WORKFLOW_VIEWER_NAVIGATION } from '@workflow-engine/ui';
const { ConsoleRecords } = loadTs(new URL('../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/support/records.ts', import.meta.url));

test('definition ID column precedes plain names and uses the exact guarded host destination', t => {
  const requests = [], destination = { hostUrlTree:true };
  let available = true;
  const injector = core.createEnvironmentInjector([
    { provide:WORKFLOW_VIEWER_NAVIGATION, useValue:{ destination(intent) {
      requests.push(intent); return available ? destination : null;
    } } },
  ], null);
  t.after(() => injector.destroy());
  const records = core.runInInjectionContext(injector, () => new ConsoleRecords());
  records.kind = () => 'definitions'; records.disabled = () => false;
  assert.deepEqual(Array.from(records.columns(), column => [column.key, column.label]), [
    ['id','ID'], ['name','Name'], ['key','Workflow key'], ['createdBy','Created by'], ['createdAt','Created'],
  ]);
  const row = { id:'9007199254740993' };
  assert.equal(records.definitionRoute(row), destination);
  assert.equal(requests[0].kind, 'definition');
  assert.equal(requests[0].id, row.id);
  available = false;
  assert.equal(records.definitionRoute(row), null);
  const count = requests.length;
  records.disabled = () => true;
  assert.equal(records.definitionRoute(row), null);
  records.disabled = () => false; records.kind = () => 'catalog';
  assert.equal(records.definitionRoute(row), null);
  assert.equal(requests.length, count);
});

test('a consumer without a navigation adapter renders definition IDs without a link', t => {
  const injector = core.createEnvironmentInjector([], null);
  t.after(() => injector.destroy());
  const records = core.runInInjectionContext(injector, () => new ConsoleRecords());
  records.kind = () => 'definitions';
  assert.equal(records.definitionRoute({id:'52'}), null);
});
