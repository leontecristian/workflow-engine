import { test } from "node:test";
import assert from "node:assert/strict";
import {
  CommandBuffer,
  runtimeType,
  component,
  CommandRecovery,
  routeAt,
  router,
  tick,
} from "../../../../unit/runtime-component.mjs";
const { WorkflowTaskComponent: TaskPage } = runtimeType("task");

test("a destroyed task view ignores a pending read through the Angular destruction lifecycle", async (t) => {
  let resolveTask;
  const task = new Promise(resolve => { resolveTask = resolve; });
  const { page, injector } = component(t, TaskPage, {
    task: () => task,
    taskContract: async () => ({ outcomes: [{ key: "APPROVE" }] }),
    taskSubmission: async () => null,
  }, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: {} },
  ]);
  page.id = "9007199254740993";
  const pending = page.load();
  injector.destroy();
  resolveTask({ id: page.id, status: "OPEN" });
  await pending;
  assert.equal(page.detail(), null);
  assert.equal(page.contract(), null);
});
test("completion captures original instance token and assignment-specific task IDs", async (t) => {
  const buffer = new CommandBuffer();
  const commands = {
    buffer,
    async execute() {
      throw new Error("Transport outcome unknown");
    },
  };
  const { page } = component(t, TaskPage, {}, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: commands },
  ]);
  page.diagnosticsEnabled = true;
  page.id = "9223372036854775807";
  page.detail.set({ instanceId: "9007199254740993", status: "OPEN" });
  page.contract.set({ instanceLockVersion: "9007199254740999", canComplete: true, outcomes: [{key:"COMPLETE", label:"Complete", requiresComment:false}] });
  page.outcome = "COMPLETE";
  page.comment = "Original";
  await page.complete("COMPLETE");
  const original = buffer.command;
  page.comment = "Edited";
  page.contract.set({ instanceLockVersion: "100" });
  assert.equal(original.body.taskId, "9223372036854775807");
  assert.equal(original.body.comment, "Original");
  assert.equal(original.body.outcomeKey, "COMPLETE");
  assert.equal(original.expectedToken, "9007199254740999");
  assert.ok(Object.isFrozen(original));
  assert.match(page.error(), /Transport outcome unknown/);
});
test("outcome actions require their own comments, explicit keys and an open task", async (t) => {
  const buffer = new CommandBuffer();
  let calls = 0;
  const commands = { buffer, observations: new Map(), async execute() { calls++; throw new Error("Unknown transport outcome"); } };
  const { page } = component(t, TaskPage, {}, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: commands },
  ]);
  page.diagnosticsEnabled = true;
  page.id = "3";
  page.detail.set({ instanceId: "1", status: "OPEN" });
  page.contract.set({ instanceLockVersion: "2", canComplete: true, outcomes: [
    { key: "REJECT", label: "Reject", requiresComment: true },
    { key: "APPROVE", label: "Approve", requiresComment: false },
  ] });
  page.comment = " \t\n";
  await page.complete("REJECT");
  assert.equal(calls, 0);
  assert.match(page.error(), /comment is required/);
  await page.complete("unknown");
  assert.equal(calls, 0);
  page.comment = "  Exact reason\n";
  await page.complete("REJECT");
  assert.equal(calls, 1);
  const original = buffer.command;
  assert.equal(original.body.outcomeKey, "REJECT");
  assert.equal(original.body.comment, "  Exact reason\n");
  page.comment = "";
  await page.complete("APPROVE");
  assert.equal(calls, 2);
  assert.equal(buffer.command.body.outcomeKey, "APPROVE");
  assert.equal(buffer.command.body.comment, null);
  assert.equal(buffer.entries[0], original);
  page.detail.set({ instanceId: "1", status: "COMPLETED" });
  await page.complete("APPROVE");
  assert.equal(calls, 2);
});
test("pending completion freezes one outcome and blocks other action clicks", async (t) => {
  const buffer = new CommandBuffer();
  let release;
  const commands = { buffer, observations: new Map(), execute: () => new Promise((resolve) => { release = resolve; }) };
  const { page } = component(t, TaskPage, {}, routeAt(), undefined, [
    { provide: CommandRecovery, useValue: commands },
  ]);
  page.diagnosticsEnabled = true;
  page.id = "3";
  page.detail.set({ instanceId: "1", status: "OPEN" });
  page.contract.set({ instanceLockVersion: "2", canComplete: true, outcomes: [
    { key: "APPROVE", label: "Approve", requiresComment: false },
    { key: "REJECT", label: "Reject", requiresComment: true },
  ] });
  const pending = page.complete("APPROVE");
  page.comment = "Changed while pending";
  await page.complete("REJECT");
  assert.equal(buffer.entries.length, 1);
  assert.equal(buffer.command.body.outcomeKey, "APPROVE");
  assert.equal(buffer.command.body.comment, null);
  release();
  await pending;
});
test("reused task route resets completion input and destroyed pages ignore later parameters", async (t) => {
  const route = routeAt({ id: "1" });
  let reads = 0;
  const api = {
    async task(id) {
      reads++;
      return { id };
    },
    async taskContract() {
      return { instanceLockVersion: "1" };
    },
    async taskSubmission() {
      return null;
    },
  };
  const { page, injector } = component(t, TaskPage, api, route, undefined, [
    { provide: CommandRecovery, useValue: { result: () => null, setScope(){} } },
  ]);
  page.ngOnChanges({id:{currentValue:page.id}});
  await tick();
  page.outcome = "OLD";
  page.comment = "Prior task";
  page.id="2";page.ngOnChanges({id:{currentValue:"2"}});
  await tick();
  assert.equal(page.outcome, "");
  assert.equal(page.comment, "");
  assert.equal(page.detail().id, "2");
  injector.destroy();
  reads = 0;
  page.id="3";page.ngOnChanges({id:{currentValue:"3"}});
  await tick();
  assert.equal(reads, 0);
});


