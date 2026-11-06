import { test } from "node:test";
import assert from "node:assert/strict";
import {
  services,
  ConsoleApi,
  ConsoleSession,
  CommandRecovery,
} from "../../../unit/component-loader.mjs";
const identity = {
  actorRef: "admin",
  roles: ["CONSOLE_ADMIN"],
  csrfHeaderName: "X-CSRF-TOKEN",
  csrfToken: "server-token",
};
test("request creation transports exact JSON text and represents omission as null", async (t) => {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push(options); return { ok: true, text: async () => '{}' };
  });
  const injector = services(t); injector.get(ConsoleSession).session.set(identity);
  const api = injector.get(ConsoleApi);
  const source = '{"amount":9007199254740993.00,"value":1E+2}';
  await api.createRequest("Exact request", source);
  await api.createRequest("No data");
  assert.equal(JSON.parse(calls[0].body).requestDataJson, source);
  assert.equal(JSON.parse(calls[1].body).requestDataJson, null);
});
test("JSON start transport and variable inspection retain exact numeric text", async (t) => {
  const calls=[];
  const exact='{"items":[{"key":"booking","type":"JSON","initialized":true,"value":{"amount":9007199254740993.00}}],"nextAfterKey":null,"lockVersion":"2","instanceId":"1"}';
  t.mock.method(globalThis,"fetch",async(url,options)=>{
    calls.push({url,options}); return {ok:true,text:async()=>url.includes("/variables?") ? exact : '{}'};
  });
  const injector=services(t); injector.get(ConsoleSession).session.set(identity);
  const api=injector.get(ConsoleApi);
  const inputs='{"booking":{"amount":9007199254740993.00,"exponent":1E+2}}';
  const envelope={kind:'start',body:{commandId:'12345678-1234-1234-1234-123456789abc',definitionVersionId:'42',requestId:'1',expectedRequestVersion:'0',inputs},expectedToken:null};
  await api.executeCommand(envelope,false); await api.executeCommand(envelope,true);
  assert.ok(calls[0].options.body.includes(inputs)); assert.equal(calls[0].options.body,calls[1].options.body);
  assert.equal(calls[0].options.headers['X-CSRF-TOKEN'],'server-token');
  const page=await api.variables('1'); assert.equal(page.exactJson,exact);
});
test("typed API keeps document text and exact expected token while adding CSRF", async (t) => {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push({ url, options });
    return {
      ok: true,
      text: async () => '{"after":{"id":"9007199254740993"}}',
    };
  });
  const injector = services(t),
    session = injector.get(ConsoleSession);
  session.session.set(identity);
  const api = injector.get(ConsoleApi);
  const raw = ' {"a":1,"a":900719925474099312345} ';
  const result = await api.saveDocument(
    "9007199254740993",
    raw,
    "9007199254740999",
  );
  assert.equal(calls[0].url, "/console/api/versions/9007199254740993/document");
  assert.equal(calls[0].options.body, raw);
  assert.equal(
    calls[0].options.headers["X-Expected-Lock-Version"],
    "9007199254740999",
  );
  assert.equal(calls[0].options.headers["X-CSRF-TOKEN"], "server-token");
  assert.equal(calls[0].options.credentials, "same-origin");
  assert.equal(result.after.id, "9007199254740993");
});
test("query methods use explicit bounded cursors and encode identity segments", async (t) => {
  const calls = [];
  t.mock.method(globalThis, "fetch", async (url) => {
    calls.push(url);
    return { ok: true, text: async () => '{"items":[],"nextAfterId":null}' };
  });
  const injector = services(t);
  injector.get(ConsoleSession).session.set(identity);
  const api = injector.get(ConsoleApi);
  await api.history("9007199254740993", "9007199254740995", 10);
  await api.submissions("2", "9223372036854775807", 1);
  await api.lookupVersion("key with/&", "3");
  await api.outboxEvent("15:4");
  await api.instanceGraph("9007199254740993");
  assert.match(calls[0], /afterSequence=9007199254740995/);
  assert.match(calls[1], /afterTaskId=9223372036854775807/);
  assert.match(calls[2], /key=key\+with%2F%26/);
  assert.match(calls[3], /events\/15%3A4$/);
  assert.equal(calls[4], "/console/api/instances/9007199254740993/graph");
});
test("concurrent initialization shares one session request and 401 clears private command state", async (t) => {
  let sessionReads = 0;
  t.mock.method(globalThis, "fetch", async (url) => ({
    ok: true,
    text: async () => {
      if (url.endsWith("/session")) {
        sessionReads++;
        return JSON.stringify(identity);
      }
      return '{"failureFlags":{}}';
    },
  }));
  const injector = services(t);
  const session = injector.get(ConsoleSession);
  const commands = injector.get(CommandRecovery);
  await Promise.all([session.initialize(), session.initialize()]);
  assert.equal(sessionReads, 1);
  assert.equal(commands.buffer.actor, "admin");
  t.mock.method(globalThis, "fetch", async () => ({
    ok: false,
    status: 401,
    text: async () => '{"code":"UNAUTHENTICATED"}',
  }));
  await assert.rejects(
    injector.get(ConsoleApi).listDefinitions(),
    /401 UNAUTHENTICATED/,
  );
  assert.equal(session.session(), null);
  assert.equal(commands.buffer.actor, null);
  assert.equal(commands.result(), null);
});
