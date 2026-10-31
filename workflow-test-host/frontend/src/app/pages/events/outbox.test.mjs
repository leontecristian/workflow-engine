import { test } from "node:test";
import assert from "node:assert/strict";
import { loadTs, component } from "../../../../unit/component-loader.mjs";
const { OutboxPage } = loadTs(new URL("./outbox.ts", import.meta.url));
test("outbox load never dispatches; dispatch is explicit and refreshes only on success", async (t) => {
  let reads = 0,
    dispatches = 0;
  const { page } = component(t, OutboxPage, {
    async outbox() {
      reads++;
      return { items: [], nextAfterId: null };
    },
    async dispatch(limit) {
      dispatches++;
      assert.equal(limit, 50);
      return { delivered: 1, duplicates: 0 };
    },
  });
  await page.load();
  assert.equal(dispatches, 0);
  await page.dispatch();
  assert.equal(dispatches, 1);
  assert.equal(reads, 2);
  assert.match(page.notice(), /delivered/);
});
