import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadTs, component } from '../../../../unit/administration-component.mjs';
const { selectedObjectJson, applyFragment } = loadTs(new URL('../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/definition-fragment.ts', import.meta.url));
const { WorkflowDefinitionVersionComponent: VersionPage } = loadTs(new URL('../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/version.ts', import.meta.url));
const source = '{"version":9007199254740993,"nodes":[{"key":"A","value":900719925474099312345}],"transitions":[{"key":"go","from":"A","to":"A","condition":"#vars.n > 10"}],"other":"\\u0041"}';
test('extract and splice selected object without changing exact surrounding tokens', () => {
 const original = selectedObjectJson(source,'nodes','A');
 assert.equal(original,'{"key":"A","value":900719925474099312345}');
 const replacement='{"key":"A","value":900719925474099399999}';
 const edit={source,kind:'nodes',key:'A',replacement};
 assert.equal(applyFragment(source,edit),source.replace(original,replacement));
 const edge=selectedObjectJson(source,'transitions','go');
 assert.match(edge,/#vars.n > 10/);
});
test('invalid fragments, duplicate properties, stale text and renamed keys do not apply', () => {
 for(const replacement of ['{','[]','null','{"key":"B"}','{"key":"A","x":1,"x":2}']) {
  assert.throws(()=>applyFragment(source,{source,kind:'nodes',key:'A',replacement}));
 }
 assert.throws(()=>applyFragment(source+' ',{source,kind:'nodes',key:'A',replacement:'{"key":"A"}'}));
 assert.throws(()=>selectedObjectJson('{"nodes":[],"nodes":[]}', 'nodes','A'));
});
test('draft apply marks full editor dirty; published apply is blocked; stale source preserves text', t => {
 const {page}=component(t,VersionPage);
 page.detail.set({summary:{status:'DRAFT'}}); page.document=source;
 const edit={source,kind:'nodes',key:'A',replacement:'{"key":"A","name":"Changed"}'};
 page.applySelectedObject(edit);
 assert.equal(page.dirty,true); assert.match(page.document,/Changed/);
 const after=page.document;
 page.applySelectedObject(edit); assert.equal(page.document,after); assert.ok(page.error());
 page.detail.set({summary:{status:'PUBLISHED'}}); page.document=source;
 page.applySelectedObject(edit); assert.equal(page.document,source);
});

test('node selection exposes exact fragment; only editable drafts emit application', async t => {
 const {signal}=await import('@angular/core');
 const {DefinitionGraphComponent}=loadTs(new URL('../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/definition-graph.ts',import.meta.url));
 const {page}=component(t,DefinitionGraphComponent);
 page.document=signal(source);
 page.editable=signal(false);
 let emitted;
 page.fragmentApplied={emit(value){emitted=value;}};
 page.selectObject('nodes','A');
 assert.match(page.fragmentText,/900719925474099312345/);
 assert.equal(page.selectedObject().kind,'nodes');
 page.applyObject(); assert.equal(emitted,undefined);
 page.editable.set(true);
 page.fragmentText='{"key":"A","name":"New"}';
 page.applyObject(); assert.equal(emitted.replacement,page.fragmentText);
 page.fragmentText='{'; emitted=undefined;
 page.applyObject(); assert.equal(emitted,undefined); assert.ok(page.fragmentError());
 page.clearSelection(); assert.equal(page.selectedObject(),null);
});