test('read-only consumer has no execution provider and refuses completion',async t=>{
 const {WORKFLOW_RUNTIME_COMMANDS}=await import('@workflow-engine/ui');
 const buffer=new CommandBuffer();let executions=0;
 const {page}=component(t,TaskPage,{},undefined,undefined,[{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:null},{provide:CommandRecovery,useValue:{buffer,execute(){executions++;}}}]);
 page.detail.set({instanceId:'1',status:'OPEN'});page.contract.set({instanceLockVersion:'2',outcomes:[{key:'APPROVE',label:'Approve',requiresComment:false}]});
 await page.complete('APPROVE');assert.equal(page.commandProvider,null);assert.equal(executions,0);assert.equal(buffer.entries.length,0);
 page.messages={taskTitle:'Host task'};assert.equal(page.meta.title,'Host task');
});

test('normal completion refreshes and confirms; conflict refreshes; ineligible tasks cannot execute', async t => {
 const buffer=new CommandBuffer(), observations=new Map(); let calls=0, reads=0, conflict=false;
 const commands={buffer,observations,async execute(){calls++;if(conflict)throw Object.assign(new Error('conflict'),{httpStatus:409});}};
 const api={async task(){reads++;return {instanceId:'1',status:'OPEN'};},async taskContract(){return {instanceLockVersion:'2',canComplete:true,outcomes:[{key:'OK',label:'OK',requiresComment:false}]};},async taskSubmission(){return null;}};
 const {page}=component(t,TaskPage,api,routeAt(),undefined,[{provide:CommandRecovery,useValue:commands}]);
 page.id='3';await page.load();page.contract.set({...page.contract(),canComplete:false});await page.complete('OK');assert.equal(calls,0);
 page.contract.set({...page.contract(),canComplete:true});await page.complete('OK');assert.equal(reads,2);assert.equal(page.notice(),'Task completed.');assert.equal(page.diagnosticsEnabled,false);
 conflict=true;await page.complete('OK');assert.equal(reads,3);assert.match(page.error(),/refreshed/);
});

test('uncertain completion retains its frozen command and blocks replacement', async t => {
 const buffer=new CommandBuffer(),observations=new Map();let calls=0;
 const commands={buffer,observations,async execute(){calls++;observations.set(buffer.command.body.commandId,{outcome:'UNKNOWN_OUTCOME'});throw new Error('network');}};
 const {page}=component(t,TaskPage,{},routeAt(),undefined,[{provide:CommandRecovery,useValue:commands}]);
 page.id='3';page.detail.set({instanceId:'1',status:'OPEN'});page.contract.set({instanceLockVersion:'2',canComplete:true,outcomes:[{key:'OK',label:'OK',requiresComment:false}]});
 await page.complete('OK');const original=buffer.command;await page.complete('OK');assert.equal(calls,1);assert.equal(buffer.command,original);assert.match(page.error(),/outcome is uncertain/i);
});

