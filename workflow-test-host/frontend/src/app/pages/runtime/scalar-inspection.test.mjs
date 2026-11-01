import { test } from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,component,routeAt,tick} from '../../../../unit/component-loader.mjs';
const {InstancePage}=loadTs(new URL('./instance.ts',import.meta.url));
test('variable paging retains token and clears stale pages for explicit refresh',async t => {
  const calls=[];
  const {page}=component(t,InstancePage,{
    async instance(){return {id:'1'};}, async instanceGraph(){throw new Error('GRAPH_UNAVAILABLE');},
    async variables(...args){calls.push(args);if(calls.length===2)throw new Error('STALE_VERSION'); return {instanceId:'1',lockVersion:'9007199254740993',items:[{key:'q',type:'LONG',initialized:true,value:'9007199254740993'}],nextAfterKey:'q'};}
  },routeAt({id:'1'}));
  page.ngOnInit(); await tick(); await page.loadVariables();
  assert.equal(page.variables().items[0].value,'9007199254740993');
  await page.loadVariables(true); assert.deepEqual(calls[1],['1','q','9007199254740993']);
  assert.equal(page.variables(),null); assert.match(page.variableError(),/Refresh/); assert.ok(page.detail()); assert.match(page.graphError(),/GRAPH_UNAVAILABLE/);
  await page.loadVariables(); assert.deepEqual(calls[2],['1',undefined,undefined]);
});
