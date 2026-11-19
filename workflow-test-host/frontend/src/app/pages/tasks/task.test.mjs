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
import { provideWorkflowTaskEditors, WorkflowTaskComponent } from "@workflow-engine/ui/runtime";
import { WORKFLOW_RUNTIME_COMMANDS } from "@workflow-engine/ui";
const { WorkflowTaskComponent: TaskPage } = runtimeType("task");

function submit(page, outcomeKey, comment = "", resultJson) {
  return page.handlerInputs().submitDecision({ outcomeKey, comment, resultJson });
}

test("the Angular provider selects only the exact pinned task type and the default submits through the same callback", async (t) => {
  class BookingHandler {}
  const pinned = {
    instanceLockVersion: "12", canComplete: true,
    outcomes: [{ key: "COMPLETE", label: "Complete", requiresComment: false }],
    resultContract: { mode: "OPTIONAL", schema: { type: "object" } },
  };
  const comment = "  Exact generic comment\n";
  const resultJson = ' \n {"amount":9007199254740993.00} \n';
  const registrations = [[], [provideWorkflowTaskEditors({ BOOKING_REVIEW: BookingHandler })]];
  let defaultType;
  for (const providers of registrations) {
    const buffer = new CommandBuffer();
    let calls = 0;
    const commands = { buffer, observations: new Map(), runningIds: new Set(), execution: () => null, async execute() { calls++; } };
    const { page } = component(t, WorkflowTaskComponent, {}, routeAt({ id: "7" }), undefined, [
      ...providers, { provide: CommandRecovery, useValue: commands },
    ]);
    page.diagnosticsEnabled = true;
    page.detail.set({ id: "7", instanceId: "2", status: "OPEN", taskType: "BOOKING_REVIEW" });
    assert.equal(page.handler(), null);
    const contract = { ...pinned, taskType: "booking_review" };
    page.contract.set(contract);
    const handlerType = page.handler();
    assert.ok(handlerType);
    assert.notEqual(handlerType, BookingHandler);
    defaultType ??= handlerType;
    assert.equal(handlerType, defaultType);
    const form = new handlerType();
    const inputs = page.handlerInputs();
    assert.equal(inputs.contract, contract);
    assert.equal(inputs.editable, true);
    Object.assign(form, inputs, { comment, resultDraft: resultJson });
    await form.submit("COMPLETE");
    assert.equal(calls, 1);
    assert.equal(buffer.command.body.taskId, "7");
    assert.equal(buffer.command.body.comment, comment);
    assert.equal(buffer.command.body.resultJson, resultJson);
    assert.equal(buffer.command.expectedToken, "12");

    const bookingContract = { ...pinned, taskType: "BOOKING_REVIEW" };
    page.contract.set(bookingContract);
    assert.equal(page.handler(), providers.length ? BookingHandler : defaultType);
    const bookingInputs = page.handlerInputs();
    assert.equal(bookingInputs.contract, bookingContract);
    await bookingInputs.submitDecision({ outcomeKey: "COMPLETE", comment, resultJson });
    assert.equal(calls, 2);
    assert.equal(buffer.command.body.comment, comment);
    assert.equal(buffer.command.body.resultJson, resultJson);
  }
});

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
    observations: new Map(), runningIds: new Set(),
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
  const snapshot = { outcomeKey: "COMPLETE", comment: "Original" };
  const pending = page.handlerInputs().submitDecision(snapshot);
  snapshot.comment = "Edited";
  await pending;
  const original = buffer.command;
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
  const commands = { buffer, observations: new Map(), runningIds: new Set(), async execute() { calls++; throw new Error("Unknown transport outcome"); } };
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
  const callback = page.handlerInputs().submitDecision;
  await callback({ outcomeKey: "REJECT", comment: " \t\n" });
  assert.equal(calls, 0);
  assert.match(page.error(), /comment is required/);
  await submit(page, "unknown");
  assert.equal(calls, 0);
  assert.match(page.error(), /available outcome/);
  await submit(page, "APPROVE", "x".repeat(2001));
  assert.match(page.error(), /2000 characters/);
  await submit(page, "APPROVE", "", '{}');
  assert.match(page.error(), /result/i);
  assert.equal(calls, 0);
  await submit(page, "REJECT", "  Exact reason\n");
  assert.equal(calls, 1);
  const original = buffer.command;
  assert.equal(original.body.outcomeKey, "REJECT");
  assert.equal(original.body.comment, "  Exact reason\n");
  await submit(page, "APPROVE");
  assert.equal(calls, 2);
  assert.equal(buffer.command.body.outcomeKey, "APPROVE");
  assert.equal(buffer.command.body.comment, null);
  assert.equal(buffer.entries[0], original);
  page.detail.set({ instanceId: "1", status: "COMPLETED" });
  assert.equal(page.handler(), null);
  await callback({ outcomeKey: "APPROVE", comment: "" });
  assert.equal(calls, 2);
});
test("pending completion freezes one outcome and blocks other action clicks", async (t) => {
  const buffer = new CommandBuffer();
  let release;
  const commands = { buffer, observations: new Map(), runningIds: new Set(), execute: () => new Promise((resolve) => { release = resolve; }) };
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
  const callback = page.handlerInputs().submitDecision;
  const pending = callback({ outcomeKey: "APPROVE", comment: "" });
  assert.equal(page.handlerInputs().editable, false);
  assert.equal(page.handlerInputs().submitDecision, callback);
  await callback({ outcomeKey: "REJECT", comment: "Changed while pending" });
  assert.equal(buffer.entries.length, 1);
  assert.equal(buffer.command.body.outcomeKey, "APPROVE");
  assert.equal(buffer.command.body.comment, null);
  release();
  await pending;
});
test("task and session replacements invalidate retained callbacks and destroyed pages ignore later parameters", async (t) => {
  const route = routeAt({ id: "1" });
  const buffer = new CommandBuffer();
  let reads = 0, executions = 0;
  const contract = { instanceLockVersion: "1", canComplete: true, outcomes: [{ key: "OK", label: "OK" }] };
  const api = {
    async task(id) {
      reads++;
      return { id, instanceId: "10", status: "OPEN" };
    },
    async taskContract() {
      return contract;
    },
    async taskSubmission() {
      return null;
    },
  };
  const { page, injector } = component(t, TaskPage, api, route, undefined, [
    { provide: CommandRecovery, useValue: {
      buffer, observations: new Map(), runningIds: new Set(), execution: () => null, setScope() {}, async execute() { executions++; },
    } },
  ]);
  page.diagnosticsEnabled = true;
  page.ngOnChanges({id:{currentValue:page.id}});
  await tick();
  const decision = { outcomeKey: "OK", comment: "Retained" };
  const original = page.handlerInputs().submitDecision;
  page.id="2";page.ngOnChanges({id:{currentValue:"2"}});
  await tick();
  assert.equal(page.detail().id, "2");
  const later = page.handlerInputs().submitDecision;
  assert.notEqual(later, original);
  await original(decision);
  page.sessionScope = {};
  page.ngOnChanges({sessionScope:{currentValue:page.sessionScope}});
  await tick();
  const current = page.handlerInputs().submitDecision;
  assert.notEqual(current, later);
  await later(decision);
  assert.equal(executions, 0);
  await current(decision);
  assert.equal(executions, 1);
  assert.equal(buffer.command.body.taskId, "2");
  injector.destroy();
  await current(decision);
  assert.equal(executions, 1);
  reads = 0;
  page.id="3";page.ngOnChanges({id:{currentValue:"3"}});
  await tick();
  assert.equal(reads, 0);
});

