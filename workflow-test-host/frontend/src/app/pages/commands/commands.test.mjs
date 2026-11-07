import { test } from "node:test";
import assert from "node:assert/strict";
import {
  loadTs,
  runtimeType,
  component,
  CommandRecovery,
  CommandBuffer,
  core,
} from "../../../../unit/runtime-component.mjs";
const { WorkflowCommandsComponent: CommandsPage } = runtimeType("commands");
test("command page retries/replays explicitly without replacing command state", async (t) => {
  const calls = [];
  const result = core.signal(null);
  const service = {
    buffer:new CommandBuffer(),
    result,
    async execute(replay) {
      calls.push(replay);
    },
    execution() {
      return null;
    },
  };
  const a = component(t, CommandsPage, {}, undefined, undefined, [
    { provide: CommandRecovery, useValue: service },
  ]);
  assert.equal(calls.length, 0);
  await a.page.retry(true);
  await a.page.retry(false);
  assert.deepEqual(calls, [true, false]);
  a.injector.destroy();
  const b = component(t, CommandsPage, {}, undefined, undefined, [
    { provide: CommandRecovery, useValue: service },
  ]);
  assert.equal(b.page.result, result);
  assert.equal(b.page.envelope, "");
});
test("restore errors keep the recovery text available for correction", (t) => {
  const { page } = component(t, CommandsPage, {}, undefined, undefined, [
    {
      provide: CommandRecovery,
      useValue: {
        buffer:new CommandBuffer(), result: core.signal(null),
        restore() {
          throw new Error("Invalid command envelope");
        },
      },
    },
  ]);
  page.envelope = "original input";
  page.restoreEnvelope();
  assert.equal(page.envelope, "original input");
  assert.match(page.error(), /Invalid command envelope/);
});

test("live stage discovery keeps the historical command result and never submits a successor", async (t) => {
  const original = { instance: { id: "1", status: "ACTIVE", currentNodeKey: "PARTNER" }, task: { id: "10", status: "COMPLETED" } };
  let executions = 0;
  const service = { buffer:new CommandBuffer(), result: core.signal(original), execution: () => original, async execute() { executions++; } };
  const { page } = component(t, CommandsPage, {
    instance: async () => ({ id: "1", status: "ACTIVE", currentNodeKey: "PARTNER", lockVersion: "5" }),
    instanceTasks: async () => ({ items: [{ id: "10", status: "COMPLETED" }, { id: "11", status: "OPEN" }] }),
  }, undefined, undefined, [{ provide: CommandRecovery, useValue: service }]);
  await page.refreshCurrent();
  assert.equal(page.currentInstance().currentNodeKey, "PARTNER");
  assert.deepEqual(page.currentTasks().map(t => t.id), ["11"]);
  assert.equal(service.execution(), original);
  assert.equal(original.task.id, "10");
  assert.equal(executions, 0);
  page.viewer.instance = async () => ({ id: "1", status: "COMPLETED", currentNodeKey: "APPROVED" });
  page.api.instanceTasks = async () => ({ items: [{ id: "10", status: "COMPLETED" }, { id: "11", status: "COMPLETED" }] });
  await page.refreshCurrent();
  assert.equal(page.currentInstance().status, "COMPLETED");
  assert.equal(original.instance.status, "ACTIVE");
  assert.deepEqual(page.currentTasks(), []);
});

test("a delayed live read cannot replace a newly selected command's current work", async (t) => {
  let execution = { instance: { id: "1" } }, release;
  const delayed = new Promise(resolve => { release = resolve; });
  const service = { buffer:new CommandBuffer(), result: core.signal(null), execution: () => execution };
  const { page } = component(t, CommandsPage, {
    instance: async id => id === "1" ? delayed : { id: "2" },
    instanceTasks: async id => ({ items: [{ id: id + "0", status: "OPEN" }] }),
  }, undefined, undefined, [{ provide: CommandRecovery, useValue: service }]);
  const old = page.refreshCurrent();
  execution = { instance: { id: "2" } };
  await page.refreshCurrent();
  release({ id: "1" }); await old;
  assert.equal(page.currentInstance().id, "2");
  assert.equal(page.currentTasks()[0].id, "20");
});


