import '@angular/compiler';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { WORKFLOW_ADMINISTRATION_API } from '@workflow-engine/ui';
import { provideWorkflowAdministrationHttp } from '@workflow-engine/ui/http';
import { services, ConsoleSession, consoleWorkflowProviders, workflowVersionSummary } from '../../../unit/component-loader.mjs';

const identity = {actorRef:'admin',roles:['CONSOLE_ADMIN'],csrfHeaderName:'X-CSRF-TOKEN',csrfToken:'trusted-token'};
function administration(t) {
  const injector = services(t, [...consoleWorkflowProviders(), provideWorkflowAdministrationHttp('/console/api')]);
  const session = injector.get(ConsoleSession);
  session.session.set(identity);
  session.viewerScope.set({});
  return { api: injector.get(WORKFLOW_ADMINISTRATION_API), session };
}

test('administration sends exact raw text, original token and CSRF; exports remain text', async t => {
  const calls=[];
  const source=' {"n":9007199254740993,"d":1.00} ';
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    calls.push({url,options});
    const change = { changed: true, after: workflowVersionSummary('42', '9007199254741000') };
    return new Response(url.endsWith('/export') ? source : JSON.stringify(change));
  });
  const {api}=administration(t);
  const signal=new AbortController().signal;
  await api.saveDocument('42',source,'9007199254740999',signal);
  assert.equal(calls[0].options.body,source);
  const headers = new Headers(calls[0].options.headers);
  assert.equal(headers.get('X-Expected-Lock-Version'),'9007199254740999');
  assert.equal(headers.get('X-CSRF-TOKEN'),'trusted-token');
  assert.equal(headers.get('Content-Type'),'application/json');
  assert.equal(await api.exportVersion('42',signal),source);
});
test('aborting administration unsubscribes the HTTP request',async t=>{
  let requestSignal;
  t.mock.method(globalThis,'fetch',(_url,options)=>new Promise((_resolve,reject)=>{
    requestSignal=options.signal;
    requestSignal.addEventListener('abort',()=>reject(requestSignal.reason),{once:true});
  }));
  const {api}=administration(t);
  const controller=new AbortController();
  const pending=api.listDefinitions(undefined,20,controller.signal);
  assert.equal(requestSignal.aborted,false);
  controller.abort();
  await assert.rejects(pending,error=>error.name==='AbortError');
  assert.equal(requestSignal.aborted,true);
});
test('administration preserves denied/stale status and diagnostics without command retry advice',async t=>{
  let status=403;
  t.mock.method(globalThis,'fetch',async()=>new Response(JSON.stringify({code:'STALE_VERSION',diagnostics:[{path:'$.version'}],correlationId:'trusted-correlation'}),{status}));
  const {api}=administration(t);
  const signal=new AbortController().signal;
  for(status of [403,409]) await assert.rejects(api.listDefinitions(undefined,20,signal),error=>
    error.httpStatus===status&&error.code==='STALE_VERSION'&&error.diagnostics.length===1&&error.correlationId==='trusted-correlation'
      && !/Find result|Retry/.test(error.message));
});
test('uncertain administration write retains safe inspect-state guidance',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('network');});
  const {api}=administration(t);
  await assert.rejects(api.checkVersion('42','validate','0',new AbortController().signal),error=>
    error.message.includes('outcome unknown')&&error.message.includes('inspect stored state')&&!/Find result|Retry/.test(error.message));
});
test('late unauthorized response cannot clear a replacement session',async t=>{
  let respond;
  t.mock.method(globalThis,'fetch',()=>new Promise(resolve=>respond=resolve));
  const {api,session}=administration(t);
  const pending=api.listDefinitions(undefined,20,new AbortController().signal);
  const replacement={};session.viewerScope.set(replacement);
  respond(new Response('{}',{status:401}));
  await assert.rejects(pending,error=>error.httpStatus===401);
  assert.equal(session.viewerScope(),replacement);
  assert.equal(session.session(),identity);
});

test('Definitions page requests newest families first on every page with exact descending cursors',async t=>{
 const calls=[];
 t.mock.method(globalThis,'fetch',async(url)=>{calls.push(url);return new Response('{"items":[],"nextAfterId":null}');});
 const {api}=administration(t);
 const signal=new AbortController().signal;
 await api.listDefinitions(undefined,2,signal);await api.listDefinitions('9007199254740993',2,signal);
 assert.equal(calls[0],'/console/api/definitions?limit=2&order=DESC');
 assert.equal(calls[1],'/console/api/definitions?limit=2&order=DESC&afterId=9007199254740993');
});
