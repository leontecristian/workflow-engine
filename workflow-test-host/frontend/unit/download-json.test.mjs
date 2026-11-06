import {test} from 'node:test';
import assert from 'node:assert/strict';
import {downloadJson} from '../src/app/ui/download-json.ts';
test('JSON download preserves exact text and revokes the temporary URL', async(t)=>{
  let blob, clicked=false, revoked=false;
  const anchor={click(){clicked=true;}};
  const original=globalThis.document;
  globalThis.document={createElement:()=>anchor};
  t.after(()=>{globalThis.document=original;});
  t.mock.method(URL,'createObjectURL',value=>{blob=value; return 'blob:exact';});
  t.mock.method(URL,'revokeObjectURL',url=>{assert.equal(url,'blob:exact'); revoked=true;});
  const exact='{"amount":9007199254740993.00,"exponent":1E+2}';
  downloadJson(exact,'evidence.json');
  assert.equal(await blob.text(),exact); assert.equal(anchor.download,'evidence.json');
  assert.ok(clicked); assert.ok(revoked);
});
