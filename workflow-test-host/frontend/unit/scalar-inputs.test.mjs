import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CommandBuffer, parseScalarInputs, commandRequest } from '../src/app/command-buffer.ts';
const body = {commandId:'12345678-1234-1234-1234-123456789abc',definitionVersionId:'42',requestId:'1',expectedRequestVersion:'0'};
test('typed start input recovery freezes exact text, null and omissions', () => {
  const inputs={quantity:'9007199254740993', decimal:'12.50', timestamp:'2026-01-01T02:00:00+02:00',note:null, enabled:true};
  const buffer=new CommandBuffer(); buffer.remember({kind:'start',body:{...body,inputs},expectedToken:null});
  inputs.quantity='1';
  assert.equal(buffer.command.body.inputs.quantity,'9007199254740993');
  assert.throws(() => buffer.command.body.inputs.quantity='2');
  const restored=new CommandBuffer(); restored.restore(buffer.export());
  assert.deepEqual(restored.command,buffer.command);
  assert.equal(commandRequest(restored.command,true).path,'/commands/start/replay');
  assert.equal(commandRequest(restored.command,false).body.inputs.decimal,'12.50');
  assert.equal(Object.hasOwn(restored.command.body.inputs,'missing'),false);
  assert.throws(() => restored.remember({kind:'start',body:{...body,inputs:{...inputs,quantity:'9007199254740992'}},expectedToken:null}));
  restored.setActor('other'); assert.equal(restored.command,null);
});
test('scalar input editor rejects numeric tokens, nesting, duplicates and invalid keys', () => {
  assert.equal(parseScalarInputs('{"quantity":"9007199254740993"}').quantity,'9007199254740993');
  for(const input of ['null','[]','{"n":9007199254740993}','{"n":{}}','{"n":[]}','{"n":"1","n":"2"}','{"n":"1","\\u006e":"2"}','{"bad/key":null}'])
    assert.throws(() => parseScalarInputs(input),input);
});
test('legacy envelopes and empty inputs share identity; absent and explicit null conflict', () => {
  const buffer=new CommandBuffer(); buffer.remember({kind:'start',body,expectedToken:null});
  buffer.remember({kind:'start',body:{...body,inputs:{}},expectedToken:null});
  assert.throws(() => buffer.remember({kind:'start',body:{...body,inputs:{note:null}},expectedToken:null}));
  assert.throws(() => buffer.restore('{"kind":"start","body":{"commandId":"'+body.commandId+'","definitionVersionId":"42","requestId":"1","expectedRequestVersion":"0","inputs":{"n":"1","n":"2"}},"expectedToken":null}'));
});
