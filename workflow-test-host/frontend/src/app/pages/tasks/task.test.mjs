import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  CommandRecovery,
  routeAt,
  router,
  tick,
} from "../../../../unit/component-loader.mjs";
const { TaskPage } = loadTs(new URL("./task.ts", import.meta.url));
const { CommandBuffer } = loadTs(
  new URL("../../command-buffer.ts", import.meta.url),
);

test("a destroyed task view ignores a pending read through the Angular destruction lifecycle", async (t) => {
  let resolveTask;
  const task = new Promise(resolve => { resolveTask = resolve; });
  const { page, injector } = component(t, TaskPage, {
    task: () => task,
    taskContract: async () => ({ outcomes: [{ key: "APPROVE" }] }),
    taskSubmission: async () => null,
  }, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: {} },
  ]);
  page.id = "9007199254740993";
  const pending = page.load();
  injector.destroy();
  resolveTask({ id: page.id, status: "OPEN" });
  await pending;
  assert.equal(page.detail(), null);
  assert.equal(page.contract(), null);
});
test("completion captures original instance token and assignment-specific task IDs", async (t) => {
  const buffer = new CommandBuffer();
  const commands = {
    buffer,
    async execute() {
      throw new Error("Transport outcome unknown");
    },
  };
  const { page } = component(t, TaskPage, {}, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: commands },
  ]);
  page.id = "9223372036854775807";
  page.detail.set({ instanceId: "9007199254740993", status: "OPEN" });
  page.contract.set({ instanceLockVersion: "9007199254740999", outcomes: [{key:"COMPLETE", label:"Complete", requiresComment:false}] });
  page.outcome = "COMPLETE";
  page.comment = "Original";
  await page.complete("COMPLETE");
  const original = buffer.command;
  page.comment = "Edited";
  page.contract.set({ instanceLockVersion: "100" });
  assert.equal(original.body.taskId, "9223372036854775807");
  assert.equal(original.body.comment, "Original");
  assert.equal(original.body.outcomeKey, "COMPLETE");
  assert.equal(original.expectedToken, "9007199254740999");
  assert.ok(Object.isFrozen(original));
  assert.match(page.error(), /Transport outcome unknown/);
});
test("outcome actions require their own comments, explicit keys and an open task", async (t) => {
  const buffer = new CommandBuffer();
  let calls = 0;
  const commands = { buffer, async execute() { calls++; throw new Error("Unknown transport outcome"); } };
  const { page } = component(t, TaskPage, {}, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: commands },
  ]);
  page.id = "3";
  page.detail.set({ instanceId: "1", status: "OPEN" });
  page.contract.set({ instanceLockVersion: "2", outcomes: [
    { key: "REJECT", label: "Reject", requiresComment: true },
    { key: "APPROVE", label: "Approve", requiresComment: false },
  ] });
  page.comment = " \t\n";
  await page.complete("REJECT");
  assert.equal(calls, 0);
  assert.match(page.error(), /comment is required/);
  await page.complete("unknown");
  assert.equal(calls, 0);
  page.comment = "  Exact reason\n";
  await page.complete("REJECT");
  assert.equal(calls, 1);
  const original = buffer.command;
  assert.equal(original.body.outcomeKey, "REJECT");
  assert.equal(original.body.comment, "  Exact reason\n");
  page.comment = "";
  await page.complete("APPROVE");
  assert.equal(calls, 2);
  assert.equal(buffer.command.body.outcomeKey, "APPROVE");
  assert.equal(buffer.command.body.comment, null);
  assert.equal(buffer.entries[0], original);
  page.detail.set({ instanceId: "1", status: "COMPLETED" });
  await page.complete("APPROVE");
  assert.equal(calls, 2);
});
test("pending completion freezes one outcome and blocks other action clicks", async (t) => {
  const buffer = new CommandBuffer();
  let release;
  const commands = { buffer, execute: () => new Promise((resolve) => { release = resolve; }) };
  const { page } = component(t, TaskPage, {}, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: commands },
  ]);
  page.id = "3";
  page.detail.set({ instanceId: "1", status: "OPEN" });
  page.contract.set({ instanceLockVersion: "2", outcomes: [
    { key: "APPROVE", label: "Approve", requiresComment: false },
    { key: "REJECT", label: "Reject", requiresComment: true },
  ] });
  const pending = page.complete("APPROVE");
  page.comment = "Changed while pending";
  await page.complete("REJECT");
  assert.equal(buffer.entries.length, 1);
  assert.equal(buffer.command.body.outcomeKey, "APPROVE");
  assert.equal(buffer.command.body.comment, null);
  release();
  await pending;
});
test("reused task route resets completion input and destroyed pages ignore later parameters", async (t) => {
  const route = routeAt({ id: "1" });
  let reads = 0;
  const api = {
    async task(id) {
      reads++;
      return { id };
    },
    async taskContract() {
      return { instanceLockVersion: "1" };
    },
    async taskSubmission() {
      return null;
    },
  };
  const { page, injector } = component(t, TaskPage, api, route, undefined, [
    { provide: CommandRecovery, useValue: { result: () => null } },
  ]);
  page.ngOnInit();
  await tick();
  page.outcome = "OLD";
  page.comment = "Prior task";
  route.paramMap.next(router.convertToParamMap({ id: "2" }));
  await tick();
  assert.equal(page.outcome, "");
  assert.equal(page.comment, "");
  assert.equal(page.detail().id, "2");
  injector.destroy();
  reads = 0;
  route.paramMap.next(router.convertToParamMap({ id: "3" }));
  await tick();
  assert.equal(reads, 0);
});
