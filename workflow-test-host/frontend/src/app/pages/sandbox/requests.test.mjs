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
