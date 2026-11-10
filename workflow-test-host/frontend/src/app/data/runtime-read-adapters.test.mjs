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
test('task and activity reads isolate exact result object tokens and preserve omissions',async t=>{
 const amount='{ "nested": {"result":9007199254740993.00}, "exponent":1E+2 }';
 const schema='{"type":"object","properties":{"amount":{"const":9007199254740993.00}}}';
 const responses=[
  '{"resultContract":{"mode":"OPTIONAL","schema":'+schema+'}}',
  '{"taskId":"1","result":'+amount+'}',
  '{"taskId":"2"}',
  '{"items":[{"taskId":"1","result":'+amount+'},{"taskId":"2"},{"taskId":"3","result":{}}],"nextAfterTaskId":"3"}',
  '{"taskId":"4","result":null}',
  '{"result":'+ '['.repeat(128) + '0' + ']'.repeat(128) +'}',
 ];
 const session={async administrationCall(){return responses.shift();}};
 const {page:reads}=component(t,ConsoleRuntimeReads,{},undefined,undefined,[{provide:ConsoleSession,useValue:session}]);
 const signal=new AbortController().signal;
 assert.equal((await reads.taskContract('1',signal)).resultContract.schemaJson,schema);
 const accepted=await reads.taskSubmission('1',signal);
 assert.equal(accepted.resultJson,amount);
 assert.ok(!Object.hasOwn(accepted,'result'));
 assert.ok(!Object.hasOwn(await reads.taskSubmission('2',signal),'resultJson'));
 const page=await reads.submissions('2',undefined,3,signal);
 assert.equal(page.nextAfterTaskId,'3');
 assert.deepEqual(Array.from(page.items,row=>row.resultJson),[amount,undefined,'{}']);
 await assert.rejects(reads.taskSubmission('4',signal),/Invalid read response/);
 await assert.rejects(reads.taskSubmission('5',signal),/nesting limit/);
});
test('task contract inputs preserve exact tokens and keep null distinct from a missing value',async t=>{
 const tokens={
  payload:['JSON','{ "amount":9007199254740993.00,"tiny":1E-50 }'],
  items:['JSON','[9007199254740993.00]'],
  limit:['LONG','"9223372036854775807"'],
  reviewDate:['DATE','"2026-11-10"'],
  missing:['STRING','null'],
  approved:['BOOLEAN','false'],
 };
 const inputs=Object.entries(tokens).map(([key,[type,token]])=>`"${key}":{"type":"${type}","value":${token}}`).join(',');
 const responses=[`{"inputs":{${inputs}}}`,'{"inputs":{"broken":{"type":"JSON"}}}'];
 const session={async administrationCall(){return responses.shift();}};
 const {page:reads}=component(t,ConsoleRuntimeReads,{},undefined,undefined,[{provide:ConsoleSession,useValue:session}]);
 const signal=new AbortController().signal;
 const contract=await reads.taskContract('1',signal);
 assert.deepEqual(JSON.parse(JSON.stringify(contract.inputs)),Object.fromEntries(
  Object.entries(tokens).map(([key,[type,valueJson]])=>[key,{type,valueJson}])));
 await assert.rejects(reads.taskContract('1',signal),/Invalid read response/);
});