test('structured task completion handles required input, local JSON errors and optional omission',async t=>{
 const buffer=new CommandBuffer(),observations=new Map();
 let calls=0;
 const commands={buffer,observations,async execute(){
  calls++;
  throw Object.assign(new Error('/approvedAmount — Amount rejected.'),{httpStatus:422,code:'RESULT_REJECTED'});
 }};
 const {page}=component(t,TaskPage,{},routeAt(),undefined,[{provide:CommandRecovery,useValue:commands}]);
 page.id='3';page.detail.set({instanceId:'1',status:'OPEN'});
 const contract={instanceLockVersion:'2',canComplete:true,outcomes:[{key:'OK',label:'OK',requiresComment:false}]};
 page.contract.set({...contract,inputs:{booking:{type:'JSON',valueJson:'{"amount":9007199254740993.00}'}},resultContract:{mode:'REQUIRED',schema:{type:'object'}}});
 await page.complete('OK');
 assert.match(page.error(),/result is required/);
 page.resultDraft='null';
 await page.complete('OK');
 assert.match(page.error(),/must be a JSON object/);
 assert.equal(calls,0);
 const draft='{"approvedAmount":9007199254740993.00}';
 page.resultDraft=draft;
 await page.complete('OK');
 assert.equal(calls,1);
 assert.equal(buffer.command.body.resultJson,draft);
 assert.ok(!Object.hasOwn(buffer.command.body,'inputs'));
 assert.equal(page.inputs()[0][1].valueJson,'{"amount":9007199254740993.00}');
 assert.equal(page.resultDraft,draft);
 assert.match(page.error(),/approvedAmount.*Amount rejected/);
 page.contract.set({...contract,resultContract:{mode:'OPTIONAL',schema:{type:'object'}}});
 page.resultDraft=' \n ';
 await page.complete('OK');
 assert.equal(calls,2);
 assert.ok(!Object.hasOwn(buffer.command.body,'resultJson'));
});

test('ownership honors action flags, refreshes after success and preserves uncertain command', async t=>{
 const {WORKFLOW_RUNTIME_COMMANDS}=await import('@workflow-engine/ui');
 const buffer=new CommandBuffer(),observations=new Map();let calls=0,reads=0,fail=false;
 const commands={buffer,observations,async execute(){calls++;if(fail){observations.set(buffer.command.body.commandId,{outcome:'UNKNOWN_OUTCOME'});throw new Error('network');}}};
 const api={async task(){reads++;return {instanceId:'1',status:'OPEN',assignment:{kind:'GROUP',ref:'team'},assigneeRef:null};},async taskContract(){return {instanceLockVersion:'2',canClaim:true,canRelease:false,canAdminRelease:true,outcomes:[]};},async taskSubmission(){throw Object.assign(new Error('denied'),{httpStatus:403});}};
 const {page}=component(t,TaskPage,api,routeAt(),undefined,[{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:{ownership(){}}},{provide:CommandRecovery,useValue:commands}]);
 page.id='3';await page.load();await page.ownership('release');assert.equal(calls,0);
 await page.ownership('claim');assert.equal(calls,1);assert.equal(reads,2);assert.equal(page.notice(),'Task ownership updated.');
 fail=true;page.reason='  review  ';await page.ownership('admin-release');assert.equal(buffer.command.body.reason,'  review  ');await page.ownership('claim');assert.equal(calls,2);
});

test('loads the command token before the displayed ownership snapshot', async t => {
  let finishContract;
  let taskReads = 0;
  const contract = new Promise(resolve => { finishContract = resolve; });
  const { page } = component(t, TaskPage, {
    taskContract: () => contract,
    async task() { taskReads++; return { assigneeRef: 'owner' }; },
    async taskSubmission() { return null; },
  }, routeAt(), undefined, [{ provide: CommandRecovery, useValue: {} }]);
  page.id = '3';
  const pending = page.load();
  assert.equal(taskReads, 0);
  finishContract({ instanceLockVersion: '2' });
  await pending;
  assert.equal(taskReads, 1);
  assert.equal(page.detail().assigneeRef, 'owner');
});
