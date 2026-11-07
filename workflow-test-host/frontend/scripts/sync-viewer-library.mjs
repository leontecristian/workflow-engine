import { cpSync, lstatSync, readFileSync, realpathSync, rmSync } from "node:fs";
import { resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const frontend = realpathSync(fileURLToPath(new URL("..", import.meta.url)));
const source = fileURLToPath(new URL("../../../workflow-ui/dist/workflow-ui/", import.meta.url));
const destination = resolve(frontend, "node_modules/@workflow-engine/ui");

// Refresh only npm's installed copy; never follow a linked package outside it.
const installed = realpathSync(destination);
if (lstatSync(destination).isSymbolicLink() ||
    installed !== destination || !installed.startsWith(frontend + sep)) {
  throw new Error("Expected an installed copy of @workflow-engine/ui; run npm ci in the frontend first.");
}
for (const directory of [source, installed]) {
  const manifest = JSON.parse(readFileSync(resolve(directory, "package.json"), "utf8"));
  if (manifest.name !== "@workflow-engine/ui") {
    throw new Error(`Unexpected package at ${directory}`);
  }
}
rmSync(installed, { recursive: true });
cpSync(source, destination, { recursive: true });
console.log("Updated the console's installed @workflow-engine/ui package.");