test("late conflict from an old completion leaves the new task and callback intact", async (t) => {
  const buffer = new CommandBuffer();
  let failOldTask;
  const commands = {
    buffer, observations: new Map(), runningIds: new Set(), setScope() {},
    execute() { return new Promise((_, reject) => { failOldTask = reject; }); },
  };
  const reads = [];
  const api = {
    async task(id) { reads.push(id); return { id, instanceId: "10", status: "OPEN" }; },
    async taskContract() { return { instanceLockVersion: "2", canComplete: true, outcomes: [{ key: "OK" }] }; },
    async taskSubmission() { return null; },
  };
  const { page } = component(t, TaskPage, api, routeAt({ id: "1" }), undefined, [
    { provide: CommandRecovery, useValue: commands },
  ]);
  await page.load();
  const original = page.handlerInputs().submitDecision;
  const decision = { outcomeKey: "OK", comment: "Original" };
  const pending = original(decision);
  page.id = "2";
  page.ngOnChanges({ id: { currentValue: "2" } });
  await tick();
  const current = page.handlerInputs().submitDecision;
  await original(decision);
  failOldTask(Object.assign(new Error("conflict"), { httpStatus: 409 }));
  await pending;
  assert.deepEqual(reads, ["1", "2"]);
  assert.equal(buffer.entries.length, 1);
  assert.equal(buffer.command.body.taskId, "1");
  assert.equal(page.handlerInputs().submitDecision, current);
  assert.equal(page.handlerInputs().editable, true);
  assert.equal(page.error(), "");
});

