import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,core,services,ConsoleSession,consoleWorkflowProviders} from '../../../unit/component-loader.mjs';
const {ConsoleViewerReads,ConsoleViewerNavigation}=loadTs(new URL('./viewer-adapters.ts',import.meta.url));
import {Router} from '@angular/router';
function service(t,Type,providers){
  const injector=core.createEnvironmentInjector(providers,null);
  t.after(()=>injector.destroy());return core.runInInjectionContext(injector,()=>new Type());
}
function viewer(t, roles=[]) {
  const injector=services(t,consoleWorkflowProviders());
  const session=injector.get(ConsoleSession);
  session.session.set({actorRef:roles.length ? 'admin' : 'reviewer',roles});
  session.viewerScope.set({});
  return {session,adapter:core.runInInjectionContext(injector,()=>new ConsoleViewerReads())};
}
test('read adapter keeps raw history paths for an administrator with exact IDs and host cancellation',async t=>{
  const calls=[];
  t.mock.method(globalThis,'fetch',async url=>{
    calls.push(url);
    return new Response('{}');
  });
  const {adapter}=viewer(t,['CONSOLE_ADMIN']);
  const controller=new AbortController(), id='9007199254740993';
  const path='/console/api/instances/'+id;
  const reads=[
    signal=>adapter.instance(id,signal),
    signal=>adapter.history(id,'9007199254740995',100,signal),
    signal=>adapter.historyEvent(id,'9007199254740995',signal),
    signal=>adapter.graph(id,signal),
  ];
  for(const read of reads) await read(controller.signal);
  assert.deepEqual(calls,[path,path+'/history?afterSequence=9007199254740995&limit=100',path+'/history/9007199254740995',path+'/graph']);
  controller.abort();
  for(const read of reads) await assert.rejects(read(controller.signal),error=>error.category==='CANCELLED');
  assert.equal(calls.length,4);
});
test('reviewer history uses business projections for paging, event references and old numeric bookmarks',async t=>{
  const calls=[];
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    calls.push({url,options});
    return new Response('{}');
  });
  const {adapter}=viewer(t);
  const signal=new AbortController().signal;
  const id='9007199254740993', cursor='a'.repeat(48), eventRef='b'.repeat(48);
  const path='/console/api/instances/'+id+'/business-history';
  await adapter.history(id,'0',10,signal);
  await adapter.history(id,cursor,10,signal);
  await adapter.historyEvent(id,eventRef,signal);
  await adapter.historyEvent(id,'5',signal);
  assert.deepEqual(calls.map(c=>c.url),[path+'?limit=10',path+'?limit=10&cursor='+cursor,path+'/'+eventRef,path+'/5']);
  assert.ok(calls.every(c=>c.options.signal===signal));
  assert.ok(calls.every(c=>c.options.credentials==='same-origin'));
});
test('sandbox read transport preserves exact details and invalidates sessions on 401',async t=>{
  const {session,adapter}=viewer(t,['CONSOLE_ADMIN']);
  let observed;const signal=new AbortController().signal;
  const exact='{"n":9007199254740993,"d":12500.00,"nil":null}';
  let response=new Response(JSON.stringify({detailsJson:exact}),{status:200,headers:{'Content-Type':'application/json'}});
  t.mock.method(globalThis,'fetch',async(url,options)=>{observed={url,options};return response;});
  assert.equal((await adapter.historyEvent('1','1',signal)).detailsJson,exact);
  assert.equal(observed.url,'/console/api/instances/1/history/1');
  response=new Response('{"code":"UNAUTHENTICATED","diagnostics":[]}',{status:401});
  await assert.rejects(adapter.instance('1',signal),e=>e.category==='UNAUTHENTICATED');assert.equal(session.viewerScope(),null);assert.equal(session.session(),null);
});
test('read failures normalize separately from command recovery and same-user login replaces scope',async t=>{
  const {session,adapter}=viewer(t);
  const controller=new AbortController();
  let response;
  t.mock.method(globalThis,'fetch',async()=>response);
  const expectCategory=category=>error=>error.category===category && !/command|Retry|Find result/.test(error.message);
  for(const [candidate,category] of [[new Response('HTML login',{status:200}),'FAILED'],[new Response('{"code":"FORBIDDEN"}',{status:403}),'NOT_FOUND'],[new Response('{"code":"LIMIT_EXCEEDED"}',{status:422}),'LIMIT_EXCEEDED']]){
    response=candidate;
    await assert.rejects(adapter.instance('1',controller.signal),expectCategory(category));
  }
  controller.abort();
  await assert.rejects(adapter.instance('1',controller.signal),expectCategory('CANCELLED'));
  const login='{"actorRef":"same-user","roles":["CONSOLE_REVIEWER"]}';
  response=new Response(login);
  await session.initialize();
  const previous=session.viewerScope();
  response=new Response(login);
  await session.initialize();
  assert.notEqual(session.viewerScope(),previous);
});
test('navigation delegates exact viewer intents to existing sandbox destinations',t=>{
  const navigation=service(t,ConsoleViewerNavigation,[{provide:Router,useValue:{createUrlTree:parts=>parts}}]);
  assert.deepEqual(Array.from(navigation.destination({kind:'history',instanceId:'9007199254740993'})),['/history','9007199254740993']);
  assert.deepEqual(Array.from(navigation.destination({kind:'event',instanceId:'9007199254740993',sequence:'9007199254740995'})),['/history-event','9007199254740993','9007199254740995']);
  assert.deepEqual(Array.from(navigation.destination({kind:'task',id:'9007199254740997'})),['/task','9007199254740997']);
  assert.deepEqual(Array.from(navigation.destination({kind:'activity',id:'9007199254740999'})),['/activity','9007199254740999']);
});
