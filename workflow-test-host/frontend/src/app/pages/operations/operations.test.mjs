import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  component,
  routeAt,
  tick,
} from "../../../../unit/component-loader.mjs";
const { AuditPage } = loadTs(new URL("./audit.ts", import.meta.url));
const { AuditEventPage } = loadTs(new URL("./audit-event.ts", import.meta.url));
const { CapabilitiesPage } = loadTs(
  new URL("./capabilities.ts", import.meta.url),
);
test("audit and capabilities load independent read-only state", async (t) => {
  const audit = component(t, AuditPage, {
    async audit() {
      return { items: [{ id: "1" }], nextAfterId: null };
    },
  }).page;
  const capabilities = component(t, CapabilitiesPage, {
    async getCapabilities() {
      return { mode: "sample", failureFlags: { failAudit: false } };
    },
  }).page;
  await audit.load();
  await capabilities.load();
  audit.error.set("Audit failed");
  assert.equal(capabilities.error(), "");
  assert.equal(capabilities.detail().mode, "sample");
  audit.open({ id: "9007199254740993" });
  assert.equal(audit.rows()[0].id, "1");
});
test("audit detail retains exact evidence text", async (t) => {
  const { page } = component(
    t,
    AuditEventPage,
    {
      async auditEvent(id) {
        return { id, evidenceJson: '{"old":900719925474099312345}' };
      },
    },
    routeAt({ id: "9007199254740993" }),
  );
  page.ngOnInit();
  await tick();
  assert.equal(page.detail().id, "9007199254740993");
  assert.equal(page.detail().evidenceJson, '{"old":900719925474099312345}');
});
