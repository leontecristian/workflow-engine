import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  runtimeType,
  component,
  routeAt,
  tick,
} from "../../../../unit/runtime-component.mjs";
const { WorkflowSubmissionsComponent: SubmissionsPage } = runtimeType("submissions");
test("submissions use task cursors and link to exact task instead of activity", async (t) => {
  const calls = [];
  const { page, router } = component(
    t,
    SubmissionsPage,
    {
      async submissions(...args) {
        calls.push(args);
        return {
          items: [{ taskId: "9007199254740997" }],
          nextAfterTaskId: "9007199254740997",
        };
      },
    },
    routeAt({ id: "9007199254740993" }),
  );
  page.ngOnChanges({id:{currentValue:page.id}});
  await tick();
  page.nextPage();
  await tick();
  assert.equal(calls[1][1], "9007199254740997");
  page.open(page.rows()[0]);
  assert.deepEqual(router.calls[0].parts, ["/task", "9007199254740997"]);
});
