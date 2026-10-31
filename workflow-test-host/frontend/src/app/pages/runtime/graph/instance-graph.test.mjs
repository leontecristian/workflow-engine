import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import {
  loadTs,
  component,
  routeAt,
  router,
  tick,
} from "../../../../../unit/component-loader.mjs";
const { InstanceGraphComponent } = loadTs(
  new URL("./instance-graph.ts", import.meta.url),
);
const { InstancePage } = loadTs(new URL("../instance.ts", import.meta.url));
const id = "9007199254740993";
const fixture = (status = "ACTIVE") => ({
  instance: {
    id,
    definitionKey: "review",
    definitionVersion: 2,
    status,
    currentNodeKey: status === "ACTIVE" ? "REVIEW" : "DONE",
    lastHistorySequence: status === "ACTIVE" ? "4" : "8",
  },
  nodes: [
    {
      key: "BEGIN",
      name: "Start",
      type: "START",
      state: "COMPLETED",
      taskId: null,
      activityId: null,
    },
    {
      key: "REVIEW",
      name: "Review <script>",
      type: "HUMAN_TASK",
      state: status === "ACTIVE" ? "ACTIVE" : "COMPLETED",
      taskId: "9007199254740995",
      activityId: "9007199254740997",
    },
    {
      key: "DONE",
      name: "Finished",
      type: "END",
      state: status === "ACTIVE" ? "PENDING" : "COMPLETED",
      taskId: null,
      activityId: null,
    },
  ],
  edges: [
    {
      key: "begin",
      from: "BEGIN",
      to: "REVIEW",
      outcome: null,
      traversed: true,
    },
    {
      key: "finish",
      from: "REVIEW",
      to: "DONE",
      outcome: "COMPLETE",
      traversed: status === "COMPLETED",
    },
  ],
});

test("overlapping refreshes keep the newest snapshot and its loading state", async (t) => {
  const pending = [];
  const { page } = component(t, InstancePage, {
    async instance() {
      return fixture().instance;
    },
    instanceGraph() {
      return new Promise((resolve) => pending.push(resolve));
    },
  });
  page.id = id;
  const first = page.load();
  await tick();
  const second = page.load();
  await tick();
  pending[0](fixture());
  await first;
  assert.equal(page.graph(), null);
  assert.equal(page.busy(), true);
  pending[1](fixture("COMPLETED"));
  await second;
  assert.equal(page.detail().lastHistorySequence, "8");
  assert.equal(page.busy(), false);
});

test("denied instance read never requests definition graph metadata", async (t) => {
  let graphReads = 0;
  const { page } = component(t, InstancePage, {
    async instance() {
      throw new Error("404 NOT_FOUND");
    },
    async instanceGraph() {
      graphReads++;
    },
  });
  page.id = id;
  await page.load();
  assert.equal(graphReads, 0);
  assert.equal(page.graph(), null);
  assert.match(page.error(), /404 NOT_FOUND/);
  assert.equal(page.busy(), false);
});
test("graph labels and links distinguish active, completed and future steps with exact IDs", (t) => {
  const { page } = component(t, InstanceGraphComponent);
  let model = fixture();
  page.graph = () => model;
  assert.equal(page.label(model.nodes[0]), "Completed");
  assert.equal(page.label(model.nodes[1]), "Current step");
  assert.equal(page.label(model.nodes[2]), "Not reached");
  assert.deepEqual(Array.from(page.route(model.nodes[1])), [
    "/task",
    "9007199254740995",
  ]);
  assert.deepEqual(Array.from(page.route(model.nodes[0])), ["/history", id]);
  assert.match(
    page.accessibleName(model.nodes[1]),
    /Review <script>.*Current step/,
  );
  model = fixture("COMPLETED");
  assert.match(page.label(model.nodes[2]), /Completed · final position/);
  assert.ok(model.nodes.every((n) => page.label(n) !== "Current step"));
});
test("layout follows node keys and truncates only visible labels without damaging Unicode", (t) => {
  const { page } = component(t, InstanceGraphComponent);
  page.graph = () => fixture();
  assert.equal(page.edgeStart("BEGIN"), 274);
  assert.equal(page.edgeEnd("REVIEW"), 334);
  const name = "😀".repeat(40);
  const short = page.shortName(name);
  assert.equal(Array.from(short).length, 26);
  assert.equal(page.graph().nodes[1].name, "Review <script>");
});
test("graph uses text bindings, keyboard links, state labels and scoped mobile scrolling", () => {
  const template = readFileSync(
    new URL("./instance-graph.html", import.meta.url),
    "utf8",
  );
  const style = readFileSync(
    new URL("./instance-graph.scss", import.meta.url),
    "utf8",
  );
  assert.ok(!template.includes("innerHTML"));
  assert.match(template, /\[routerLink\]="route\(node\)"/);
  assert.match(template, /aria-label/);
  assert.match(template, /tabindex="0"/);
  assert.match(template, /\['\/activity',node.activityId\]/);
  assert.match(style, /overflow-x:\s*auto/);
  assert.match(style, /:focus/);
});
test("instance refresh adopts graph metadata and highlighted state from one snapshot", async (t) => {
  let status = "ACTIVE";
  const { page } = component(
    t,
    InstancePage,
    {
      async instance() {
        return fixture().instance;
      },
      async instanceGraph() {
        return fixture(status);
      },
    },
    routeAt({ id }),
  );
  page.ngOnInit();
  await tick();
  assert.equal(page.graph().nodes[1].state, "ACTIVE");
  status = "COMPLETED";
  await page.load();
  assert.equal(page.detail(), page.graph().instance);
  assert.equal(page.detail().lastHistorySequence, "8");
  assert.equal(page.graph().nodes[2].state, "COMPLETED");
});
test("graph failure preserves ordinary detail without inventing progress", async (t) => {
  const { page } = component(
    t,
    InstancePage,
    {
      async instance() {
        return fixture().instance;
      },
      async instanceGraph() {
        throw new Error("500 HOST_CONFIGURATION");
      },
    },
    routeAt({ id }),
  );
  page.ngOnInit();
  await tick();
  assert.equal(page.detail().id, id);
  assert.equal(page.graph(), null);
  assert.match(page.graphError(), /HOST_CONFIGURATION/);
  assert.equal(page.error(), "");
  assert.equal(page.busy(), false);
});
test("late graph responses cannot replace a reused or destroyed instance route", async (t) => {
  let release;
  const { page, route, injector } = component(
    t,
    InstancePage,
    {
      async instance(instanceId) {
        return { ...fixture().instance, id: instanceId };
      },
      instanceGraph(instanceId) {
        if (instanceId === id) return new Promise((r) => (release = r));
        return Promise.resolve({
          ...fixture(),
          instance: { ...fixture().instance, id: instanceId },
        });
      },
    },
    routeAt({ id }),
  );
  page.ngOnInit();
  await tick();
  route.paramMap.next(router.convertToParamMap({ id: "2" }));
  await tick();
  release(fixture("COMPLETED"));
  await tick();
  assert.equal(page.detail().id, "2");
  assert.equal(page.graph().instance.id, "2");
  injector.destroy();
  route.paramMap.next(router.convertToParamMap({ id: "3" }));
  await tick();
  assert.equal(page.detail().id, "2");
});
