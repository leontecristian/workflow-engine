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
  page.detail.set({ instanceId: "9007199254740993" });
  page.contract.set({ instanceLockVersion: "9007199254740999" });
  page.outcome = "COMPLETE";
  page.comment = "Original";
  await page.complete();
  const original = buffer.command;
  page.comment = "Edited";
  page.contract.set({ instanceLockVersion: "100" });
  assert.equal(original.body.taskId, "9223372036854775807");
  assert.equal(original.body.comment, "Original");
  assert.equal(original.expectedToken, "9007199254740999");
  assert.ok(Object.isFrozen(original));
  assert.match(page.error(), /Transport outcome unknown/);
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
