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

test("live stage discovery keeps the historical command result and never submits a successor", async (t) => {
  const original = { instance: { id: "1", status: "ACTIVE", currentNodeKey: "PARTNER" }, task: { id: "10", status: "COMPLETED" } };
  let executions = 0;
  const service = { result: core.signal(original), execution: () => original, async execute() { executions++; } };
  const { page } = component(t, CommandsPage, {
    instance: async () => ({ id: "1", status: "ACTIVE", currentNodeKey: "PARTNER", lockVersion: "5" }),
    instanceTasks: async () => ({ items: [{ id: "10", status: "COMPLETED" }, { id: "11", status: "OPEN" }] }),
  }, undefined, undefined, [{ provide: CommandRecovery, useValue: service }]);
  await page.refreshCurrent();
  assert.equal(page.currentInstance().currentNodeKey, "PARTNER");
  assert.deepEqual(page.currentTasks().map(t => t.id), ["11"]);
  assert.equal(service.execution(), original);
  assert.equal(original.task.id, "10");
  assert.equal(executions, 0);
  page.api.instance = async () => ({ id: "1", status: "COMPLETED", currentNodeKey: "APPROVED" });
  page.api.instanceTasks = async () => ({ items: [{ id: "10", status: "COMPLETED" }, { id: "11", status: "COMPLETED" }] });
  await page.refreshCurrent();
  assert.equal(page.currentInstance().status, "COMPLETED");
  assert.equal(original.instance.status, "ACTIVE");
  assert.deepEqual(page.currentTasks(), []);
});

test("a delayed live read cannot replace a newly selected command's current work", async (t) => {
  let execution = { instance: { id: "1" } }, release;
  const delayed = new Promise(resolve => { release = resolve; });
  const service = { result: core.signal(null), execution: () => execution };
  const { page } = component(t, CommandsPage, {
    instance: async id => id === "1" ? delayed : { id: "2" },
    instanceTasks: async id => ({ items: [{ id: id + "0", status: "OPEN" }] }),
  }, undefined, undefined, [{ provide: CommandRecovery, useValue: service }]);
  const old = page.refreshCurrent();
  execution = { instance: { id: "2" } };
  await page.refreshCurrent();
  release({ id: "1" }); await old;
  assert.equal(page.currentInstance().id, "2");
  assert.equal(page.currentTasks()[0].id, "20");
});
