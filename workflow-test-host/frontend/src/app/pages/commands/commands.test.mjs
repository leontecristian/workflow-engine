import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  CommandRecovery,
  core,
} from "../../../../unit/component-loader.mjs";
const { CommandsPage } = loadTs(new URL("./commands.ts", import.meta.url));
test("command page retries/replays explicitly without replacing command state", async (t) => {
  const calls = [];
  const result = core.signal(null);
  const service = {
    result,
    async execute(replay) {
      calls.push(replay);
    },
    execution() {
      return null;
    },
  };
  const a = component(t, CommandsPage, {}, undefined, undefined, [
    { provide: CommandRecovery, useValue: service },
  ]);
  assert.equal(calls.length, 0);
  await a.page.retry(true);
  await a.page.retry(false);
  assert.deepEqual(calls, [true, false]);
  a.injector.destroy();
  const b = component(t, CommandsPage, {}, undefined, undefined, [
    { provide: CommandRecovery, useValue: service },
  ]);
  assert.equal(b.page.result, result);
  assert.equal(b.page.envelope, "");
});
test("restore errors keep the recovery text available for correction", (t) => {
  const { page } = component(t, CommandsPage, {}, undefined, undefined, [
    {
      provide: CommandRecovery,
      useValue: {
        result: core.signal(null),
        restore() {
          throw new Error("Invalid command envelope");
        },
      },
    },
  ]);
  page.envelope = "original input";
  page.restoreEnvelope();
  assert.equal(page.envelope, "original input");
  assert.match(page.error(), /Invalid command envelope/);
});
