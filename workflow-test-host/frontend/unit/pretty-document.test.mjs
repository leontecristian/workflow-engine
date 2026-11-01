import { strict as assert } from "node:assert";
import { test } from "node:test";
import { prettyDocument } from "../src/app/command-buffer.ts";

test("pretty printing changes only whitespace outside JSON strings", () => {
  const input = String.raw`{"id":9007199254740993,"text":"a,:[{}]","nested":[{},[],{"a":true}],"value":1e3,"escaped":"\u0061"}`;
  const formatted = prettyDocument(input);
  assert.ok(formatted.startsWith('{\n  "id": 9007199254740993,'));
  assert.ok(formatted.includes('"value": 1e3'));
  assert.ok(formatted.includes(String.raw`"escaped": "\u0061"`));
  assert.deepEqual(JSON.parse(formatted), JSON.parse(input));
  assert.equal(prettyDocument(formatted), formatted);
  assert.equal(prettyDocument(" [ ] "), "[]");
});

test("invalid JSON cannot be pretty printed", () => {
  for (const input of ["", "{", '{"a":}', '{"a":1,}']) {
    assert.throws(() => prettyDocument(input));
  }
});
