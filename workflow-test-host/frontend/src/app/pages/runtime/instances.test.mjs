import { test } from "node:test";
import assert from "node:assert/strict";
import { loadTs, runtimeType, component, tick } from "../../../../unit/runtime-component.mjs";
const { WorkflowInstancesComponent: InstancesPage } = runtimeType("instances");
test("monitor defaults ACTIVE and retains exact cursor/filter independently", async (t) => {
  const calls = [];
  const { page, router } = component(t, InstancesPage, {
    async listInstances(...args) {
      calls.push(args);
      return {
        items: [{ id: "9007199254740993" }],
        nextAfterId: "9007199254740993",
      };
    },
  });
  page.ngOnChanges({id:{currentValue:page.id}});
  await tick();
  assert.equal(calls[0][0], "ACTIVE");
  page.nextPage();
  await tick();
  assert.equal(calls[1][1], "9007199254740993");
  page.filter = "COMPLETED";
  await page.load();
  assert.equal(calls[2][0], "COMPLETED");
  assert.equal(calls[2][1], undefined);
  await page.load();
  assert.equal(calls[3][0], "COMPLETED");
  page.open({ id: "9223372036854775807" });
  assert.equal(router.calls[0].parts[1], "9223372036854775807");
});
