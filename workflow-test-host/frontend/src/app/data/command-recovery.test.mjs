import { test } from "node:test";
import assert from "node:assert/strict";
import {
  services,
  ConsoleApi,
  ConsoleSession,
  CommandRecovery,
  core,
  rxjs,
} from "../../../unit/component-loader.mjs";
const original = {
  kind: "complete",
  body: {
    commandId: "12345678-1234-1234-1234-123456789abc",
    instanceId: "9007199254740993",
    taskId: "9223372036854775807",
    outcomeKey: "COMPLETE",
    comment: "Original",
  },
  expectedToken: "9007199254740999",
};
function recovery(t, executeCommand) {
  const identityChanges = new rxjs.Subject();
  const session = {
    session: core.signal({ actorRef: "reviewer" }),
    identityChanges,
  };
  const injector = services(t, [
    { provide: ConsoleSession, useValue: session },
    { provide: ConsoleApi, useValue: { executeCommand } },
  ]);
  return { commands: injector.get(CommandRecovery), session };
}
test("recovery survives page replacement and retries the original envelope", async (t) => {
  const calls = [];
  const { commands } = recovery(t, async (e, replay) => {
    calls.push({ e, replay });
    return replay
      ? {
          found: true,
          result: { replayed: true, execution: { instance: { id: "1" } } },
        }
      : { replayed: false, execution: { instance: { id: "1" } } };
  });
  commands.buffer.remember(original);
  await commands.execute(false);
  const first = commands.result();
  commands.select(original.body.commandId);
  await commands.execute(true);
  assert.equal(calls[0].e, calls[1].e);
  assert.equal(calls[1].e.expectedToken, original.expectedToken);
  assert.equal(calls[1].replay, true);
  assert.equal(first.outcome, "NEW_COMMITTED_RESULT");
  assert.equal(commands.result().outcome, "REPLAY_ONLY_LOOKUP");
  assert.equal(commands.execution().instance.id, "1");
});
test("unknown outcomes retain original input; actor change clears all private recovery state", async (t) => {
  const { commands, session } = recovery(t, async () => {
    throw new Error("network");
  });
  commands.buffer.remember(original);
  await assert.rejects(commands.execute(false), /network/);
  assert.equal(commands.result().outcome, "UNKNOWN_OUTCOME");
  assert.equal(commands.buffer.command.expectedToken, original.expectedToken);
  session.identityChanges.next("other");
  assert.equal(commands.buffer.command, null);
  assert.equal(commands.observations.size, 0);
  assert.equal(commands.result(), null);
});
test("late command responses cannot repopulate buffers after logout", async (t) => {
  let resolve;
  const { commands, session } = recovery(
    t,
    () => new Promise((r) => (resolve = r)),
  );
  commands.buffer.remember(original);
  const pending = commands.execute(false);
  session.identityChanges.next(null);
  session.identityChanges.next("reviewer");
  resolve({ replayed: false, execution: {} });
  await pending;
  assert.equal(commands.result(), null);
  assert.equal(commands.observations.size, 0);
});
