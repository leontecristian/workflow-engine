import "@angular/compiler";
import {CommandBuffer} from '@workflow-engine/ui';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { freezeStartBody, parseScalarInputs, commandRequest } from '../src/app/command-buffer.ts';
const body = {commandId:'12345678-1234-1234-1234-123456789abc',definitionVersionId:'42',requestId:'1',expectedRequestVersion:'0'};
test('raw JSON inputs survive export, restoration, retry and transport without numeric conversion', () => {
  const inputs='{"booking":{"amount":9007199254740993.00,"exponent":1E+2},"scalar":"12.50","optional":null}';
  const buffer=new CommandBuffer({freezeBody:freezeStartBody}); buffer.remember({kind:'start',body:{...body,inputs:parseScalarInputs(inputs)},expectedToken:null});
  const restored=new CommandBuffer({freezeBody:freezeStartBody}); restored.restore(buffer.export());
  assert.deepEqual(restored.command,buffer.command);
  const request=commandRequest(restored.command,false);
  assert.equal(typeof request.body,'string');
  assert.ok(request.body.includes(inputs));
  assert.equal(commandRequest(restored.command,true).body,request.body);
  assert.equal(commandRequest(restored.command,true).path,'/commands/start/replay');
  assert.throws(() => restored.remember({kind:'start',body:{...body,inputs:inputs.replace('.00','.0')},expectedToken:null}));
});
test('raw input editor accepts nested values and rejects malformed objects and duplicates', () => {
  const text='{"booking":{"array":[1.00,null,{}]},"n":"9007199254740993"}';
  assert.equal(parseScalarInputs(text),text);
  for(const input of ['null','[]','{"n":"1","n":"2"}','{"n":"1","\\u006e":"2"}','{"booking":{"a":1,"a":2}}','{"bad/key":null}'])
    assert.throws(() => parseScalarInputs(input),input);
});
test('empty start requests still send an input object', () => {
  const buffer=new CommandBuffer({freezeBody:freezeStartBody}); buffer.remember({kind:'start',body,expectedToken:null});
  assert.ok(commandRequest(buffer.command,false).body.endsWith(',"inputs":{}}'));
});
