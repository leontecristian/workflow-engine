import { test } from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,component,routeAt,tick} from '../../../../unit/component-loader.mjs';
const {InstancePage}=loadTs(new URL('./instance.ts',import.meta.url));

test('variable values appear individually with exact JSON numbers and distinct absent/null states', t => {
  const {page}=component(t,InstancePage,{},undefined,undefined,[{provide:loadTs(new URL('../../data/console-session.ts',import.meta.url)).ConsoleSession,useValue:{viewerScope:()=>({})}}]);
  const exactJson='{"items":[{"key":"requestData","type":"JSON","initialized":true,"value":{"amount":9007199254740993.00,"items":[1E+2]}},{"key":"status","type":"STRING","initialized":true,"value":"APPROVED"},{"key":"empty","type":"STRING","initialized":true,"value":null},{"key":"missing","type":"STRING","initialized":false,"value":null}]}';
  page.variables.set({...JSON.parse(exactJson),exactJson});
  const rows=page.variableDisplays();
  assert.equal(rows[0].displayValue,'{\n  "amount": 9007199254740993.00,\n  "items": [\n    1E+2\n  ]\n}');
  assert.equal(rows[1].displayValue,'APPROVED');
  assert.equal(rows[2].displayValue,'null');
  assert.equal(rows[3].displayValue,'No stored value');
  page.variables.set(null);
  assert.equal(page.variableDisplays().length,0);
});
test('variable paging retains token and clears stale pages for explicit refresh',async t => {
  const calls=[];
  const {page}=component(t,InstancePage,{
    async instance(){return {id:'1'};}, async instanceGraph(){throw new Error('GRAPH_UNAVAILABLE');},
    async variables(...args){calls.push(args);if(calls.length===2)throw new Error('STALE_VERSION'); return {instanceId:'1',lockVersion:'9007199254740993',items:[{key:'q',type:'LONG',initialized:true,value:'9007199254740993'}],nextAfterKey:'q'};}
  },routeAt({id:'1'}),undefined,[{provide:loadTs(new URL('../../data/console-session.ts',import.meta.url)).ConsoleSession,useValue:{viewerScope:()=>({})}}]);
  page.ngOnInit(); await tick(); await page.loadVariables();
  assert.equal(page.variables().items[0].value,'9007199254740993');
  await page.loadVariables(true); assert.deepEqual(calls[1],['1','q','9007199254740993']);
  assert.equal(page.variables(),null); assert.match(page.variableError(),/Refresh/);
  await page.loadVariables(); assert.deepEqual(calls[2],['1',undefined,undefined]);
});
