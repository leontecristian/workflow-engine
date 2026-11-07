import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,component,ConsoleSession} from '../../../unit/component-loader.mjs';
const {ConsoleRuntimeReads}=loadTs(new URL('./runtime-read-adapters.ts',import.meta.url));
test('runtime host reads retain exact JSON text, paging token, limit and nullable accepted submission',async t=>{
 const calls=[];const text='{"instanceId":"9007199254740993","lockVersion":"9007199254740997","items":[{"key":"data","type":"JSON","initialized":true,"value":{"n":9007199254740993.00}}],"nextAfterKey":null}';
 const session={async administrationCall(...args){calls.push(args);return args[0].endsWith('/submission')?'null':text;}};
 const {page:reads}=component(t,ConsoleRuntimeReads,{},undefined,undefined,[{provide:ConsoleSession,useValue:session}]);
 const signal=new AbortController().signal;const page=await reads.variables('9007199254740993','key','9007199254740997',signal);
 assert.equal(page.exactJson,text);assert.equal(page.lockVersion,'9007199254740997');
 const query=new URLSearchParams(calls[0][0].split('?')[1]);assert.equal(query.get('limit'),'50');assert.equal(query.get('afterKey'),'key');assert.equal(query.get('expectedLockVersion'),'9007199254740997');assert.equal(calls[0][1],signal);assert.equal(calls[0].at(-1),true);
 assert.equal(await reads.taskSubmission('30',signal),null);
});
