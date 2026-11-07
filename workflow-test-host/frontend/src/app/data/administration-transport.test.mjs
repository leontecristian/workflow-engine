import { test } from 'node:test';
import assert from 'node:assert/strict';
import { services, ConsoleSession } from '../../../unit/component-loader.mjs';
const identity = {actorRef:'admin',roles:['CONSOLE_ADMIN'],csrfHeaderName:'X-CSRF-TOKEN',csrfToken:'trusted-token'};
test('administration sends exact raw text, original token and CSRF; exports remain text', async t => {
  const calls=[];
  const source=' {"n":9007199254740993,"d":1.00} ';
  t.mock.method(globalThis,'fetch',async(url,options)=>{calls.push({url,options});return {ok:true,status:200,text:async()=>url.endsWith('/export')?source:'{"changed":true}'};});
  const session=services(t).get(ConsoleSession);session.session.set(identity);session.viewerScope.set({});
  const signal=new AbortController().signal;
  await session.administrationCall('/versions/42/document',signal,'PUT',source,'9007199254740999',true);
  assert.equal(calls[0].options.body,source);
  assert.equal(calls[0].options.headers['X-Expected-Lock-Version'],'9007199254740999');
  assert.equal(calls[0].options.headers['X-CSRF-TOKEN'],'trusted-token');
  assert.equal(calls[0].options.signal,signal);
  assert.equal(await session.administrationCall('/versions/42/export',signal,'GET',undefined,undefined,false,true),source);
});
test('administration preserves denied/stale status and diagnostics without command retry advice',async t=>{
  let status=403;
  t.mock.method(globalThis,'fetch',async()=>({ok:false,status,text:async()=>JSON.stringify({code:'STALE_VERSION',diagnostics:[{path:'$.version'}],correlationId:'trusted-correlation'})}));
  const session=services(t).get(ConsoleSession);
  const signal=new AbortController().signal;
  for(status of [403,409]) await assert.rejects(session.administrationCall('/definitions',signal),error=>error.httpStatus===status&&error.diagnostics.length===1&&error.correlationId==='trusted-correlation');
});
test('uncertain administration write retains safe inspect-state guidance',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('network');});
  const session=services(t).get(ConsoleSession);
  await assert.rejects(session.administrationCall('/versions/42/validate',new AbortController().signal,'POST'),error=>
    error.message.includes('outcome unknown')&&!/Find result|Retry/.test(error.message));
});
test('late unauthorized response cannot clear a replacement session',async t=>{
  let respond;
  t.mock.method(globalThis,'fetch',()=>new Promise(resolve=>respond=resolve));
  const session=services(t).get(ConsoleSession);session.session.set(identity);session.viewerScope.set({});
  const pending=session.administrationCall('/definitions',new AbortController().signal);
  const replacement={};session.viewerScope.set(replacement);
  respond({ok:false,status:401,text:async()=>'{}'});
  await assert.rejects(pending);
  assert.equal(session.viewerScope(),replacement);
  assert.equal(session.session(),identity);
});

test('Definitions page requests newest families first on every page with exact descending cursors',async t=>{
 const {loadTs}=await import('../../../unit/component-loader.mjs');
 const {ConsoleAdministrationApi}=loadTs(new URL('./administration-adapters.ts',import.meta.url));
 const calls=[];
 t.mock.method(globalThis,'fetch',async(url)=>{calls.push(url);return {ok:true,status:200,text:async()=>'{"items":[],"nextAfterId":null}'};});
 const injector=services(t,[ConsoleAdministrationApi]),api=injector.get(ConsoleAdministrationApi);
 const signal=new AbortController().signal;
 await api.listDefinitions(undefined,2,signal);await api.listDefinitions('9007199254740993',2,signal);
 assert.equal(calls[0],'/console/api/definitions?limit=2&order=DESC');
 assert.equal(calls[1],'/console/api/definitions?limit=2&order=DESC&afterId=9007199254740993');
});
