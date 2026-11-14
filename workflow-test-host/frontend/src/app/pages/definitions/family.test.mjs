import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadTs, component, tick } from '../../../../unit/administration-component.mjs';
const { WorkflowDefinitionFamilyComponent } = loadTs(new URL('../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/family.ts', import.meta.url));
const { DEFAULT_ADMINISTRATION_MESSAGES } = loadTs(new URL('../../../../../../workflow-ui/projects/workflow-ui/src/lib/administration/contracts.ts', import.meta.url));

test('versions cursor navigation commits rows and metadata only after success and blocks overlap', async t => {
  let fail = false, finish;
  const calls = [];
  const { page } = component(t, WorkflowDefinitionFamilyComponent, {
    async listVersions(id, filter, cursor, size) {
      calls.push({id,filter,cursor,size});
      if (fail) throw new Error('Read failed');
      if (cursor === 'second') return new Promise(resolve => finish = resolve);
      return {items:[{id:'first',version:1}], nextAfterId:'second'};
    },
    async definition() { return {id:'37',name:fail ? 'Failed read metadata' : 'Retained metadata'}; },
  });
  page.definitionId = '37';
  assert.equal(page.meta.title, 'Workflow Definition');
  // Source-level default: the host's installed package is refreshed by the final build.
  assert.equal(DEFAULT_ADMINISTRATION_MESSAGES.familyTitle, 'Workflow Definition');
  await page.load();
  const pending = page.nextPage();
  await page.nextPage(); await page.previousPage();
  assert.equal(calls.length, 2);
  assert.equal(page.pageIndex, 0);
  finish({items:[{id:'next',version:2}],nextAfterId:'third'}); await pending;
  assert.equal(page.pageIndex, 1);
  fail = true;
  await page.nextPage(); await page.previousPage(); await page.load(undefined, true);
  assert.equal(page.pageIndex, 1);
  assert.equal(page.rows()[0].id, 'next');
  assert.equal(page.name, 'Retained metadata');
  assert.equal(page.next, 'third');
  fail = false;
  await page.previousPage();
  assert.equal(page.pageIndex, 0);
  assert.equal(calls.at(-1).cursor, undefined);
  assert.ok(calls.every(call => call.id === '37'));
});

test('version filter, size, refresh and identity/session changes reset successful cursor history', async t => {
  let fail = false;
  const { page } = component(t, WorkflowDefinitionFamilyComponent, {
    async listVersions(id,filter,cursor,size) {
      if (fail) throw new Error('Read failed');
      return {items:[{id:cursor ?? 'first',filter,size,definitionId:id}],nextAfterId:cursor ? null : 'second'};
    },
    async definition(id) { return {id,name:'Family'}; },
  });
  page.definitionId = '37';
  await page.load(); await page.nextPage();
  fail = true;
  const sizeSelect = {value:10}, filterSelect = {value:'PUBLISHED'};
  await page.changePageSize(10, sizeSelect); await page.changeFilter('PUBLISHED', filterSelect);
  assert.equal(page.pageIndex, 1);
  assert.equal(sizeSelect.value, 50);
  assert.equal(filterSelect.value, '');
  fail = false;
  await page.changeFilter('PUBLISHED');
  assert.equal(page.pageIndex, 0);
  assert.equal(page.filter, 'PUBLISHED');
  await page.nextPage(); await page.changePageSize(10);
  assert.equal(page.pageIndex, 0);
  assert.equal(page.pageSize, 10);
  await page.nextPage(); await page.load(undefined, true);
  assert.equal(page.pageIndex, 0);
  await page.nextPage();
  page.definitionId = '38'; page.ngOnChanges({definitionId:true}); await tick();
  assert.equal(page.pageIndex, 0);
  assert.equal(page.rows()[0].definitionId, '38');
  await page.nextPage();
  page.sessionScope = {}; page.ngOnChanges({sessionScope:true}); await tick();
  assert.equal(page.pageIndex, 0);
});
