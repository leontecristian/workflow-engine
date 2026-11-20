import '@angular/compiler';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {
 CommandBuffer, freezeEnvelope, WORKFLOW_CANCELLATION, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_TASK_REASSIGNMENT,
} from '@workflow-engine/ui';
import {
 provideWorkflowCancellationHttp, provideWorkflowTaskReassignmentHttp,
} from '@workflow-engine/ui/http';
import {services, loadTs, consoleWorkflowProviders, ConsoleSession} from '../../../unit/component-loader.mjs';

const {consoleRuntimeCommands}=loadTs(new URL('./runtime-command-adapters.ts',import.meta.url));
const instanceId='9007199254740993';
const completion={kind:'complete',body:{commandId:'12345678-1234-1234-1234-123456789abc',taskId:'9223372036854775807',outcomeKey:'APPROVE',comment:' exact '},expectedToken:'opaque-version'};
const identity={actorRef:'reviewer',csrfHeaderName:'X-HOST-CSRF',csrfToken:'token',roles:[]};
const mount='/console/api';
const success={replayed:false,status:'ACTIVE',taskStatus:'COMPLETED'};

function host(t) {
 const injector=services(t,[
  ...consoleWorkflowProviders(),
  {provide:WORKFLOW_RUNTIME_COMMANDS,useFactory:consoleRuntimeCommands},
  provideWorkflowCancellationHttp(mount),
  provideWorkflowTaskReassignmentHttp(mount),
 ]);
 const session=injector.get(ConsoleSession);
 session.session.set(identity);
 session.viewerScope.set({});
 return {
  session, signal:new AbortController().signal, commands:injector.get(WORKFLOW_RUNTIME_COMMANDS),
  cancellation:injector.get(WORKFLOW_CANCELLATION), reassignment:injector.get(WORKFLOW_TASK_REASSIGNMENT),
 };
}

function record(t, answer) {
 const calls=[];
 t.mock.method(globalThis,'fetch',async (url,options)=>{
  calls.push({url:url.slice(mount.length),...options});
  return Response.json(answer(url));
 });
 return calls;
}

test('cancellation availability and mutation/replay preserve exact reason, identifiers and token',async t=>{
 const {cancellation,signal}=host(t);
 const envelope=freezeEnvelope({kind:'cancel',body:{commandId:completion.body.commandId,instanceId,reason:'  stop\n😀  '},expectedToken:'9007199254740999'});
 const calls=record(t,url=>{
  if(url.endsWith('availability'))return {allowed:true,lockVersion:envelope.expectedToken};
  if(url.endsWith('replay'))return {found:false,result:null};
  return {replayed:false,execution:{operation:'CANCEL_WORKFLOW',commandId:completion.body.commandId,
   instance:{id:instanceId,lockVersion:envelope.expectedToken,status:'CANCELLED',endedAt:'2026-11-20T00:00:00Z'},
   activity:{id:'1',status:'CANCELLED'},cancelledTaskCount:1,firstHistorySequence:'1',lastHistorySequence:'2'}};
 });
 await cancellation.availability(instanceId,signal);
 await cancellation.cancel(envelope,signal);
 await cancellation.replay(envelope,signal);
 assert.equal(calls[0].url,'/instances/9007199254740993/cancellation-availability');
 assert.equal(calls[1].url,'/instances/9007199254740993/cancel');
 assert.deepEqual(JSON.parse(calls[1].body),{commandId:envelope.body.commandId,reason:envelope.body.reason});
 assert.equal(calls[2].url,'/commands/cancel/replay');
 assert.deepEqual(JSON.parse(calls[2].body),envelope.body);
 for(const call of calls.slice(1))assert.equal(call.headers['X-Expected-Lock-Version'],envelope.expectedToken);
});

test('completion shares transport and business start preserves its frozen body through the session',async t=>{
 const {commands,signal}=host(t);
 const calls=record(t,url=>url.endsWith('/replay')?{found:false,result:null}:success);
 await commands.complete(completion,signal);
 await commands.replayComplete(completion,signal);
 assert.equal(calls[0].url,'/tasks/9223372036854775807/complete');
 assert.deepEqual(JSON.parse(calls[0].body),{commandId:completion.body.commandId,outcomeKey:'APPROVE',comment:' exact '});
 assert.equal(calls[0].headers['X-Task-Precondition'],completion.expectedToken);
 assert.equal(calls[1].url,'/commands/complete/replay');
 assert.deepEqual(JSON.parse(calls[1].body),completion.body);
 const inputs='{"amount":9007199254740993.00,"exponent":1E+2}';
 const start={kind:'start',body:{commandId:completion.body.commandId,definitionVersionId:'3',requestId:'4',expectedRequestVersion:'0',inputs},expectedToken:null};
 const buffer=new CommandBuffer(commands.start);
 buffer.remember(start);
 const text=buffer.export();
 buffer.clear();
 buffer.restore(text);
 await commands.start.execute(buffer.command,signal);
 await commands.start.replay(buffer.command,signal);
 assert.equal(calls[2].url,'/instances/start');
 assert.equal(calls[3].url,'/commands/start/replay');
 assert.equal(calls[2].body,calls[3].body);
 assert.ok(calls[2].body.includes(inputs));
 assert.equal(calls[2].signal,signal);
});

test('read-only recovery rejects start restore before insertion and rejects duplicate properties',()=>{
 const buffer=new CommandBuffer();
 const start={kind:'start',body:{commandId:completion.body.commandId},expectedToken:null};
 assert.throws(()=>buffer.restore(JSON.stringify(start)),/start adapter/);
 assert.equal(buffer.entries.length,0);
 assert.throws(()=>buffer.restore(JSON.stringify(completion).replace('"comment":" exact "','"comment":null,"comment":"changed"')),/Duplicate/);
 assert.equal(buffer.entries.length,0);
 assert.throws(()=>freezeEnvelope({...completion,expectedToken:null}));
});

