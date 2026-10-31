import { test } from "node:test";
import assert from "node:assert/strict";
import { loadTs } from "../../../unit/component-loader.mjs";
const { ConsoleRecords } = loadTs(new URL("./records.ts", import.meta.url));
const { ConsoleDetails } = loadTs(new URL("./details.ts", import.meta.url));
test("record presentation preserves exact IDs and does not format actors as dates", () => {
  for (const Type of [ConsoleRecords, ConsoleDetails]) {
    assert.equal(
      Type.prototype.isDate("actorRef", "2026-10-31Tnot-a-date"),
      false,
    );
    assert.equal(Type.prototype.isDate("name", "2026-10-31T12:00:00Z"), false);
    assert.equal(
      Type.prototype.isDate("createdAt", "2026-10-31T12:00:00Z"),
      true,
    );
  }
  const row = {
    instanceId: "9223372036854775807",
    lockVersion: "9007199254740993",
    status: "COMPLETED",
  };
  assert.equal(
    ConsoleRecords.prototype.cell(row, "instanceId"),
    row.instanceId,
  );
  assert.equal(
    ConsoleRecords.prototype.cell(row, "lockVersion"),
    row.lockVersion,
  );
});
test("delivery and catalog badges describe stored state", () => {
  assert.equal(
    ConsoleRecords.prototype.cell({ deliveredAt: null }, "deliveryState"),
    "PENDING",
  );
  assert.equal(
    ConsoleRecords.prototype.cell({ deliveredAt: "now" }, "deliveryState"),
    "DELIVERED",
  );
  assert.equal(
    ConsoleRecords.prototype.cell({ selectable: false }, "selectable"),
    "DISABLED",
  );
});
test("metadata reserves JSON for explicit evidence while preserving exact scalars", () => {
  const record = {
    id: "9007199254740993",
    name: "Review",
    instanceId: null,
    documentJson: '{"large":900719925474099312345}',
    nested: { status: "PASSED" },
  };
  const entries = ConsoleDetails.prototype.scalarEntries.call({
    value: () => record,
  });
  assert.deepEqual(
    Array.from(entries, (entry) => Array.from(entry)),
    [
      ["id", "9007199254740993"],
      ["name", "Review"],
      ["instanceId", null],
    ],
  );
  assert.equal(record.documentJson, '{"large":900719925474099312345}');
});
test("instance columns expose current state", () => {
  const columns = ConsoleRecords.prototype.columns.call({
    kind: () => "instances",
  });
  for (const key of [
    "id",
    "definitionKey",
    "status",
    "currentNodeKey",
    "startedAt",
    "resultKey",
  ])
    assert.ok(columns.some((c) => c.key === key));
});
