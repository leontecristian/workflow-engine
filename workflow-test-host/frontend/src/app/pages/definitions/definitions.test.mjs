import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  routeAt,
  router,
  tick,
} from "../../../../unit/component-loader.mjs";
const { DefinitionsPage } = loadTs(
  new URL("./definitions.ts", import.meta.url),
);
const { FamilyPage } = loadTs(new URL("./family.ts", import.meta.url));
const { VersionPage } = loadTs(new URL("./version.ts", import.meta.url));
test("import passes duplicate properties and exact numbers as unchanged raw text", async (t) => {
  let received;
  const { page, router } = component(t, DefinitionsPage, {
    async importDraft(text) {
      received = text;
      return { after: { id: "9007199254740993" }, changed: true };
    },
  });
  page.document = ' {"x":1,"x":900719925474099312345} ';
  await page.importDraft();
  assert.equal(received, page.document);
  assert.equal(router.calls[0].parts[1], "9007199254740993");
});
test("version conflict preserves unsaved raw text, dirty state and original token", async (t) => {
  const { page } = component(t, VersionPage, {
    async saveDocument() {
      throw new Error("409 STALE_VERSION");
    },
  });
  page.id = "1";
  page.detail.set({
    summary: { lockVersion: "7" },
    validationDetailsJson: "old",
  });
  page.document = '{"n":900719925474099312345}';
  page.dirty = true;
  await page.saveDocument();
  assert.equal(page.dirty, true);
  assert.equal(page.detail().summary.lockVersion, "7");
  assert.equal(page.document, '{"n":900719925474099312345}');
  assert.match(page.error(), /STALE_VERSION/);
});
test("successful draft save adopts token and clears stale validation without parsing document", async (t) => {
  let input;
  const { page } = component(t, VersionPage, {
    async saveDocument(id, text, token) {
      input = { id, text, token };
      return { changed: true, after: { lockVersion: "9" } };
    },
  });
  page.id = "2";
  page.detail.set({
    summary: { lockVersion: "7" },
    validationDetailsJson: '{"report":{"valid":true}}',
  });
  page.document = '{"x":1,"x":2}';
  page.dirty = true;
  await page.saveDocument();
  assert.equal(input.text, page.document);
  assert.equal(input.token, "7");
  assert.equal(page.detail().summary.lockVersion, "9");
  assert.equal(page.validationReport(), null);
  assert.equal(page.dirty, false);
});
test("version load and reload display formatted JSON without unsaved changes", async (t) => {
  const stored = '{"key":"review","version":9007199254740993}';
  const { page } = component(t, VersionPage, {
    async version() {
      return { summary: { status: "DRAFT", lockVersion: "7" }, documentJson: stored };
    },
  });
  page.id = "9";
  await page.load();
  assert.equal(page.document, '{\n  "key": "review",\n  "version": 9007199254740993\n}');
  assert.equal(page.dirty, false);
  assert.equal(page.detail().documentJson, stored);
  page.document = "unsaved edit";
  page.dirty = true;
  await page.load();
  assert.match(page.document, /\n  "key": "review"/);
  assert.equal(page.dirty, false);
  assert.equal(page.error(), "");
});

test("explicit editor reload requires confirmation when dirty", async (t) => {
  const { page } = component(t, VersionPage);
  let loads = 0;
  page.load = () => {
    loads++;
  };
  page.dirty = true;
  t.mock.method(globalThis, "confirm", () => false);
  page.reloadEditor();
  assert.equal(loads, 0);
  globalThis.confirm = () => true;
  page.reloadEditor();
  assert.equal(loads, 1);
});
test("destroyed family components stop reacting to route parameter changes", async (t) => {
  const route = routeAt({ id: "1" });
  let reads = 0;
  const api = {
    async listVersions() {
      reads++;
      return { items: [], nextAfterId: null };
    },
    async definition(id) {
      return { id, name: "Family" };
    },
  };
  for (let i = 0; i < 4; i++) {
    const old = component(t, FamilyPage, api, route);
    old.page.ngOnInit();
    old.injector.destroy();
  }
  const current = component(t, FamilyPage, api, route);
  current.page.ngOnInit();
  await tick();
  reads = 0;
  route.paramMap.next(router.convertToParamMap({ id: "9007199254740993" }));
  await tick();
  assert.equal(reads, 1);
  assert.equal(current.page.detail().id, "9007199254740993");
  current.injector.destroy();
  reads = 0;
  route.paramMap.next(router.convertToParamMap({ id: "2" }));
  await tick();
  assert.equal(reads, 0);
});
test("out-of-order detail response cannot overwrite a later route identity", async (t) => {
  let release;
  const { page, route } = component(
    t,
    VersionPage,
    {
      version(id) {
        return id === "1"
          ? new Promise((r) => (release = r))
          : Promise.resolve({ summary: { id }, documentJson: '"new"' });
      },
    },
    routeAt({ id: "1" }),
  );
  page.ngOnInit();
  route.paramMap.next(router.convertToParamMap({ id: "2" }));
  await tick();
  release({ summary: { id: "1" }, documentJson: '"old"' });
  await tick();
  assert.equal(page.detail().summary.id, "2");
  assert.equal(page.document, '"new"');
});

test("definition diagram follows loaded/saved source, not unsaved editor changes", async (t) => {
  const { page } = component(t, VersionPage, {
    async version() { return { summary: { status: "DRAFT", lockVersion: "1" }, documentJson: '{"saved":true}' }; },
    async saveDocument() { return { changed: true, after: { lockVersion: "2" } }; }
  });
  page.id = "1";
  await page.load();
  page.document = '{"edited":true}'; page.dirty = true;
  assert.equal(page.savedDocument(), '{\n  "saved": true\n}');
  await page.saveDocument();
  assert.equal(page.savedDocument(), '{"edited":true}');
});

test("diagram uses submitted text when editor changes while save is pending", async (t) => {
  let finish;
  const { page } = component(t, VersionPage, {
    saveDocument() { return new Promise(resolve => finish = resolve); }
  });
  page.id = "1";
  page.detail.set({ summary: { lockVersion: "1" } });
  page.document = '{"submitted":true}';
  const saving = page.saveDocument();
  page.document = '{"newer":true}';
  finish({ changed: true, after: { lockVersion: "2" } });
  await saving;
  assert.equal(page.savedDocument(), '{"submitted":true}');
  assert.equal(page.dirty, true);
});