test("a returned task becomes editable when its command settles behind another recovery selection", async (t) => {
  const pending = new Map();
  const transport = {
    complete(envelope) {
      return new Promise((_, reject) => pending.set(envelope.body.taskId, reject));
    },
  };
  const api = {
    async task(id) { return { id, instanceId: "10", status: "OPEN" }; },
    async taskContract() { return { instanceLockVersion: "2", canComplete: true, outcomes: [{ key: "OK" }] }; },
    async taskSubmission() { return null; },
  };
  const { page } = component(t, TaskPage, api, routeAt({ id: "1" }), undefined, [
    CommandRecovery,
    { provide: WORKFLOW_RUNTIME_COMMANDS, useValue: transport },
  ]);
  const visit = async (id) => {
    page.id = id;
    page.ngOnChanges({ id: { currentValue: id } });
    await tick();
  };
  await visit("1");
  const first = submit(page, "OK");
  await visit("2");
  const second = submit(page, "OK");
  await visit("1");
  const returned = page.handlerInputs();
  assert.equal(returned.editable, false);
  assert.equal(page.commands.buffer.command.body.taskId, "2");
  await returned.submitDecision({ outcomeKey: "OK", comment: "Still pending" });
  assert.equal(page.commands.buffer.entries.length, 2);

  const rejection = Object.assign(new Error("Invalid result"), { httpStatus: 422 });
  pending.get("1")(rejection);
  await first;
  assert.equal(page.commands.buffer.command.body.taskId, "2");
  assert.equal(page.handlerInputs().editable, true);
  assert.equal(page.handlerInputs().submitDecision, returned.submitDecision);
  pending.get("2")(rejection);
  await second;
});

test("a superseded completion refresh cannot interrupt a later visit to the same task", async (t) => {
  for (const conflict of [false, true]) {
    const reads = [];
    const api = {
      async task(id, signal) {
        if (id !== "1" || !reads.length) {
          if (id === "1") reads.push({ signal });
          return { id, instanceId: "10", status: "OPEN" };
        }
        return new Promise(resolve => reads.push({ signal, resolve }));
      },
      async taskContract() { return { instanceLockVersion: "2", canComplete: true, outcomes: [{ key: "OK" }] }; },
      async taskSubmission() { return null; },
    };
    const commands = {
      buffer: new CommandBuffer(), observations: new Map(), runningIds: new Set(), setScope() {},
      execution: () => null,
      async execute() {
        if (conflict) throw Object.assign(new Error("Conflict"), { httpStatus: 409 });
      },
    };
    const { page } = component(t, TaskPage, api, routeAt({ id: "1" }), undefined, [
      { provide: CommandRecovery, useValue: commands },
    ]);
    await page.load();
    const completion = submit(page, "OK");
    await tick();
    page.id = "2";
    page.ngOnChanges({ id: { currentValue: "2" } });
    await tick();
    page.id = "1";
    page.ngOnChanges({ id: { currentValue: "1" } });
    await tick();
    assert.equal(reads.length, 3);
    assert.equal(reads[1].signal.aborted, true);

    reads[1].resolve({ id: "1", instanceId: "10", status: "COMPLETED" });
    await completion;
    assert.equal(reads[2].signal.aborted, false);
    assert.equal(page.notice(), "");
    assert.equal(page.error(), "");
    reads[2].resolve({ id: "1", instanceId: "10", status: "OPEN" });
    await tick();
    assert.equal(page.detail().status, "OPEN");
    assert.equal(page.handlerInputs().editable, true);
  }
});

