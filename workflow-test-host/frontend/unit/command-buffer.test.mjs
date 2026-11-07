import "@angular/compiler";
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  CommandBuffer,
  freezeEnvelope,
  exactDecimal,
  rawDocument,
  commandRequest,
} from "../src/app/command-buffer.ts";
const command = {
  kind: "complete",
  body: {
    commandId: "12345678-1234-1234-1234-123456789abc",
    instanceId: "9007199254740993",
    taskId: "9223372036854775807",
    outcomeKey: "COMPLETE",
    comment: "original",
  },
  expectedToken: "9007199254740999",
};
test("retry envelope is detached, frozen and keeps exact original token and UUID", () => {
  const input = structuredClone(command);
  const frozen = freezeEnvelope(input);
  input.body.comment = "changed";
  assert.equal(frozen.body.comment, "original");
  assert.equal(frozen.expectedToken, command.expectedToken);
  assert.throws(() => (frozen.body.comment = "x"));
  assert.equal(frozen.body.instanceId, "9007199254740993");
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
test("lost response retry and replay-only recovery keep original UUID, input and token", () => {
  const original = freezeEnvelope(command);
  const first = commandRequest(original, false);
  const afterTransportFailure = commandRequest(original, false);
  const replay = commandRequest(original, true);
  assert.deepEqual(first, afterTransportFailure);
  assert.equal(replay.path, "/commands/complete/replay");
  assert.equal(replay.token, first.token);
  assert.equal(replay.body.commandId, first.body.commandId);
  assert.equal(replay.body.comment, "original");
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
