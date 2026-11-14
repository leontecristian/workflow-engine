import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  routeAt,
  router,
  tick,
  rxjs,
} from "../../../../unit/administration-component.mjs";
const { MatDialog } = await import("@angular/material/dialog");
const { WorkflowDefinitionsComponent: DefinitionsPage } = loadTs(
  new URL("../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/definitions.ts", import.meta.url),
);
const { WorkflowDefinitionFamilyComponent: FamilyPage } = loadTs(new URL("../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/family.ts", import.meta.url));
const { WorkflowDefinitionVersionComponent: VersionPage } = loadTs(new URL("../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/version.ts", import.meta.url));

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

function importDialogDouble() {
  const closed = new rxjs.Subject();
  const ref = { disableClose: false, closes: 0, afterClosed: () => closed,
    close() { this.closes++; closed.next(); closed.complete(); } };
  const service = { opens: 0, open() { this.opens++; return ref; } };
  return { ref, service, provider: { provide: MatDialog, useValue: service } };
}

test("import dialog dismissal retains unfinished text and refuses duplicate opening", t => {
  const dialog = importDialogDouble();
  const { page } = component(t, DefinitionsPage, {}, undefined, undefined, [dialog.provider]);
  page.document = '{"unfinished":9007199254740993}';
  page.dirty = true;
  page.error.set('Validation feedback');
  page.openImport({}); page.openImport({});
  assert.equal(dialog.service.opens, 1);
  assert.equal(page.importDialogOpen(), true);
  page.closeImport();
  assert.equal(page.importDialogOpen(), false);
  assert.equal(page.document, '{"unfinished":9007199254740993}');
  assert.equal(page.dirty, true);
  assert.equal(page.error(), 'Validation feedback');
});

test("import dialog protects pending upload, allows file reselection and retains file errors", async t => {
  const dialog = importDialogDouble();
  const { page } = component(t, DefinitionsPage, {}, undefined, undefined, [dialog.provider]);
  page.openImport({});
  let finish;
  const input = { value: 'selected.json', files: [{ size: 2, text: () => new Promise(resolve => finish = resolve) }] };
  const uploading = page.upload({ target: input });
  assert.equal(input.value, '');
  assert.equal(dialog.ref.disableClose, true);
  page.closeImport();
  assert.equal(dialog.ref.closes, 0);
  finish('{"n":12500.00}'); await uploading;
  assert.equal(page.document, '{"n":12500.00}');
  assert.equal(dialog.ref.disableClose, false);
  await page.upload({ target: { files: [{ size: 4 * 1024 * 1024 + 1 }] } });
  assert.match(page.error(), /4 MiB/);
  assert.equal(page.importDialogOpen(), true);
  assert.equal(page.document, '{"n":12500.00}');
});

test("import failure remains in the dialog and accepted import closes before navigation", async t => {
  const dialog = importDialogDouble();
  let finish;
  const { page, router } = component(t, DefinitionsPage, {
    importDraft() { return new Promise((resolve, reject) => finish = { resolve, reject }); }
  }, undefined, undefined, [dialog.provider]);
  page.document = '{"x":1,"x":9007199254740993}'; page.dirty = true;
  page.openImport({});
  const failed = page.importDraft();
  assert.equal(dialog.ref.disableClose, true);
  page.closeImport(); assert.equal(dialog.ref.closes, 0);
  finish.reject(new Error('Invalid definition')); await failed;
  assert.equal(page.error(), 'Invalid definition');
  assert.equal(page.importDialogOpen(), true);
  assert.equal(dialog.ref.disableClose, false);
  assert.equal(page.dirty, true);
  const accepted = page.importDraft();
  finish.resolve({ changed: true, after: { id: '9007199254740993' } }); await accepted;
  assert.equal(page.importDialogOpen(), false);
  assert.equal(dialog.ref.closes, 1);
  assert.equal(router.calls[0].parts[1], '9007199254740993');
});

test("session invalidation and destruction close import dialogs and reject late results", async t => {
  for (const destroy of [false, true]) {
    const dialog = importDialogDouble();
    let finish, signal;
    const { page, injector, router } = component(t, DefinitionsPage, {
      importDraft(_text, abortSignal) { signal = abortSignal; return new Promise(resolve => finish = resolve); }
    }, undefined, undefined, [dialog.provider]);
    page.document = '{"private":true}';
    page.openImport({});
    const pending = page.importDraft();
    if (destroy) injector.destroy();
    else { page.sessionScope = null; page.ngOnChanges({ sessionScope: true }); }
    assert.equal(signal.aborted, true);
    assert.equal(dialog.ref.closes, 1);
    assert.equal(page.importDialogOpen(), false);
    assert.equal(page.document, '');
    finish({ changed: true, after: { id: '9' } }); await pending;
    assert.equal(router.calls.length, 0);
    assert.equal(page.notice(), '');
  }
});

