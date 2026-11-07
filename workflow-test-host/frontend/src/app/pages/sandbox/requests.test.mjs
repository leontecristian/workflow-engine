import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  routeAt,
  navigation,
  CommandRecovery,
  router as routing,
} from "../../../../unit/component-loader.mjs";
const { RequestsPage } = loadTs(new URL("./requests.ts", import.meta.url));
const { RequestPage } = loadTs(new URL("./request.ts", import.meta.url));
const commands = {
  buffer: {
    remember(value) {
      this.command = value;
    },
  },
  async execute() {},
};
test("Use in start form transfers exact request identity into a newly created component", async (t) => {
  const router = navigation();
  const detail = component(
    t,
    RequestPage,
    {},
    routeAt({ id: "9007199254740993" }),
    router,
  );
  detail.page.selectRequest({
    id: "9007199254740993",
    lockVersion: "9007199254740995",
  });
  detail.injector.destroy();
  const query = router.calls[0].options.queryParams;
  const current = component(
    t,
    RequestsPage,
    {
      async listRequests() {
        return { items: [], nextAfterId: null };
      },
      async listDefinitions() {
        return { items: [], nextAfterId: null };
      },
    },
    routeAt({}, query),
    router,
    [{ provide: CommandRecovery, useValue: commands }],
  );
  current.page.ngOnInit();
  assert.equal(current.page.requestId, "9007199254740993");
  assert.equal(current.page.requestToken, "9007199254740995");
  current.page.requestId = "42";
  await current.page.load();
  assert.equal(current.page.requestId, "42");
  current.injector.destroy();
  current.route.queryParamMap.next(
    routing.convertToParamMap({ requestId: "99", requestVersion: "2" }),
  );
  assert.equal(current.page.requestId, "42");
});
test("start retains original selection and token in command recovery", async (t) => {
  const { page, router } = component(
    t,
    RequestsPage,
    {},
    routeAt(),
    undefined,
    [{ provide: CommandRecovery, useValue: commands }],
  );
  page.versionId = "9223372036854775807";
  page.requestId = "9007199254740993";
  page.requestToken = "9007199254740995";
  await page.start();
  assert.equal(
    commands.buffer.command.body.expectedRequestVersion,
    "9007199254740995",
  );
  assert.equal(
    commands.buffer.command.body.definitionVersionId,
    "9223372036854775807",
  );
  assert.equal(commands.buffer.command.expectedToken, null);
  assert.equal(router.calls[0].parts[0], "/commands");
});

const workflow = (id, name = "Booking review", key = "booking.review") => ({ id, name, key });
const version = (id, number = 1) => ({ id, version: number });
function requests(t, api) {
  return component(t, RequestsPage, api, routeAt(), undefined,
    [{ provide: CommandRecovery, useValue: commands }]);
}
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
test("workflow search filters loaded names and keys and appends bounded catalog pages", async (t) => {
  const calls = [];
  const { page } = requests(t, {
    async listDefinitions(cursor, limit) {
      calls.push([cursor, limit]);
      return cursor
        ? { items: [workflow("9007199254740993", "Travel", "TRAVEL_REVIEW")], nextAfterId: null }
        : { items: [workflow("1")], nextAfterId: "1" };
    },
  });
  await page.loadWorkflows();
  page.workflowEdited(" BOOKING ");
  assert.equal(page.matchingWorkflows()[0].id, "1");
  page.workflowEdited("travel_review");
  assert.equal(page.matchingWorkflows().length, 0);
  await page.loadWorkflows(true);
  assert.equal(page.workflows().length, 2);
  assert.equal(page.matchingWorkflows()[0].id, "9007199254740993");
  assert.equal(page.workflowLabel(page.matchingWorkflows()[0]), "Travel · TRAVEL_REVIEW");
  assert.equal(JSON.stringify(calls), JSON.stringify([[undefined, 50], ["1", 50]]));
});
test("selection loads published versions and sole complete-page option is selected", async (t) => {
  const calls = [];
  const { page } = requests(t, {
    async listVersions(...args) {
      calls.push(args);
      return { items: [version("9007199254740995")], nextAfterId: null };
    },
  });
  await page.selectWorkflow(workflow("9007199254740993"));
  assert.equal(page.familyId, "9007199254740993");
  assert.equal(page.versionId, "9007199254740995");
  assert.equal(JSON.stringify(calls[0]), JSON.stringify(["9007199254740993", "PUBLISHED", undefined, 50]));
  page.workflowEdited("another workflow");
  assert.equal(page.familyId, "");
  assert.equal(page.versionId, "");
  assert.equal(page.publishedRows().length, 0);
});
test("zero or multiple versions require selection and pagination preserves earlier options", async (t) => {
  let response = { items: [], nextAfterId: null };
  const { page } = requests(t, { async listVersions() { return response; } });
  await page.selectWorkflow(workflow("1"));
  assert.equal(page.versionId, "");
  response = { items: [version("10"), version("11", 2)], nextAfterId: null };
  await page.selectWorkflow(workflow("2"));
  assert.equal(page.versionId, "");
  response = { items: [version("20")], nextAfterId: "20" };
  await page.selectWorkflow(workflow("3"));
  assert.equal(page.versionId, "");
  page.versionId = "20";
  response = { items: [version("21", 2)], nextAfterId: null };
  await page.loadPublished(true);
  assert.equal(page.publishedRows().map((row) => row.id).join(","), "20,21");
  assert.equal(page.versionId, "20");
});
test("changing workflows clears selection immediately and ignores stale success and failure", async (t) => {
  const first = deferred(), second = deferred(), third = deferred();
  const pending = [first, second, third];
  const { page } = requests(t, { listVersions() { return pending.shift().promise; } });
  page.versionId = "old";
  const a = page.selectWorkflow(workflow("1"));
  assert.equal(page.versionId, "");
  assert.equal(page.versionsLoading(), true);
  const b = page.selectWorkflow(workflow("2"));
  second.resolve({ items: [version("22")], nextAfterId: null });
  await b;
  first.resolve({ items: [version("11")], nextAfterId: null });
  await a;
  assert.equal(page.versionId, "22");
  assert.equal(page.familyId, "2");
  const c = page.selectWorkflow(workflow("3"));
  page.workflowEdited("free text");
  third.reject(new Error("obsolete failure"));
  await c;
  assert.equal(page.versionId, "");
  assert.equal(page.versionError(), "");
  assert.equal(page.versionsLoading(), false);
});
test("catalog and version failures are retryable without retaining a previous workflow version", async (t) => {
  let fail = true;
  const { page } = requests(t, {
    async listDefinitions() {
      if (fail) throw new Error("catalog unavailable");
      return { items: [workflow("1")], nextAfterId: null };
    },
    async listVersions() {
      if (fail) throw new Error("versions unavailable");
      return { items: [version("10")], nextAfterId: null };
    },
  });
  await page.loadWorkflows();
  assert.match(page.workflowError(), /catalog unavailable/);
  page.versionId = "old";
  await page.selectWorkflow(workflow("1"));
  assert.equal(page.versionId, "");
  assert.match(page.versionError(), /versions unavailable/);
  fail = false;
  await page.loadWorkflows();
  await page.loadPublished();
  assert.equal(page.workflowError(), "");
  assert.equal(page.versionError(), "");
  assert.equal(page.versionId, "10");
});

