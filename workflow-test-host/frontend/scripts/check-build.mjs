import { readFileSync } from "node:fs";
import assert from "node:assert/strict";

// CSP forbids inline script handlers. Critical CSS optimization otherwise emits
// media="print" + onload, leaving the actual application stylesheet disabled.
const html = readFileSync(
  new URL("../dist/console/browser/index.html", import.meta.url),
  "utf8",
);
assert.doesNotMatch(
  html,
  /\son\w+\s*=/i,
  "Built HTML must not require inline script handlers",
);
const styles = html.match(/<link\b[^>]*rel=["']stylesheet["'][^>]*>/gi) || [];
assert.ok(styles.length, "Production HTML must load its stylesheet");
for (const style of styles)
  assert.doesNotMatch(
    style,
    /media=["']print["']/i,
    "Application styles must apply without JavaScript",
  );
console.log(
  "PASS: production styles load under the existing Content Security Policy",
);
