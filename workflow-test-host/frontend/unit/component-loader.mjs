import { readFileSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { runInNewContext } from "node:vm";
import ts from "typescript";
import * as jsonc from "jsonc-parser";
import "@angular/compiler";
import * as core from "@angular/core";
import * as router from "@angular/router";
import * as interop from "@angular/core/rxjs-interop";
import * as common from "@angular/common";
import * as forms from "@angular/forms";
import * as rxjs from "rxjs";

globalThis.confirm ??= () => true;

// Compile just the module under test and its imports. Real Angular decorators,
// DI, signals, DestroyRef and RxJS; external HTTP/navigation/DOM are test doubles.
// Never evaluate application bootstrap, start a host, or load a database context.
const modules = new Map(
  Object.entries({
    "@angular/core": core,
    "@angular/router": router,
    "@angular/core/rxjs-interop": interop,
    "@angular/common": common,
    "@angular/forms": forms,
    rxjs,
    "jsonc-parser": jsonc,
  }),
);
for (const name of [
  "autocomplete",
  "button",
  "card",
  "checkbox",
  "dialog",
  "expansion",
  "form-field",
  "input",
  "list",
  "progress-bar",
  "select",
  "sidenav",
  "table",
  "toolbar",
  "tooltip",
])
  modules.set(
    "@angular/material/" + name,
    await import("@angular/material/" + name),
  );
modules.set("@angular/cdk/layout", await import("@angular/cdk/layout"));
modules.set('@workflow-engine/ui', await import('@workflow-engine/ui'));
modules.set('@workflow-engine/ui/administration', await import('@workflow-engine/ui/administration'));
const cache = new Map();
export function loadTs(url) {
  const file = typeof url === "string" ? resolve(url) : fileURLToPath(url);
  if (cache.has(file)) return cache.get(file);
  const exports = {};
  cache.set(file, exports);
  const code = ts.transpileModule(readFileSync(file, "utf8"), {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.CommonJS,
      experimentalDecorators: true,
      useDefineForClassFields: false,
    },
  }).outputText;
  runInNewContext(
    code,
    {
      exports,
      require(name) {
        if (name.startsWith(".")) {
          const dependency =
            resolve(dirname(file), name) + (name.endsWith(".ts") ? "" : ".ts");
          if (!existsSync(dependency))
            throw new Error("Missing dependency " + dependency);
          return loadTs(dependency);
        }
        if (!modules.has(name))
          throw new Error("Unconfigured dependency " + name);
        return modules.get(name);
      },
      console,
      AbortController,
      URL,
      URLSearchParams,
      Blob,
      crypto: globalThis.crypto,
      fetch: (...args) => globalThis.fetch(...args),
      confirm: (...args) => globalThis.confirm(...args),
      get navigator() {
        return globalThis.navigator;
      },
      get document() {
        return globalThis.document;
      },
      get location() {
        return globalThis.location;
      },
    },
    { filename: file },
  );
  return exports;
}
export const { ConsoleApi } = loadTs(
  new URL("../src/app/data/console-api.ts", import.meta.url),
);
export const { ConsoleSession } = loadTs(
  new URL("../src/app/data/console-session.ts", import.meta.url),
);
export const { CommandRecovery } = loadTs(
  new URL("../src/app/data/command-recovery.ts", import.meta.url),
);
export function routeAt(params = {}, query = {}) {
  const paramMap = new rxjs.BehaviorSubject(router.convertToParamMap(params));
  const queryParamMap = new rxjs.BehaviorSubject(
    router.convertToParamMap(query),
  );
  return { paramMap, queryParamMap };
}
export function navigation() {
  return {
    calls: [],
    async navigate(parts, options) {
      this.calls.push({ parts: Array.from(parts), options });
      return true;
    },
  };
}
export function component(
  t,
  Type,
  api = {},
  route = routeAt(),
  navigate = navigation(),
  extra = [],
) {
  const injector = core.createEnvironmentInjector(
    [
      { provide: ConsoleApi, useValue: api },
      { provide: router.ActivatedRoute, useValue: route },
      { provide: router.Router, useValue: navigate },
      { provide: modules.get("@angular/material/dialog").MatDialog, useValue: {
        open() { throw new Error("Provide a dialog double for dialog interactions"); },
      } },
      ...extra,
    ],
    null,
  );
  t.after(() => {
    if (!injector.destroyed) injector.destroy();
  });
  const page = core.runInInjectionContext(injector, () => new Type());
  return { page, injector, route, router: navigate };
}
export function services(t, providers = []) {
  const injector = core.createEnvironmentInjector(
    [ConsoleApi, ConsoleSession, CommandRecovery, ...providers],
    null,
  );
  t.after(() => injector.destroy());
  return injector;
}
export const tick = () => new Promise(setImmediate);
export { core, router, rxjs };
