import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { loadTs } from "../../unit/component-loader.mjs";
const { APP_ROUTES } = loadTs(new URL("./app.routes.ts", import.meta.url));
test("all retained URLs have explicit distinct page boundaries and a wildcard fallback", () => {
  const expected = [
    "definitions",
    "definition/:id",
    "version/:id",
    "catalog",
    "requests",
    "request/:id",
    "instances",
    "instance/:id",
    "my-tasks",
    "instance-tasks/:id",
    "task/:id",
    "activity/:id",
    "history/:id",
    "history-event/:id/:sequence",
    "submissions/:id",
    "commands",
    "events",
    "event/:id",
    "inbox",
    "inbox-event/:id",
    "audit",
    "audit-event/:id",
    "capabilities",
  ];
  assert.deepEqual(
    Array.from(APP_ROUTES, (r) => r.path)
      .filter((p) => p !== "" && p !== "**")
      .sort(),
    expected.sort(),
  );
  assert.equal(APP_ROUTES[0].redirectTo, "my-tasks");
  assert.equal(APP_ROUTES.at(-1).path, "**");
  assert.equal(
    new Set(APP_ROUTES.filter((r) => r.component).map((r) => r.component)).size,
    24,
  );
  assert.ok(!APP_ROUTES.some((r) => r.path.includes(":page")));
  const bootstrap = readFileSync(
    new URL("../main.ts", import.meta.url),
    "utf8",
  );
  assert.match(bootstrap, /withHashLocation\(\)/);
  assert.ok(!bootstrap.includes("@Component"));
  assert.ok(bootstrap.split("\n").length < 15);
});