test('structured completion and replay send the frozen exact result as an ordinary JSON object',async t=>{
 const {commands,signal}=host(t);
 const calls=record(t,url=>url.endsWith('/replay')?{found:false,result:null}:success);
 const resultJson=' { "amount":9007199254740993.00, "nested":[100e-2, null] } ';
 const original={...completion,body:{...completion.body,resultJson}};
 const buffer=new CommandBuffer();
 buffer.remember(original);
 const exported=buffer.export();
 original.body.resultJson='{}';
 assert.throws(()=>buffer.remember(original),/different original request/);
 buffer.clear();
 const recovered=buffer.restore(exported);
 await commands.complete(recovered,signal);
 await commands.replayComplete(recovered,signal);
 for(const call of calls){
  assert.equal(call.headers['X-Task-Precondition'],completion.expectedToken);
  assert.ok(call.body.endsWith(',"result":'+resultJson+'}'));
  assert.ok(!Object.hasOwn(JSON.parse(call.body),'resultJson'));
 }
 assert.equal(JSON.parse(calls[0].body).taskId,undefined);
 assert.equal(JSON.parse(calls[1].body).taskId,completion.body.taskId);
 for(const invalid of ['null','[]','{"nested":{"a":1,"a":2}}','{} {}','/* comment */{}']){
  assert.throws(()=>freezeEnvelope({...completion,body:{...completion.body,resultJson:invalid}}));
  await assert.rejects(commands.complete({...completion,body:{...completion.body,resultJson:invalid}},signal));
 }
 assert.equal(calls.length,2);
});

test('structured validation feedback uses the server diagnostic path and message',async t=>{
 const {commands,signal}=host(t);
 t.mock.method(globalThis,'fetch',async()=>Response.json({
  code:'RESULT_REJECTED',message:'Check the result.',diagnostics:[{path:'/approvedAmount',message:'Amount exceeds the allowed maximum.'}],
 },{status:422}));
 await assert.rejects(commands.complete(completion,signal),failure=>{
  assert.equal(failure.httpStatus,422);
  assert.equal(failure.code,'RESULT_REJECTED');
  assert.equal(failure.message,'Check the result.');
  assert.deepEqual(failure.fieldErrors,[{path:'/approvedAmount',message:'Amount exceeds the allowed maximum.'}]);
  return true;
 });
});

test('host interceptor adds CSRF and late 401 cannot clear a replacement session',async t=>{
 const {session,commands,signal}=host(t);
 let resolve,options;
 t.mock.method(globalThis,'fetch',(_url,o)=>{options=o;return new Promise(r=>resolve=r);});
 const pending=commands.complete(completion,signal);
 const replacement={};
 session.viewerScope.set(replacement);
 resolve(Response.json({code:'UNAUTHENTICATED'},{status:401}));
 await assert.rejects(pending,{httpStatus:401});
 assert.equal(session.viewerScope(),replacement);
 assert.equal(options.headers['X-HOST-CSRF'],'token');
 assert.equal(options.headers['X-Task-Precondition'],completion.expectedToken);
 assert.ok(options.signal instanceof AbortSignal);
 t.mock.method(globalThis,'fetch',async()=>{throw new Error('network');});
 await assert.rejects(commands.complete(completion,signal),/outcome unknown/);
 t.mock.method(globalThis,'fetch',async()=>new Response(null,{status:401}));
 await assert.rejects(commands.complete(completion,signal),{httpStatus:401});
 assert.equal(session.viewerScope(),null);
});

test('reassignment maps flat frozen fields to wire assignment and the task directory',async t=>{
 const {reassignment,signal}=host(t);
 const envelope=freezeEnvelope({kind:'reassign',body:{commandId:completion.body.commandId,taskId:completion.body.taskId,assignmentKind:'ROLE',assignmentRef:'finance/reviewer',reason:'  exact\n😀  '},expectedToken:completion.expectedToken});
 const calls=record(t,url=>url.includes('targets')?{items:[],nextCursor:null}
  :url.endsWith('availability')?{allowed:true,precondition:completion.expectedToken}
  :url.endsWith('replay')?{found:false,result:null}:success);
 await reassignment.availability(envelope.body.taskId,signal);
 await reassignment.targets(envelope.body.taskId,'ROLE','a & b','opaque+cursor',20,signal);
 await reassignment.reassign(envelope,false,signal);
 await reassignment.reassign(envelope,true,signal);
 assert.equal(calls[0].url,'/tasks/9223372036854775807/reassignment-availability');
 const query=new URLSearchParams(calls[1].url.split('?')[1]);
 assert.equal(query.get('query'),'a & b');
 assert.equal(query.get('cursor'),'opaque+cursor');
 assert.equal(query.get('kind'),'ROLE');
 assert.equal(calls[2].url,'/tasks/9223372036854775807/reassign');
 assert.equal(calls[3].url,'/commands/reassign/replay');
 for(const call of calls.slice(2)){
  const body=JSON.parse(call.body);
  assert.deepEqual(body.assignment,{kind:'ROLE',ref:'finance/reviewer'});
  assert.equal(body.assignmentKind,undefined);
  assert.equal(body.reason,envelope.body.reason);
  assert.equal(call.headers['X-Task-Precondition'],envelope.expectedToken);
 }
 assert.equal(JSON.parse(calls[2].body).taskId,undefined);
 assert.equal(JSON.parse(calls[3].body).taskId,envelope.body.taskId);
});
