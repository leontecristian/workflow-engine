import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,core,ConsoleSession} from '../../../unit/component-loader.mjs';
import {CommandBuffer,freezeEnvelope} from '@workflow-engine/ui';
const {ConsoleRuntimeCommands,ConsoleRuntimeSessionBridge}=loadTs(new URL('./runtime-command-adapters.ts',import.meta.url));
const completion={kind:'complete',body:{commandId:'12345678-1234-1234-1234-123456789abc',instanceId:'9007199254740993',taskId:'9223372036854775807',outcomeKey:'APPROVE',comment:' exact '},expectedToken:'9007199254740999'};
function service(t,Type,providers=[]){const injector=core.createEnvironmentInjector(providers,null);t.after(()=>injector.destroy());return core.runInInjectionContext(injector,()=>new Type());}
test('completion dispatch preserves paths, exact body, token and cancellation; start dispatch preserves raw inputs',async t=>{
 const calls=[];const adapter=service(t,ConsoleRuntimeCommands,[{provide:ConsoleSession,useValue:{runtimeCommand:async(...args)=>{calls.push(args);return {};}}}]);const signal=new AbortController().signal;
 await adapter.complete(completion,signal);await adapter.replayComplete(completion,signal);
 assert.equal(calls[0][0],'/instances/9007199254740993/tasks/9223372036854775807/complete');assert.deepEqual({...calls[0][1]},{commandId:completion.body.commandId,outcomeKey:'APPROVE',comment:' exact '});assert.equal(calls[0][2],completion.expectedToken);assert.equal(calls[0][4],signal);assert.equal(calls[1][0],'/commands/complete/replay');assert.equal(calls[1][1],completion.body);
 const inputs='{"amount":9007199254740993.00,"exponent":1E+2}';const start={kind:'start',body:{commandId:completion.body.commandId,definitionVersionId:'3',requestId:'4',expectedRequestVersion:'0',inputs},expectedToken:null};
 const buffer=new CommandBuffer(adapter.start);buffer.remember(start);const text=buffer.export();buffer.clear();buffer.restore(text);await adapter.start.execute(buffer.command,signal);await adapter.start.replay(buffer.command,signal);
 assert.equal(calls[2][0],'/instances/start');assert.equal(calls[3][0],'/commands/start/replay');assert.equal(calls[2][1],calls[3][1]);assert.ok(calls[2][1].includes(inputs));assert.equal(calls[2][3],true);
});
test('read-only recovery rejects start restore before insertion and rejects duplicate properties',()=>{
 const buffer=new CommandBuffer();const start={kind:'start',body:{commandId:completion.body.commandId},expectedToken:null};assert.throws(()=>buffer.restore(JSON.stringify(start)),/start adapter/);assert.equal(buffer.entries.length,0);
 assert.throws(()=>buffer.restore(JSON.stringify(completion).replace('"comment":" exact "','"comment":null,"comment":"changed"')),/Duplicate/);assert.equal(buffer.entries.length,0);
 assert.throws(()=>freezeEnvelope({...completion,expectedToken:null}));
});
test('structured completion and replay send the frozen exact result as an ordinary JSON object',async t=>{
 const calls=[];
 const adapter=service(t,ConsoleRuntimeCommands,[{provide:ConsoleSession,useValue:{runtimeCommand:async(...args)=>{calls.push(args);return {};}}}]);
 const signal=new AbortController().signal;
 const resultJson=' { "amount":9007199254740993.00, "nested":[100e-2, null] } ';
 const original={...completion,body:{...completion.body,resultJson}};
 const buffer=new CommandBuffer();
 buffer.remember(original);
 const exported=buffer.export();
 original.body.resultJson='{}';
 assert.throws(()=>buffer.remember(original),/different original request/);
 buffer.clear();
 const recovered=buffer.restore(exported);
 await adapter.complete(recovered,signal);
 await adapter.replayComplete(recovered,signal);
 for(const [,body,token,raw] of calls){
  assert.equal(token,completion.expectedToken);
  assert.equal(raw,true);
  assert.ok(body.endsWith(',"result":'+resultJson+'}'));
  assert.ok(!Object.hasOwn(JSON.parse(body),'resultJson'));
 }
 assert.equal(JSON.parse(calls[0][1]).taskId,undefined);
 assert.equal(JSON.parse(calls[1][1]).taskId,completion.body.taskId);
 for(const invalid of ['null','[]','{"nested":{"a":1,"a":2}}','{} {}','/* comment */{}']){
  assert.throws(()=>freezeEnvelope({...completion,body:{...completion.body,resultJson:invalid}}));
  await assert.rejects(async()=>adapter.complete({...completion,body:{...completion.body,resultJson:invalid}},signal));
 }
 assert.equal(calls.length,2);
});
test('structured validation feedback uses the server diagnostic path and message',async t=>{
 const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);
 const session=service(t,ConsoleSession);
 session.session.set({actorRef:'reviewer',csrfHeaderName:'X-HOST-CSRF',csrfToken:'token',roles:[]});
 session.viewerScope.set({});
 globalThis.fetch=async()=>new Response(JSON.stringify({
  code:'RESULT_REJECTED',diagnostics:[{path:'/approvedAmount',message:'Amount exceeds the allowed maximum.'}],
 }),{status:422});
 await assert.rejects(session.runtimeCommand('/complete',{},'1',false,new AbortController().signal),
  {httpStatus:422,code:'RESULT_REJECTED',message:'/approvedAmount — Amount exceeds the allowed maximum.'});
});
test('runtime transport carries CSRF and old-session 401 cannot clear a replacement session',async t=>{
 const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);const session=service(t,ConsoleSession);const identity={actorRef:'reviewer',csrfHeaderName:'X-HOST-CSRF',csrfToken:'token',roles:[]};session.session.set(identity);session.viewerScope.set({});let resolve,options;
 globalThis.fetch=async(_url,o)=>{options=o;return new Promise(r=>resolve=r);};const signal=new AbortController().signal;const pending=session.runtimeCommand('/complete',{commandId:'id'},completion.expectedToken,false,signal);
 const replacement={};session.viewerScope.set(replacement);resolve(new Response('{"code":"UNAUTHENTICATED"}',{status:401}));await assert.rejects(pending,/401/);assert.equal(session.viewerScope(),replacement);assert.equal(options.headers['X-HOST-CSRF'],'token');assert.equal(options.headers['X-Expected-Lock-Version'],completion.expectedToken);assert.equal(options.signal,signal);
 globalThis.fetch=async()=>{throw new Error('network');};await assert.rejects(session.runtimeCommand('/complete',{},null,false,signal),/outcome unknown/);
});