test('read-only consumer has no execution provider and refuses completion',async t=>{
 const {WORKFLOW_RUNTIME_COMMANDS}=await import('@workflow-engine/ui');
 const buffer=new CommandBuffer();let executions=0;
 const {page}=component(t,TaskPage,{},undefined,undefined,[{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:null},{provide:CommandRecovery,useValue:{buffer,execute(){executions++;}}}]);
 page.detail.set({instanceId:'1',status:'OPEN'});page.contract.set({instanceLockVersion:'2',canComplete:true,outcomes:[{key:'APPROVE',label:'Approve',requiresComment:false}]});
 assert.equal(page.commandProvider,null);assert.equal(page.handler(),null);assert.equal(executions,0);assert.equal(buffer.entries.length,0);
 page.messages={taskTitle:'Host task'};assert.equal(page.meta.title,'Host task');
});

test('normal completion refreshes and confirms; conflict refreshes; ineligible tasks cannot execute', async t => {
 const buffer=new CommandBuffer(), observations=new Map(); let calls=0, reads=0, conflict=false;
 const commands={buffer,observations,runningIds:new Set(),execution:()=>null,async execute(){calls++;if(conflict)throw Object.assign(new Error('conflict'),{httpStatus:409});}};
 const api={async instanceTasks(){return {items:[],nextAfterId:null};},async task(){reads++;return {instanceId:'1',status:'OPEN'};},async taskContract(){return {instanceLockVersion:'2',canComplete:true,outcomes:[{key:'OK',label:'OK',requiresComment:false}]};},async taskSubmission(){return null;}};
 const {page}=component(t,TaskPage,api,routeAt(),undefined,[{provide:CommandRecovery,useValue:commands}]);
 page.id='3';await page.load();page.contract.set({...page.contract(),canComplete:false});assert.equal(page.handlerInputs().editable,false);await submit(page,'OK');assert.equal(calls,0);
 page.contract.set({...page.contract(),canComplete:true});await submit(page,'OK');assert.equal(reads,2);assert.equal(page.notice(),'Task completed.');assert.equal(page.diagnosticsEnabled,false);
 conflict=true;await submit(page,'OK');assert.equal(reads,3);assert.match(page.error(),/refreshed/);
});

test('ALL completion reports waiting or resolution and refreshes authorized later-page work',async t=>{
 for(const status of ['ACTIVE','COMPLETED']){
  const buffer=new CommandBuffer(),observations=new Map(),receipt={activity:{completionPolicy:'ALL',status}};
  const commands={buffer,observations,runningIds:new Set(),execution:()=>receipt,async execute(){}};
  const api={task:async()=>({instanceId:'1',status:'COMPLETED'}),taskContract:async()=>({canComplete:false,outcomes:[]}),taskSubmission:async()=>null,
   instanceTasks:async(_,cursor)=>cursor?{items:[{id:'51',status:status==='ACTIVE'?'OPEN':'CANCELLED'}],nextAfterId:null}:{items:[],nextAfterId:'50'}};
  const {page}=component(t,TaskPage,api,routeAt(),undefined,[{provide:CommandRecovery,useValue:commands}]);
  page.id='3';page.detail.set({instanceId:'1',status:'OPEN'});page.contract.set({instanceLockVersion:'1',canComplete:true,outcomes:[{key:'OK',label:'OK'}]});
  await submit(page,'OK');assert.equal(page.error(),'');
  assert.match(page.notice(),status==='ACTIVE'?/waiting for other tasks/:/resolved the review/);
  assert.equal(page.currentTasks().length,status==='ACTIVE'?1:0);assert.equal(receipt.activity.status,status);
 }
});

