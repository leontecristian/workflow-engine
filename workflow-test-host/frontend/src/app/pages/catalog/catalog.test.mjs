import { test } from "node:test";
import assert from "node:assert/strict";
import { loadTs, component } from "../../../../unit/administration-component.mjs";
const { WorkflowCatalogComponent: CatalogPage } = loadTs(new URL("../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/tasktype/catalog.ts", import.meta.url));
test("consecutive catalog edits use the latest committed version", async (t) => {
  let stored = {
    id: "9007199254740993",
    lockVersion: "0",
    name: "Original",
    code: "REVIEW",
    selectable: true,
  };
  const tokens = [];
  const { page } = component(t, CatalogPage, {
    async updateTaskType(id, body, token) {
      tokens.push(token);
      if (token !== stored.lockVersion) throw new Error("409 STALE_VERSION");
      stored = { ...stored, ...body, lockVersion: String(BigInt(token) + 1n) };
      return { changed: true, after: { ...stored } };
    },
    async listTaskTypes() {
      return { items: [{ ...stored }], nextAfterId: null };
    },
  });
  page.open({ ...stored });
  page.name = "First edit";
  await page.saveCatalog();
  page.name = "Second edit";
  await page.saveCatalog();
  assert.deepEqual(tokens, ["0", "1"]);
  assert.equal(page.error(), "");
  assert.equal(stored.name, "Second edit");
  assert.equal(page.editing.lockVersion, "2");
});
test("rejected catalog save retains token, selection and input without reloading", async (t) => {
  let reads = 0;
  const { page } = component(t, CatalogPage, {
    async updateTaskType() {
      throw new Error("409 STALE_VERSION");
    },
    async listTaskTypes() {
      reads++;
    },
  });
  page.editing = { id: "1", lockVersion: "4" };
  page.name = "Unsaved edit";
  page.description = "Keep this";
  await page.saveCatalog();
  assert.match(page.error(), /409 STALE_VERSION/);
  assert.equal(page.editing.lockVersion, "4");
  assert.equal(page.name, "Unsaved edit");
  assert.equal(page.description, "Keep this");
  assert.equal(reads, 0);
});
test("catalog creation and edit state belong to each routed component", async (t) => {
  const a = component(t, CatalogPage).page,
    b = component(t, CatalogPage).page;
  a.name = "Only A";
  a.filter = "SELECTABLE";
  a.editing = { id: "1", lockVersion: "2" };
  a.error.set("Conflict");
  assert.equal(b.name, "");
  assert.equal(b.filter, "ALL");
  assert.equal(b.editing, null);
  assert.equal(b.error(), "");
});
