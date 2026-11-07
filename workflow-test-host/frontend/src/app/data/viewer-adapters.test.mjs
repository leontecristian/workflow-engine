import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,core} from '../../../unit/component-loader.mjs';
const {ConsoleSession}=loadTs(new URL('./console-session.ts',import.meta.url));
const {ConsoleViewerReads,ConsoleViewerNavigation}=loadTs(new URL('./viewer-adapters.ts',import.meta.url));
import {Router} from '@angular/router';
function service(t,Type,providers){
  const injector=core.createEnvironmentInjector(providers,null);
  t.after(()=>injector.destroy());return core.runInInjectionContext(injector,()=>new Type());
}
test('read adapter exposes exactly the four paths with exact IDs and host cancellation',async t=>{
  const calls=[];
  const adapter=service(t,ConsoleViewerReads,[{provide:ConsoleSession,useValue:{viewerRead:async(...args)=>{calls.push(args);return {};}}}]);
  const signal=new AbortController().signal,id='9007199254740993';
  await adapter.instance(id,signal);await adapter.history(id,'9007199254740995',100,signal);await adapter.historyEvent(id,'9007199254740995',signal);await adapter.graph(id,signal);
  assert.deepEqual(calls.map(c=>c[0]),['/instances/'+id,'/instances/'+id+'/history?afterSequence=9007199254740995&limit=100','/instances/'+id+'/history/9007199254740995','/instances/'+id+'/graph']);
  assert.ok(calls.every(c=>c[1]===signal));
});
test('sandbox read transport preserves exact details and invalidates sessions on 401',async t=>{
  const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);
  const session=service(t,ConsoleSession,[]);session.session.set({actorRef:'reviewer',roles:[]});session.viewerScope.set({});
  let observed;const signal=new AbortController().signal;
  const exact='{"n":9007199254740993,"d":12500.00,"nil":null}';
  globalThis.fetch=async(url,options)=>{observed={url,options};return new Response(JSON.stringify({detailsJson:exact}),{status:200,headers:{'Content-Type':'application/json'}});};
  assert.equal((await session.viewerRead('/instances/1/history/1',signal)).detailsJson,exact);
  assert.equal(observed.url,'/console/api/instances/1/history/1');assert.equal(observed.options.signal,signal);assert.equal(observed.options.credentials,'same-origin');
  const adapter=service(t,ConsoleViewerReads,[{provide:ConsoleSession,useValue:session}]);
  globalThis.fetch=async()=>new Response('{"code":"UNAUTHENTICATED","diagnostics":[]}',{status:401});
  await assert.rejects(adapter.instance('1',signal),e=>e.category==='UNAUTHENTICATED');assert.equal(session.viewerScope(),null);assert.equal(session.session(),null);
});
test('read failures normalize separately from command recovery and same-user login replaces scope',async t=>{
  const original=globalThis.fetch;t.after(()=>globalThis.fetch=original);
  const session=service(t,ConsoleSession,[]),signal=new AbortController().signal;
  const adapter=service(t,ConsoleViewerReads,[{provide:ConsoleSession,useValue:session}]);
  for(const [response,category] of [[()=>new Response('HTML login',{status:200}),'FAILED'],[()=>new Response('{"code":"FORBIDDEN"}',{status:403}),'NOT_FOUND'],[()=>new Response('{"code":"LIMIT_EXCEEDED"}',{status:422}),'LIMIT_EXCEEDED'],[()=>{throw new DOMException('Aborted','AbortError');},'CANCELLED']]){
    globalThis.fetch=async()=>response();await assert.rejects(adapter.instance('1',signal),e=>e.category===category && !/command|Retry|Find result/.test(e.message));
  }
  globalThis.fetch=async()=>new Response('{"actorRef":"same-user","roles":["CONSOLE_REVIEWER"]}',{status:200});
  await session.initialize();const previous=session.viewerScope();await session.initialize();assert.notEqual(session.viewerScope(),previous);
});
test('navigation delegates exact viewer intents to existing sandbox destinations',t=>{
  const navigation=service(t,ConsoleViewerNavigation,[{provide:Router,useValue:{createUrlTree:parts=>parts}}]);
  assert.deepEqual(Array.from(navigation.destination({kind:'event',instanceId:'9007199254740993',sequence:'9007199254740995'})),['/history-event','9007199254740993','9007199254740995']);
  assert.deepEqual(Array.from(navigation.destination({kind:'task',id:'9007199254740997'})),['/task','9007199254740997']);
  assert.deepEqual(Array.from(navigation.destination({kind:'activity',id:'9007199254740999'})),['/activity','9007199254740999']);
});