test('uncertain completion retains its frozen command and blocks replacement', async t => {
 const buffer=new CommandBuffer(),observations=new Map();let calls=0;
 const commands={buffer,observations,runningIds:new Set(),async execute(){calls++;observations.set(buffer.command.body.commandId,{outcome:'UNKNOWN_OUTCOME'});throw new Error('network');}};
 const {page}=component(t,TaskPage,{},routeAt(),undefined,[{provide:CommandRecovery,useValue:commands}]);
 page.id='3';page.detail.set({instanceId:'1',status:'OPEN'});page.contract.set({instanceLockVersion:'2',canComplete:true,outcomes:[{key:'OK',label:'OK',requiresComment:false}]});
 await submit(page,'OK');const original=buffer.command;assert.equal(page.handlerInputs().editable,false);await submit(page,'OK');assert.equal(calls,1);assert.equal(buffer.command,original);assert.match(page.error(),/outcome is uncertain/i);
});

test('structured task completion handles required input, local JSON errors and optional omission',async t=>{
 const buffer=new CommandBuffer(),observations=new Map();
 let calls=0;
 const commands={buffer,observations,runningIds:new Set(),async execute(){
  calls++;
  throw Object.assign(new Error('/approvedAmount — Amount rejected.'),{httpStatus:422,code:'RESULT_REJECTED'});
 }};
 const {page}=component(t,TaskPage,{},routeAt(),undefined,[{provide:CommandRecovery,useValue:commands}]);
 page.id='3';page.detail.set({instanceId:'1',status:'OPEN'});
 const contract={instanceLockVersion:'2',canComplete:true,outcomes:[{key:'OK',label:'OK',requiresComment:false}]};
 page.contract.set({...contract,inputs:{booking:{type:'JSON',valueJson:'{"amount":9007199254740993.00}'}},resultContract:{mode:'REQUIRED',schema:{type:'object'}}});
 const form = new (page.handler())();
 Object.assign(form,page.handlerInputs());
 await submit(page,'OK');
 assert.match(page.error(),/result is required/);
 form.resultDraft='null';
 await form.submit('OK');
 assert.match(page.error(),/must be a JSON object/);
 assert.equal(calls,0);
 const draft='{"approvedAmount":9007199254740993.00}';
 form.resultDraft=draft;
 await form.submit('OK');
 assert.equal(calls,1);
 assert.equal(buffer.command.body.resultJson,draft);
 assert.ok(!Object.hasOwn(buffer.command.body,'inputs'));
 assert.equal(page.inputs()[0][1].valueJson,'{"amount":9007199254740993.00}');
 assert.equal(form.resultDraft,draft);
 assert.equal(page.handlerInputs().submitDecision,form.submitDecision);
 assert.match(page.error(),/approvedAmount.*Amount rejected/);
 page.contract.set({...contract,resultContract:{mode:'OPTIONAL',schema:{type:'object'}}});
 await submit(page,'OK','',' \n ');
 assert.equal(calls,2);
 assert.ok(!Object.hasOwn(buffer.command.body,'resultJson'));
});

test('ownership honors action flags, refreshes after success and preserves uncertain command', async t=>{
 const {WORKFLOW_RUNTIME_COMMANDS}=await import('@workflow-engine/ui');
 const buffer=new CommandBuffer(),observations=new Map();let calls=0,reads=0,fail=false;
 const commands={buffer,observations,runningIds:new Set(),async execute(){calls++;if(fail){observations.set(buffer.command.body.commandId,{outcome:'UNKNOWN_OUTCOME'});throw new Error('network');}}};
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
