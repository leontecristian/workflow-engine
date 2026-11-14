import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,component,ConsoleSession} from '../../../unit/component-loader.mjs';
const {ConsoleAdministrationApi}=loadTs(new URL('./administration-adapters.ts',import.meta.url));
test('draft transports send empty create object and bodyless DELETE with exact source tokens',async t=>{
  const calls=[];const session={async administrationCall(...args){calls.push(args);return {};}};
  const {page:api}=component(t,ConsoleAdministrationApi,{},undefined,undefined,[{provide:ConsoleSession,useValue:session}]);
  const signal=new AbortController().signal;
  await api.createDraftFromVersion('9007199254740993','9007199254740995',signal);
  await api.deleteDraft('9007199254740993','9007199254740995',signal);
  assert.equal(calls[0][0],'/versions/9007199254740993/drafts');assert.equal(calls[0][2],'POST');assert.equal(JSON.stringify(calls[0][3]),'{}');
  assert.equal(calls[1][0],'/versions/9007199254740993');assert.equal(calls[1][2],'DELETE');assert.equal(calls[1][3],undefined);
  for(const call of calls){assert.equal(call[1],signal);assert.equal(call[4],'9007199254740995');}
});