test('same-user session replacement clears host start text and suppresses late command navigation',async t=>{
 const {ConsoleSession}=loadTs(new URL('../../data/console-session.ts',import.meta.url));
 let resolve;
 const {page,injector,router}=component(t,RequestsPage,{async listRequests(){return {items:[],nextAfterId:null};},async listDefinitions(){return {items:[],nextAfterId:null};}},undefined,undefined,[{provide:CommandRecovery,useValue:{execute:()=>new Promise(r=>resolve=r)}}]);
 page.ngOnInit();page.inputsText='{"private":9007199254740993.00}';page.requestDataText='{"private":true}';page.requestId='9007199254740993';
 const pending=page.issue(false),session=injector.get(ConsoleSession);
 session.viewerScope.set({});session.identityChanges.next('same-user');
 assert.equal(page.inputsText,'{}');assert.equal(page.requestDataText,'');assert.equal(page.requestId,'');
 resolve({replayed:false,execution:{}});await pending;assert.equal(router.calls.length,0);assert.equal(page.notice(),'');
});

test('late old-session request reads and creation cannot repopulate cleared start context',async t=>{
 const {ConsoleSession}=loadTs(new URL('../../data/console-session.ts',import.meta.url));
 let rows,workflows,created;
 const {page,injector}=component(t,RequestsPage,{listRequests:()=>new Promise(r=>rows=r),listDefinitions:()=>new Promise(r=>workflows=r),createRequest:()=>new Promise(r=>created=r)},undefined,undefined,[{provide:CommandRecovery,useValue:commands}]);
 page.ngOnInit();const creating=page.createRequest(),session=injector.get(ConsoleSession);
 const oldRows=rows,oldWorkflows=workflows;
 session.viewerScope.set({});session.identityChanges.next('same-user');
 oldRows({items:[{id:'old'}],nextAfterId:'old'});oldWorkflows({items:[{id:'old'}],nextAfterId:'old'});created({id:'old',lockVersion:'9'});
 rows({items:[],nextAfterId:null});workflows({items:[],nextAfterId:null});
 await creating;await new Promise(setImmediate);
 assert.equal(page.rows().length,0);assert.equal(page.workflows().length,0);assert.equal(page.requestId,'');assert.equal(page.requestToken,'0');assert.equal(page.notice(),'');assert.equal(page.busy(),false);assert.equal(page.workflowsLoading(),false);
});

test('initial session arrival loads host start options and retains explicit deep-link selection',async t=>{
 const {ConsoleSession}=loadTs(new URL('../../data/console-session.ts',import.meta.url));
 const {core,rxjs}=await import('../../../../unit/component-loader.mjs');
 const session={viewerScope:core.signal(null),identityChanges:new rxjs.Subject()};let requestReads=0,workflowReads=0;
 const {page}=component(t,RequestsPage,{async listRequests(){requestReads++;return {items:[],nextAfterId:null};},async listDefinitions(){workflowReads++;return {items:[{id:'1'}],nextAfterId:null};}},routeAt({},{requestId:'9007199254740993',requestVersion:'5'}),undefined,[{provide:ConsoleSession,useValue:session},{provide:CommandRecovery,useValue:commands}]);
 page.ngOnInit();assert.equal(requestReads,0);session.viewerScope.set({});session.identityChanges.next('admin');await new Promise(setImmediate);
 assert.equal(requestReads,1);assert.equal(workflowReads,1);assert.equal(page.workflows().length,1);assert.equal(page.requestId,'9007199254740993');assert.equal(page.requestToken,'5');
});
