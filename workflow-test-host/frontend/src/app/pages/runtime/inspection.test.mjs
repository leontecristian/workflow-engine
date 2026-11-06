import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  routeAt,
  tick,
} from "../../../../unit/component-loader.mjs";
const { HistoryPage } = loadTs(new URL("./history.ts", import.meta.url));
const { SubmissionsPage } = loadTs(
  new URL("./submissions.ts", import.meta.url),
);
const { HistoryEventPage } = loadTs(
  new URL("./history-event.ts", import.meta.url),
);
test("history uses sequence cursors and exact route parameters", async (t) => {
  const calls = [];
  const { page, router } = component(
    t,
    HistoryPage,
    {
      async history(...args) {
        calls.push(args);
        return {
          items: [{ historySequence: "9007199254740995" }],
          nextAfterSequence: "9007199254740995",
        };
      },
    },
    routeAt({ id: "9007199254740993" }),
  );
  page.ngOnInit();
  await tick();
  page.nextPage();
  await tick();
  assert.equal(calls[1][0], "9007199254740993");
  assert.equal(calls[1][1], "9007199254740995");
  page.open(page.rows()[0]);
  assert.deepEqual(router.calls[0].parts, [
    "/history-event",
    "9007199254740993",
    "9007199254740995",
  ]);
});
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
test("exact history evidence reads both Angular parameters without splitting URLs", async (t) => {
  let received;
  const { page } = component(
    t,
    HistoryEventPage,
    {
      async historyEvent(...args) {
        received = args;
        return {
          summary: { historySequence: args[1] },
          detailsJson: '{"n":900719925474099312345}',
        };
      },
    },
    routeAt({ id: "9007199254740993", sequence: "9007199254740995" }),
  );
  page.ngOnInit();
  await tick();
  assert.deepEqual(received, ["9007199254740993", "9007199254740995"]);
  assert.equal(page.detail().summary.historySequence, "9007199254740995");
  assert.equal(page.detail().detailsJson, '{"n":900719925474099312345}');
});
