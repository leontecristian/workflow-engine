import "@angular/compiler";
import {CommandBuffer,freezeEnvelope,WORKFLOW_RUNTIME_COMMANDS} from '@workflow-engine/ui';
import {provideWorkflowRuntimeCommandsHttp} from '@workflow-engine/ui/http';
import {consoleWorkflowProviders,services} from './component-loader.mjs';
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  exactDecimal,
  rawDocument,
} from "../src/app/command-buffer.ts";
const command = {
  kind: "complete",
  body: {
    commandId: "12345678-1234-1234-1234-123456789abc",
    taskId: "9223372036854775807",
    outcomeKey: "COMPLETE",
    comment: "original",
  },
  expectedToken: "opaque-task-version",
};
test("retry envelope is detached, frozen and keeps exact original token and UUID", () => {
  const input = structuredClone(command);
  const frozen = freezeEnvelope(input);
  input.body.comment = "changed";
  assert.equal(frozen.body.comment, "original");
  assert.equal(frozen.expectedToken, command.expectedToken);
  assert.throws(() => (frozen.body.comment = "x"));
  assert.equal(frozen.body.taskId, "9223372036854775807");
  assert.ok(!Object.hasOwn(frozen.body, "instanceId"));
});
test("full-reload export and restore preserve the original request", () => {
  const buffer = new CommandBuffer();
  buffer.setActor("reviewer");
  buffer.remember(command);
  const recovered = new CommandBuffer();
  recovered.setActor("reviewer");
  assert.deepEqual(recovered.restore(buffer.export()), buffer.command);
});
test("account changes clear private command input; same actor initialization retains it", () => {
  const b = new CommandBuffer();
  b.setActor("reviewer");
  b.remember(command);
  b.setActor("reviewer");
  assert.ok(b.command);
  b.setActor("admin");
  assert.equal(b.command, null);
  b.remember(command);
  b.setActor(null);
  assert.equal(b.command, null);
});
test("identifiers reject floating point, exponent, overflow and leading zeros", () => {
  for (const value of ["01", "1e3", "1.0", "-1", "9223372036854775808"])
    assert.throws(() => exactDecimal(value));
  assert.equal(exactDecimal("9223372036854775807"), "9223372036854775807");
});
test("raw authoring text keeps whitespace and integers beyond JavaScript precision", () => {
  const value =
    ' { "designer": {"large":900719925474099312345}, "name":"a" }\n';
  assert.equal(rawDocument(value), value);
});
test("pasted commands cannot introduce arbitrary fields or endpoints", () => {
  assert.throws(() => freezeEnvelope({ ...command, path: "/admin" }));
  assert.throws(() =>
    freezeEnvelope({
      ...command,
      body: { ...command.body, actorRef: "admin" },
    }),
  );
  assert.throws(() => freezeEnvelope({ ...command, expectedToken: 1 }));
});
function runtimeCommands(t, respond) {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({path, body: JSON.parse(options.body), token: new Headers(options.headers).get('X-Task-Precondition')});
    return new Response(JSON.stringify(respond(calls.length, path)));
  });
  const commands = services(t, [
    ...consoleWorkflowProviders(), provideWorkflowRuntimeCommandsHttp('/console/api'),
  ]).get(WORKFLOW_RUNTIME_COMMANDS);
  return { calls, commands };
}

const taskResult = {replayed:false,status:'ACTIVE',taskStatus:'OPEN'};

test("lost response retry and replay-only recovery keep original UUID, input and token", async t => {
  const original = freezeEnvelope(command);
  const signal = new AbortController().signal;
  const { calls, commands } = runtimeCommands(t, (count, path) => {
    if (count === 1) throw new Error('Connection lost');
    return path.endsWith('/replay') ? {found:true,result:taskResult} : taskResult;
  });
  await assert.rejects(commands.complete(original, signal), /outcome unknown/);
  assert.equal(calls.length, 1);
  await commands.complete(original, signal);
  await commands.replayComplete(original, signal);
  const [first, afterTransportFailure, replay] = calls;
  assert.deepEqual(first, afterTransportFailure);
  assert.equal(first.path, '/console/api/tasks/9223372036854775807/complete');
  assert.equal(replay.path, "/console/api/commands/complete/replay");
  assert.equal(replay.token, first.token);
  assert.equal(replay.body.commandId, first.body.commandId);
  assert.equal(replay.body.comment, "original");
  assert.equal(replay.body.taskId, original.body.taskId);
});
test("new actions retain earlier commands and conflicting pasted UUIDs cannot overwrite them", () => {
  const b = new CommandBuffer();
  b.setActor("reviewer");
  b.remember(command);
  const second = {
    ...command,
    body: {
      ...command.body,
      commandId: "12345678-1234-1234-1234-123456789abd",
    },
  };
  b.remember(second);
  assert.equal(b.entries.length, 2);
  b.select(command.body.commandId);
  assert.deepEqual(b.command, freezeEnvelope(command));
  assert.throws(() =>
    b.restore(
      JSON.stringify({
        ...command,
        body: { ...command.body, comment: "modified" },
      }),
    ),
  );
  b.setActor(null);
  assert.equal(b.entries.length, 0);
});

test('ownership envelopes preserve exact inputs and map mutation and replay routes', async t => {
 const { calls, commands } = runtimeCommands(t, (_count,path)=>path.endsWith('/replay')?{found:false,result:null}:taskResult);
 const signal = new AbortController().signal;
 for(const kind of ['claim','release','admin-release']) {
  const body={commandId:command.body.commandId,taskId:'3',...(kind==='admin-release'?{reason:'  exact reason  '}: {})};
  const envelope=freezeEnvelope({kind,body,expectedToken:'8'});
  await commands.ownership(envelope,false,signal);
  await commands.ownership(envelope,true,signal);
  const [mutation,replay]=calls.splice(0);
  assert.equal(mutation.path,`/console/api/tasks/3/${kind}`);
  assert.deepEqual(mutation.body,kind==='admin-release'?{commandId:body.commandId,reason:body.reason}:{commandId:body.commandId});
  assert.deepEqual(replay.body,body);
  assert.equal(replay.path,`/console/api/commands/${kind}/replay`);
 }
 assert.throws(()=>freezeEnvelope({kind:'admin-release',body:{commandId:command.body.commandId,taskId:'2',reason:' '},expectedToken:'0'}));
});
