import '@angular/compiler';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import { WORKFLOW_RUNTIME_READS } from '@workflow-engine/ui';
import { provideWorkflowRuntimeReadsHttp } from '@workflow-engine/ui/http';
import { loadTs, services, consoleWorkflowProviders } from '../../../unit/component-loader.mjs';
const {ConsoleTaskDiagnostics}=loadTs(new URL('./task-diagnostics.ts',import.meta.url));

function runtimeReads(t, response) {
 t.mock.method(globalThis, 'fetch', async (path, options) => new Response(await response(path, options)));
 return services(t, [...consoleWorkflowProviders(), provideWorkflowRuntimeReadsHttp('/console/api')]).get(WORKFLOW_RUNTIME_READS);
}

test('shared runtime reads retain exact JSON text, paging token, limit and nullable accepted submission',async t=>{
 const calls=[];const text='{"instanceId":"9007199254740993","lockVersion":"9007199254740997","items":[{"key":"data","type":"JSON","initialized":true,"value":{"n":9007199254740993.00}}],"nextAfterKey":null}';
 const reads=runtimeReads(t,path=>{calls.push(path);return path.endsWith('/submission')?'null':text;});
 const signal=new AbortController().signal;const page=await reads.variables('9007199254740993','key','9007199254740997',signal);
 assert.equal(page.exactJson,text);assert.equal(page.lockVersion,'9007199254740997');
 const url=new URL(calls[0],'https://sandbox.test');
 assert.equal(url.pathname,'/console/api/instances/9007199254740993/variables');
 assert.equal(url.searchParams.get('limit'),'50');assert.equal(url.searchParams.get('afterKey'),'key');assert.equal(url.searchParams.get('expectedLockVersion'),'9007199254740997');
 assert.equal(await reads.taskSubmission('30',signal),null);
});
test('task and activity reads isolate exact result object tokens and preserve omissions',async t=>{
 const amount='{ "nested": {"result":9007199254740993.00}, "exponent":1E+2 }';
 const schema='{"type":"object","properties":{"amount":{"const":9007199254740993.00}}}';
 const signal=new AbortController().signal;
 const responses=[
  '{"task":{"status":"OPEN"},"resultContract":{"mode":"OPTIONAL","form":{"kind":"object","label":"Result","fields":[{"formId":4,"label":"Amount","kind":"number","help":{"textId":1,"text":"Shared help"}},4,{"label":"Other amount","kind":"number","help":{"textId":1}}]}}}',
  '{"taskId":"1","result":'+amount+'}',
  '{"taskId":"2"}',
  '{"items":[{"taskId":"1","result":'+amount+'},{"taskId":"2"},{"taskId":"3","result":{}}],"nextAfterTaskId":"3"}',
  '{"taskId":"4","result":null}',
  '{"result":'+ '['.repeat(128) + '0' + ']'.repeat(128) +'}',
 ];
 const reads=runtimeReads(t,(path,options)=>{
  if(path.endsWith('/diagnostics')){
   assert.equal(path,'/console/api/tasks/1/diagnostics');
   assert.equal(options.signal,signal);
   return '{"task":{"id":"1","activityId":"2","instanceId":"3"},"contract":{"resultContract":{"schema":'+schema+'}},"submission":{"result":'+amount+'}}';
  }
  return responses.shift();
 });
 const contract=await reads.taskContract('1',signal);
 assert.equal(contract.resultContract.form.fields[0],contract.resultContract.form.fields[1]);
 assert.equal(contract.resultContract.form.fields[0].label,'Amount');
 assert.equal(contract.resultContract.form.fields[2].help,'Shared help');
 const diagnostics=await new ConsoleTaskDiagnostics().readTaskDiagnostics('1',signal);
 assert.equal(diagnostics.contractJson,'{"resultContract":{"schema":'+schema+'}}');
 assert.equal(diagnostics.resultJson,amount);
 assert.ok(!Object.hasOwn(contract.resultContract,'schema'));
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
 const responses=[`{"task":{"status":"OPEN"},"inputs":{${inputs}}}`,'{"task":{"status":"OPEN"},"inputs":{"broken":{"type":"JSON"}}}'];
 const reads=runtimeReads(t,()=>responses.shift());
 const signal=new AbortController().signal;
 const contract=await reads.taskContract('1',signal);
 assert.deepEqual(JSON.parse(JSON.stringify(contract.inputs)),Object.fromEntries(
  Object.entries(tokens).map(([key,[type,valueJson]])=>[key,{type,valueJson}])));
 await assert.rejects(reads.taskContract('1',signal),/Invalid read response/);
});

test('previous-decision adapter keeps receiving-task navigation and opaque cursors exact',async t=>{
 const calls=[];const text='{"items":[{"nodeName":"Review","actorLabel":"Reviewer","comment":" <b>Exact</b> "}],"nextCursor":"continued-page"}';
 const reads=runtimeReads(t,path=>{calls.push(path);return text;});
 const signal=new AbortController().signal;
 const page=await reads.previousDecisions('9007199254740997','original-page',20,signal);
 assert.deepEqual(calls,['/console/api/tasks/9007199254740997/previous-decisions?cursor=original-page&limit=20']);
 assert.equal(page.items[0].actorLabel,'Reviewer');assert.equal(page.items[0].comment,' <b>Exact</b> ');assert.equal(page.nextCursor,'continued-page');
});

test('administration sends exact filters and stable bounded cursor without loading instance pages',async t=>{
 const calls=[];const reads=runtimeReads(t,path=>{calls.push(path);return '{"items":[],"nextCursor":"9007199254740993"}';});
 const filters={status:'OPEN',taskName:' Review & 100%_ ',taskType:'SANDBOX_REVIEW',assignmentKind:'GROUP',assignmentRef:'Finance & Legal',assigneeRef:'reviewer',instanceId:'9007199254740993'};
 const page=await reads.administrationTasks(filters,'9007199254740994',100,new AbortController().signal);
 assert.equal(calls.length,1);assert.ok(calls[0].startsWith('/console/api/tasks/administration?'));const query=new URLSearchParams(calls[0].split('?')[1]);
 for(const [key,value] of Object.entries(filters))assert.equal(query.get(key),value);assert.equal(query.get('cursor'),'9007199254740994');assert.equal(query.get('limit'),'100');assert.equal(page.nextCursor,'9007199254740993');
});
