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

export function component(t, Type, reads={}, navigation={destination:i=>[i.kind, i.id ?? i.instanceId, i.sequence].filter(Boolean)}) {
  const {WORKFLOW_VIEWER_READS,WORKFLOW_VIEWER_NAVIGATION}=loadTs(new URL('../projects/workflow-ui/src/lib/contracts.ts',import.meta.url));
  const injector=core.createEnvironmentInjector([{provide:WORKFLOW_VIEWER_READS,useValue:reads},{provide:WORKFLOW_VIEWER_NAVIGATION,useValue:navigation}],null);
  const page=core.runInInjectionContext(injector,()=>new Type());
  if ('sessionScope' in page) {page.sessionScope={};page.instanceId='9007199254740993';}
  t.after(()=>{page.ngOnDestroy?.();injector.destroy();});
  return {page,injector};
}
export const tick=()=>new Promise(setImmediate);
