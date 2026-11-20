import '@angular/compiler';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import { WORKFLOW_ADMINISTRATION_API } from '@workflow-engine/ui';
import { provideWorkflowAdministrationHttp } from '@workflow-engine/ui/http';
import {services,consoleWorkflowProviders,workflowVersionSummary} from '../../../unit/component-loader.mjs';

test('draft transports send empty create object and bodyless DELETE with exact source tokens',async t=>{
  const calls=[];
  const id='9007199254740993', token='9007199254740995';
  const change={changed:true,after:workflowVersionSummary('9007199254740994','0')};
  const deletion={operation:'DELETE_DRAFT',before:workflowVersionSummary(id,token)};
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    calls.push({url,options});
    return new Response(JSON.stringify(options.method==='POST' ? change : deletion));
  });
  const injector=services(t,[...consoleWorkflowProviders(),provideWorkflowAdministrationHttp('/console/api')]);
  const api=injector.get(WORKFLOW_ADMINISTRATION_API);
  const controller=new AbortController();
  assert.deepEqual(await api.createDraftFromVersion(id,token,controller.signal),change);
  assert.deepEqual(await api.deleteDraft(id,token,controller.signal),deletion);
  assert.equal(calls[0].url,'/console/api/versions/'+id+'/drafts');
  assert.equal(calls[0].options.method,'POST');
  assert.equal(calls[0].options.body,'{}');
  assert.equal(calls[1].url,'/console/api/versions/'+id);
  assert.equal(calls[1].options.method,'DELETE');
  assert.equal(calls[1].options.body ?? undefined,undefined);
  for(const {options} of calls) assert.equal(new Headers(options.headers).get('X-Expected-Lock-Version'),token);

  controller.abort();
  await assert.rejects(api.createDraftFromVersion(id,token,controller.signal),error=>error.name==='AbortError');
  await assert.rejects(api.deleteDraft(id,token,controller.signal),error=>error.name==='AbortError');
  assert.equal(calls.length,2);
});
