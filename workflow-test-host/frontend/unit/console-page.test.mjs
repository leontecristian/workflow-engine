import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { runInNewContext } from "node:vm";
import ts from "typescript";
import "@angular/compiler";
import * as core from "@angular/core";
import * as interop from "@angular/core/rxjs-interop";
import * as common from "@angular/common";
import * as forms from "@angular/forms";
import * as routing from "@angular/router";
import { Subject } from "rxjs";
import * as commands from "../src/app/command-buffer.ts";

// Compile the actual page for Node without launching the browser application.
// Angular injection, signals, destruction and RxJS are real; HTTP/router I/O
// are test doubles. No DOM, host or database is needed.
class ConsoleApi {}
const modules = {
  "@angular/platform-browser": { bootstrapApplication: async () => {} },
  "@angular/core": core,
  "@angular/core/rxjs-interop": interop,
  "@angular/common": common,
  "@angular/forms": forms,
  "@angular/router": routing,
  "./app/console-api": { ConsoleApi },
  "./app/command-buffer": commands,
};
const source = readFileSync(new URL("../src/main.ts", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.CommonJS,
    experimentalDecorators: true,
    useDefineForClassFields: false,
  },
}).outputText;
const context = {
  exports: {},
  require(name) {
    assert.ok(name in modules, `Unconfigured test dependency: ${name}`);
    return modules[name];
  },
  console,
  URLSearchParams,
};
runInNewContext(compiled, context, { filename: "console-page.js" });
const { ConsolePage } = context.exports;

function routerAt(url) {
  const serializer = new routing.DefaultUrlSerializer();
  return {
    url,
    events: new Subject(),
    parseUrl: (value) => serializer.parse(value),
    async navigate(parts, options = {}) {
      this.url = parts.join("/");
      if (options.queryParams)
        this.url += "?" + new URLSearchParams(options.queryParams);
      return true;
    },
  };
}

function pageFor(t, router, call = async () => ({ items: [], nextAfterId: null })) {
  const api = {
    session: () => ({ actorRef: "admin" }),
    commandResult: core.signal(null),
    call,
  };
  const injector = core.createEnvironmentInjector([
    { provide: ConsoleApi, useValue: api },
    { provide: routing.Router, useValue: router },
  ], null);
  t.after(() => { if (!injector.destroyed) injector.destroy(); });
  const page = core.runInInjectionContext(injector, () => new ConsolePage());
  return { page, injector };
}

test("consecutive catalog edits use the latest committed version", async (t) => {
  let stored = { id: "9007199254740993", lockVersion: "0", name: "Original" };
  const writes = [];
  const { page } = pageFor(t, routerAt("/catalog"), async (path, method, body, token) => {
    if (method === "PUT") {
      writes.push(token);
      if (token !== stored.lockVersion) throw new Error("409 STALE_VERSION");
      stored = { ...stored, ...body, lockVersion: String(BigInt(token) + 1n) };
      return { changed: true, after: { ...stored } };
    }
    return { items: [{ ...stored }], nextAfterId: null };
  });
  page.page = "catalog";
  page.editing = { ...stored };
  page.name = "First edit";
  await page.saveCatalog();
  page.name = "Second edit";
  await page.saveCatalog();
  assert.deepEqual(writes, ["0", "1"]);
  assert.equal(page.error(), "");
  assert.equal(stored.name, "Second edit");
  assert.equal(page.editing.lockVersion, "2");
});

test("a rejected catalog edit retains its original token and user input", async (t) => {
  const { page } = pageFor(t, routerAt("/catalog"), async () => {
    throw new Error("409 STALE_VERSION");
  });
  page.page = "catalog";
  page.editing = { id: "1", lockVersion: "4" };
  page.name = "Unsaved edit";
  await page.saveCatalog();
  assert.match(page.error(), /409 STALE_VERSION/);
  assert.equal(page.editing.lockVersion, "4");
  assert.equal(page.name, "Unsaved edit");
});

test("request selection reaches a new start-page component without losing precision", async (t) => {
  const router = routerAt("/request/9007199254740993");
  const detail = pageFor(t, router);
  detail.page.selectRequest({ id: "9007199254740993", lockVersion: "9007199254740995" });
  detail.injector.destroy();
  const { page } = pageFor(t, router);
  await page.activate();
  assert.equal(page.page, "requests");
  assert.equal(page.requestId, "9007199254740993");
  assert.equal(page.requestToken, "9007199254740995");
  // Refreshing the list must not replace subsequent edits to the start form.
  page.requestId = "42";
  await page.load();
  assert.equal(page.requestId, "42");
});

test("destroyed pages stop reacting to later navigation", async (t) => {
  const router = routerAt("/catalog");
  let reads = 0;
  const call = async () => { reads++; return { items: [], nextAfterId: null }; };
  for (let i = 0; i < 4; i++) {
    const previous = pageFor(t, router, call);
    previous.page.ngOnInit();
    previous.injector.destroy();
  }
  const current = pageFor(t, router, call);
  current.page.ngOnInit();
  await new Promise(setImmediate);
  reads = 0;
  router.events.next(new routing.NavigationEnd(1, "/catalog", "/catalog"));
  await new Promise(setImmediate);
  assert.equal(reads, 1);
  current.injector.destroy();
  reads = 0;
  router.events.next(new routing.NavigationEnd(2, "/catalog", "/catalog"));
  await new Promise(setImmediate);
  assert.equal(reads, 0);
});