test('committed original result survives a failed current-state refresh',async t=>{
 const observation={outcome:'NEW_COMMITTED_RESULT',result:{replayed:false,execution:{instance:{id:'1'},task:{id:'10'}}}};
 const service={buffer:new CommandBuffer(),result:core.signal(null),execution(){return this.result()?.result?.execution||null;},async execute(){this.result.set(observation);}};
 const {page}=component(t,CommandsPage,{instance:async()=>{throw new Error('Current read failed');},instanceTasks:async()=>({items:[]})},undefined,undefined,[{provide:CommandRecovery,useValue:service}]);
 await page.retry(false);assert.equal(page.result(),observation);assert.equal(page.currentInstance(),null);assert.match(page.error(),/Current read failed/);
});
test('a delayed command cannot launch current reads after same-user session replacement',async t=>{
 let release,reads=0;
 const service={buffer:new CommandBuffer(),result:core.signal(null),setScope(){},execution:()=>({instance:{id:'1'}}),execute:()=>new Promise(resolve=>release=resolve)};
 const {page}=component(t,CommandsPage,{instance:async()=>{reads++;return {id:'1'};},instanceTasks:async()=>({items:[]})},undefined,undefined,[{provide:CommandRecovery,useValue:service}]);
 const pending=page.retry(false);page.sessionScope={};page.ngOnChanges({sessionScope:{currentValue:page.sessionScope}});await new Promise(setImmediate);const afterReplacement=reads;
 release();await pending;assert.equal(reads,afterReplacement);assert.equal(page.notice(),'');
});
test('replay-only absence is explicit and custom recovery title is applied',t=>{
 const result=core.signal({outcome:'REPLAY_ONLY_LOOKUP',result:{found:false,result:null}});
 const {page}=component(t,CommandsPage,{},undefined,undefined,[{provide:CommandRecovery,useValue:{result,buffer:new CommandBuffer(),execution:()=>null}}]);
 page.messages={commandsTitle:'Host recovery'};assert.equal(page.meta.title,'Host recovery');assert.equal(page.resultNotFound(),true);
});

test('copy exports frozen values and delayed clipboard completion cannot replace newer selection or session feedback',async t=>{
 const descriptor=Object.getOwnPropertyDescriptor(globalThis,'navigator');let release,copied;
 Object.defineProperty(globalThis,'navigator',{configurable:true,value:{clipboard:{writeText:text=>{copied=text;return new Promise(resolve=>release=resolve);}}}});
 t.after(()=>{if(descriptor)Object.defineProperty(globalThis,'navigator',descriptor);else delete globalThis.navigator;});
 const buffer=new CommandBuffer(),first={kind:'complete',body:{commandId:'12345678-1234-1234-1234-123456789abc',instanceId:'9007199254740993',taskId:'2',outcomeKey:'APPROVE',comment:' exact '},expectedToken:'9007199254740999'};
 buffer.remember(first);
 const service={buffer,result:core.signal(null),execution:()=>null,select:id=>buffer.select(id),setScope(){buffer.clear();}};
 const {page}=component(t,CommandsPage,{},undefined,undefined,[{provide:CommandRecovery,useValue:service}]);
 const old=page.copyEnvelope();assert.deepEqual(JSON.parse(copied),first);
 const second={...first,body:{...first.body,commandId:'12345678-1234-1234-1234-123456789abd'}};buffer.remember(second);page.selectCommand(second.body.commandId);page.notice.set('Selected newer command');release();await old;
 assert.equal(buffer.command.body.commandId,second.body.commandId);assert.equal(page.notice(),'Selected newer command');
 const pending=page.copyEnvelope();page.sessionScope={};page.ngOnChanges({sessionScope:{currentValue:page.sessionScope}});release();await pending;assert.equal(page.notice(),'');assert.equal(buffer.command,null);
});