test("definition cursor navigation commits page state only after a successful read", async t => {
  const calls = [];
  let fail = false, finish;
  const { page } = component(t, DefinitionsPage, {
    async listDefinitions(cursor, size) {
      calls.push({ cursor, size });
      if (fail) throw new Error('Read failed');
      if (cursor === 'second') return new Promise(resolve => finish = resolve);
      return { items: [{ id: 'first' }], nextAfterId: 'second' };
    }
  });
  await page.load();
  assert.equal(page.pageIndex, 0);
  const advancing = page.nextPage();
  await page.nextPage(); await page.previousPage();
  assert.equal(calls.length, 2);
  assert.equal(page.pageIndex, 0);
  finish({ items: [{ id: 'second' }], nextAfterId: 'third' }); await advancing;
  assert.equal(page.pageIndex, 1);
  fail = true;
  await page.nextPage(); await page.previousPage(); await page.load(undefined, true);
  assert.equal(page.pageIndex, 1);
  assert.equal(page.rows()[0].id, 'second');
  assert.equal(page.next, 'third');
  fail = false;
  await page.previousPage();
  assert.equal(page.pageIndex, 0);
  assert.equal(calls.at(-1).cursor, undefined);
});

test("definition refresh, page-size change and session replacement reset cursor history", async t => {
  let fail = false;
  const { page } = component(t, DefinitionsPage, {
    async listDefinitions(cursor, size) {
      if (fail) throw new Error('Read failed');
      return { items: [{ id: cursor ?? 'first', size }], nextAfterId: cursor ? null : 'second' };
    }
  });
  await page.load(); await page.nextPage();
  assert.equal(page.pageIndex, 1);
  fail = true; await page.changePageSize(10);
  assert.equal(page.pageSize, 50);
  assert.equal(page.pageIndex, 1);
  fail = false; await page.changePageSize(10);
  assert.equal(page.pageSize, 10);
  assert.equal(page.pageIndex, 0);
  await page.nextPage(); await page.load(undefined, true);
  assert.equal(page.pageIndex, 0);
  await page.nextPage();
  page.sessionScope = {}; page.ngOnChanges({ sessionScope: true }); await tick();
  assert.equal(page.pageIndex, 0);
  assert.equal(page.rows()[0].id, 'first');
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


test('late mutation and upload cannot restore a cleared or replacement session',async t=>{
 for(const invalidate of [page=>{page.sessionScope=null;page.ngOnChanges({sessionScope:true});},page=>{page.sessionScope={};page.versionId='2';page.ngOnChanges({versionId:true});}]){
  let finish;const {page}=component(t,VersionPage,{saveDocument(){return new Promise(resolve=>finish=resolve);},async version(){return {summary:{id:'2',lockVersion:'1'},documentJson:'{}'};}});
  page.id='1';page.detail.set({summary:{lockVersion:'7'}});page.document='{"private":true}';const pending=page.saveDocument();invalidate(page);finish({changed:true,after:{lockVersion:'8'}});await pending;
  assert.notEqual(page.detail()?.summary?.lockVersion,'8');assert.notEqual(page.savedDocument(),'{"private":true}');assert.equal(page.notice(),'');
 }
 let finish;const {page}=component(t,DefinitionsPage);const pending=page.upload({target:{files:[{size:2,text:()=>new Promise(resolve=>finish=resolve)}]}});page.sessionScope=null;page.ngOnChanges({sessionScope:true});finish('{}');await pending;assert.equal(page.document,'');assert.equal(page.dirty,false);
});

test('successful lifecycle write retains its result when follow-up refresh fails',async t=>{
 const {page}=component(t,VersionPage,{async checkVersion(){return {change:{after:{lockVersion:'9'}},report:{valid:true},published:true};},async version(){throw new Error('Read unavailable');}});
 page.id='1';page.detail.set({summary:{lockVersion:'7'}});await page.lifecycle('publish');assert.equal(page.detail().summary.lockVersion,'9');assert.equal(page.lastResponse().published,true);assert.equal(page.error(),'');assert.equal(page.refreshError(),'Read unavailable');assert.match(page.notice(),/published=true/);
});

test('administration denial clears protected text and distinguishes the denied message',async t=>{
 const {page}=component(t,VersionPage,{async saveDocument(){throw Object.assign(new Error('Denied'),{httpStatus:403});}});page.id='1';page.detail.set({summary:{lockVersion:'7'}});page.document='private';page.dirty=true;await page.saveDocument();assert.equal(page.detail(),null);assert.equal(page.document,'');assert.equal(page.dirty,false);assert.equal(page.error(),'Administration access denied.');assert.equal(page.busy(),false);
});
