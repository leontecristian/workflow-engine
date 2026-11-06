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
