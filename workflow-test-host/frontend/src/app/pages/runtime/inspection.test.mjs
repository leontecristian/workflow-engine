import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  routeAt,
  tick,
} from "../../../../unit/component-loader.mjs";
const { SubmissionsPage } = loadTs(
  new URL("./submissions.ts", import.meta.url),
);
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
  page.ngOnInit();
  await tick();
  page.nextPage();
  await tick();
  assert.equal(calls[1][1], "9007199254740997");
  page.open(page.rows()[0]);
  assert.deepEqual(router.calls[0].parts, ["/task", "9007199254740997"]);
});
