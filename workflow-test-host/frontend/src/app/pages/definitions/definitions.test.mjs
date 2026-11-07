import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  routeAt,
  router,
  tick,
  rxjs,
} from "../../../../unit/component-loader.mjs";
const { MatDialog } = await import("@angular/material/dialog");
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
  let closed;
  let opens = 0;
  const { page } = component(t, VersionPage, {}, undefined, undefined, [
    { provide: MatDialog, useValue: { open() {
      opens++;
      closed = new rxjs.Subject();
      return { afterClosed: () => closed, close() { closed.next(undefined); } };
    } } },
  ]);
  let loads = 0;
  page.load = () => {
    loads++;
  };
  page.dirty = true;
  const opening = page.reloadEditor();
  await page.reloadEditor();
  await opening;
  assert.equal(opens, 1);
  closed.next(false);
  closed.complete();
  assert.equal(loads, 0);
  await page.reloadEditor();
  closed.next(undefined);
  closed.complete();
  assert.equal(loads, 0);
  await page.reloadEditor();
  closed.next(true);
  closed.complete();
  assert.equal(loads, 1);
  page.dirty = false;
  await page.reloadEditor();
  assert.equal(loads, 2);
  assert.equal(opens, 3);
});

test("reload confirmation cannot load after version navigation or destruction", async (t) => {
  let closed;
  let closes = 0;
  const { page, injector } = component(t, VersionPage, {}, undefined, undefined, [
    { provide: MatDialog, useValue: { open() {
      closed = new rxjs.Subject();
      return { afterClosed: () => closed, close() { closes++; closed.next(true); } };
    } } },
  ]);
  let loads = 0;
  page.load = () => { loads++; };
  page.dirty = true;
  page.id = "1";
  await page.reloadEditor();
  page.id = "2";
  closed.next(true);
  closed.complete();
  assert.equal(loads, 0);
  await page.reloadEditor();
  injector.destroy();
  assert.equal(closes, 1);
  assert.equal(loads, 0);
});

test("unavailable reload dialog preserves unsaved text and allows another attempt", async (t) => {
  let opens = 0;
  const { page } = component(t, VersionPage, {}, undefined, undefined, [
    { provide: MatDialog, useValue: { open() {
      opens++;
      throw new Error("Dialog unavailable");
    } } },
  ]);
  page.document = "unsaved text";
  page.dirty = true;
  page.load = () => { assert.fail("must not reload without confirmation"); };
  await page.reloadEditor();
  assert.match(page.error(), /Cannot open the reload confirmation/);
  assert.equal(page.document, "unsaved text");
  assert.equal(page.dirty, true);
  await page.reloadEditor();
  assert.equal(opens, 2);
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
