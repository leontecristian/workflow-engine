import {test} from 'node:test';
import assert from 'node:assert/strict';
import {runtimeType,component,CommandRecovery,core,loadTs,tick} from '../../../../unit/runtime-component.mjs';
import {CommandBuffer,freezeEnvelope,WORKFLOW_TASK_REASSIGNMENT,WORKFLOW_RUNTIME_COMMANDS} from '@workflow-engine/ui';
import {MatDialogRef,MAT_DIALOG_DATA,MatDialog} from '@angular/material/dialog';
const {WorkflowTaskComponent:Task}=runtimeType('task');
const {WorkflowTaskAdministrationComponent:Administration}=runtimeType('task-administration');
const {WorkflowReassignmentDialog:Dialog}=runtimeType('reassignment-dialog');
const envelope={kind:'reassign',body:{commandId:'12345678-1234-1234-1234-123456789abc',taskId:'30',assignmentKind:'GROUP',assignmentRef:'Finance',reason:'  exact\n😀  '},expectedToken:'reassign-reviewed-version'};
const detail={status:'OPEN',ownershipLabel:'Assigned to another user'};
const contract={task:detail,precondition:envelope.expectedToken,canComplete:false,outcomes:[]};
const api={taskContract:async()=>contract,taskSubmission:async()=>null};
function setup(t,transport,extra=[]){
 const recoveryProviders=[CommandRecovery,{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:null},{provide:WORKFLOW_TASK_REASSIGNMENT,useValue:transport},...extra];
 const result=component(t,Task,{...api},undefined,undefined,recoveryProviders);result.page.id='30';
 result.recovery=result.injector.get(CommandRecovery);result.recovery.setScope(result.page.sessionScope);return result;
}
test('reassignment preserves immutable exact fields and rejects malformed or changed destinations and reason',()=>{
 const buffer=new CommandBuffer(),source=structuredClone(envelope),frozen=buffer.remember(source);source.body.assignmentRef='changed';source.body.reason='changed';
 assert.equal(frozen.body.assignmentRef,'Finance');assert.equal(frozen.body.reason,envelope.body.reason);
 for(const reason of ['', ' \n', '\ud800', '\udc00','x'.repeat(1001)])assert.throws(()=>freezeEnvelope({...envelope,body:{...envelope.body,reason}}));
 for(const assignmentKind of ['INVALID','user'])assert.throws(()=>freezeEnvelope({...envelope,body:{...envelope.body,assignmentKind}}));
 assert.throws(()=>freezeEnvelope({...envelope,body:{...envelope.body,actorRef:'admin'}}));
 assert.throws(()=>buffer.remember({...envelope,body:{...envelope.body,assignmentRef:'other'}}),/different original/);
});
test('administration defaults OPEN, preserves filtered-empty continuation and resets cursor on fresh filters',async t=>{
 const calls=[];const {page}=component(t,Administration,{administrationTasks:async(...args)=>{calls.push(args);return {items:[],nextCursor:'40'};}});
 await page.load();assert.equal(calls[0][0].status,'OPEN');assert.equal(page.next,'40');
 await page.nextPage();assert.equal(calls[1][1],'40');
 page.filters={status:'CANCELLED',taskType:'REVIEW',assignmentKind:'GROUP',assignmentRef:'Finance',assigneeRef:'reviewer',instanceId:'9007199254740993'};
 await page.load();assert.equal(calls[2][1],undefined);assert.deepEqual({...calls[2][0]},page.filters);
 page.filters={status:'OPEN',assignmentRef:'Finance'};await page.load();assert.equal(calls.length,3);assert.match(page.error(),/assignment kind/);
});
test('editing administration filters invalidates pending rows and cursors before the next page can be requested',async t=>{
 const requests=[];const {page}=component(t,Administration,{administrationTasks:(filters,cursor,limit,signal)=>new Promise(resolve=>requests.push({filters,cursor,signal,resolve}))});
 const openRead=page.load();assert.equal(requests[0].filters.status,'OPEN');
 page.filters.status='COMPLETED';page.filtersChanged();
 assert.equal(requests[0].signal.aborted,true);assert.equal(page.busy(),false);assert.equal(page.next,null);
 requests[0].resolve({items:[{id:'30',status:'OPEN'}],nextCursor:'40'});await openRead;
 assert.equal(page.rows().length,0);assert.equal(page.next,null);page.nextPage();assert.equal(requests.length,1);
 const completedRead=page.load();assert.equal(requests[1].filters.status,'COMPLETED');assert.equal(requests[1].cursor,undefined);
 requests[1].resolve({items:[{id:'50',status:'COMPLETED'}],nextCursor:'60'});await completedRead;
 assert.equal(page.rows()[0].status,'COMPLETED');assert.equal(page.next,'60');
});
test('administration visits server cursors in both directions, including empty pages with continuation',async t=>{
 const calls=[];const pages=new Map([[undefined,{items:[{id:'10'}],nextCursor:'10'}],['10',{items:[],nextCursor:'20'}],['20',{items:[{id:'30'}],nextCursor:null}]]);
 const {page}=component(t,Administration,{administrationTasks:async(filters,cursor,size)=>{calls.push({filters,cursor,size});return pages.get(cursor);}});
 await page.load();assert.equal(page.pageIndex,0);await page.previousPage();assert.equal(calls.length,1);
 await page.nextPage();assert.equal(page.pageIndex,1);assert.equal(page.rows().length,0);assert.equal(page.next,'20');
 await page.nextPage();assert.equal(page.pageIndex,2);assert.equal(page.next,null);await page.nextPage();assert.equal(calls.length,3);
 await page.previousPage();assert.equal(page.pageIndex,1);assert.equal(page.next,'20');
 await page.previousPage();assert.equal(page.pageIndex,0);assert.deepEqual(calls.map(call=>call.cursor),[undefined,'10','20','10',undefined]);
});

test('administration apply, page size, refresh and session changes reset visited navigation without changing exact filters',async t=>{
 const calls=[];const {page}=component(t,Administration,{administrationTasks:async(filters,cursor,size)=>{calls.push({filters,cursor,size});return {items:[],nextCursor:'9007199254740993'};}});
 page.filters={status:'OPEN',assignmentKind:'GROUP',assignmentRef:' Finance ',instanceId:'9007199254740993'};
 await page.load();await page.nextPage();assert.equal(page.pageIndex,1);
 await page.load();assert.equal(page.pageIndex,0);assert.equal(calls.at(-1).cursor,undefined);
 await page.nextPage();await page.changePageSize(20);assert.equal(page.pageSize,20);assert.equal(page.pageIndex,0);assert.equal(calls.at(-1).size,20);assert.equal(calls.at(-1).cursor,undefined);
 const before=calls.length;await page.changePageSize(1);assert.equal(calls.length,before);assert.equal(page.pageSize,20);
 await page.nextPage();page.filters.status='ALL';page.filtersChanged();assert.equal(page.pageIndex,0);assert.equal(page.next,null);
 await page.load();assert.equal(calls.at(-1).filters.assignmentRef,' Finance ');assert.equal(calls.at(-1).filters.instanceId,'9007199254740993');assert.equal(calls.at(-1).cursor,undefined);
 await page.nextPage();page.sessionScope={};page.ngOnChanges({sessionScope:true});await tick();assert.equal(page.pageIndex,0);assert.equal(calls.at(-1).cursor,undefined);
});

test('administration blocks navigation while loading and never advances a failed or invalidated page',async t=>{
 const requests=[];const {page}=component(t,Administration,{administrationTasks:(filters,cursor,size,signal)=>new Promise((resolve,reject)=>requests.push({cursor,size,signal,resolve,reject}))});
 const first=page.load();await page.nextPage();await page.previousPage();await page.changePageSize(20);assert.equal(requests.length,1);assert.equal(page.pageSize,50);
 requests[0].resolve({items:[{id:'10'}],nextCursor:'10'});await first;
 const second=page.nextPage();assert.equal(page.pageIndex,0);await page.nextPage();await page.previousPage();assert.equal(requests.length,2);
 requests[1].reject(Error('page unavailable'));await second;assert.equal(page.pageIndex,0);assert.equal(page.next,null);assert.equal(page.rows().length,0);
 const renewed=page.load();requests[2].resolve({items:[],nextCursor:'20'});await renewed;
 const late=page.nextPage();page.filters.status='CANCELLED';page.filtersChanged();requests[3].resolve({items:[{id:'30'}],nextCursor:'30'});await late;
 assert.equal(requests[3].signal.aborted,true);assert.equal(page.pageIndex,0);assert.equal(page.next,null);assert.equal(page.rows().length,0);
});

test('optional provider, closed task, missing precondition and unavailable lookup fail closed',async t=>{
 for(const [availability,precondition] of [[null,contract.precondition],[{allowed:false},contract.precondition],[{allowed:true},undefined],[{allowed:true,precondition:'different-ciphertext'},contract.precondition]]){
  const {page}=setup(t,availability?{availability:async()=>availability}:null);
  page.api.taskContract=async()=>({...contract,precondition});
  await page.load();assert.equal(page.canReassign(),!!availability?.allowed&&!!precondition);
  page.detail.set({...detail,status:'COMPLETED'});assert.equal(page.canReassign(),false);
 }
 const {page}=setup(t,{availability:async()=>{throw Error('directory unavailable');}});await page.load();assert.equal(page.canReassign(),false);assert.equal(page.detail().status,'OPEN');
});
test('unknown outcome blocks new commands; lookup miss and rejected retry retain original request',async t=>{
 const calls=[];let mode='unknown';const {page,recovery}=setup(t,{availability:async()=>({allowed:true,precondition:envelope.expectedToken}),
  reassign:async(e,replay)=>{calls.push([e,replay]);if(replay)return {found:false,result:null};throw mode==='unknown'?Error('network'):Object.assign(Error('stale'),{httpStatus:409});}});
 await page.load();const frozen=recovery.buffer.remember(envelope);await page.send(frozen);
 assert.equal(page.pendingCommand(),frozen);assert.equal(page.canReassign(),false);
 await page.recover(true);assert.equal(page.recoveryMiss(),true);assert.equal(page.pendingCommand(),frozen);
 mode='rejected';await page.recover(false);assert.equal(page.pendingCommand(),frozen);
 assert.equal(calls.length,3);for(const [e] of calls)assert.equal(e,frozen);
});
test('fresh stale response refreshes; success clears pending; permission loss removes cached inputs',async t=>{
 for(const mode of ['stale','success','lost']){
  let reads=0;const {page,recovery}=setup(t,{availability:async()=>({allowed:true,precondition:envelope.expectedToken}),reassign:async()=>{if(mode==='stale')throw Object.assign(Error('stale'),{httpStatus:409});return {replayed:false,taskStatus:'OPEN',status:'ACTIVE'};}});
  page.api.taskContract=async()=>{reads++;if(mode==='lost'&&reads>1)throw Object.assign(Error('missing'),{httpStatus:404});return {...contract};};
  await page.load();await page.send(recovery.buffer.remember(envelope));
  assert.equal(page.pendingCommand(),undefined);assert.equal(reads,2);
  if(mode==='lost'){assert.equal(page.detail(),null);assert.equal(page.contract(),null);}
  else assert.equal(page.canReassign(),true);
 }
});
test('session replacement suppresses pending result and closes confirmation',async t=>{
 let resolve,calls=0;const {page,recovery}=setup(t,{reassign:async()=>{calls++;return new Promise(r=>resolve=r);}});
 const frozen=recovery.buffer.remember(envelope),pending=page.send(frozen);await tick();await page.send(frozen);assert.equal(calls,1);
 page.sessionScope={};page.ngOnChanges({sessionScope:true});resolve({replayed:false,taskStatus:'OPEN',status:'ACTIVE'});await pending;
 assert.equal(recovery.buffer.entries.length,0);assert.equal(page.notice(),'');
});
test('target search ignores late results after changed kind and confirmation requires current directory choice',async t=>{
 let resolve,closed;const {page}=component(t,Dialog,{},undefined,undefined,[{provide:MAT_DIALOG_DATA,useValue:{task:detail,taskId:'30',version:envelope.expectedToken}},
  {provide:WORKFLOW_TASK_REASSIGNMENT,useValue:{targets:async()=>new Promise(r=>resolve=r)}},{provide:MatDialogRef,useValue:{close:value=>closed=value}}]);
 const pending=page.search();page.kind='ROLE';page.reset();resolve({items:[{ref:'Finance',label:'Finance'}],nextCursor:null});await pending;
 assert.equal(page.items.length,0);page.ref='invented';page.reason='reason';page.confirm();assert.equal(closed,undefined);
 page.items=[{ref:'reviewers',label:'Reviewers'}];page.ref='reviewers';page.reason='';let touched=0;page.confirm({control:{markAsTouched(){touched++;}}});assert.equal(touched,1);assert.equal(closed,undefined);
 page.reason=' \n';page.confirm();assert.equal(closed,undefined);
 page.reason=envelope.body.reason;page.confirm();assert.equal(closed.body.assignmentKind,'ROLE');assert.equal(closed.body.reason,envelope.body.reason);
});

test('unresolved earlier completion blocks reassignment confirmation',async t=>{
 const {page,recovery}=setup(t,{availability:async()=>({allowed:true,precondition:envelope.expectedToken})});await page.load();
 const prior=recovery.buffer.remember({kind:'complete',body:{commandId:envelope.body.commandId,taskId:'30',outcomeKey:'APPROVE',comment:null},expectedToken:envelope.expectedToken});
 recovery.pendingIds.add(prior.body.commandId);assert.equal(page.canReassign(),false);
});

test('applied chips preserve exact values and removing kind also removes its dependent reference from page one',async t=>{
 const calls=[];const {page}=component(t,Administration,{administrationTasks:async(filters,cursor)=>{calls.push({filters,cursor});return {items:[],nextCursor:'40'};}});
 assert.equal(page.filtersExpanded,false);assert.equal(page.appliedChips().length,0);
 page.filters={status:'OPEN',taskType:' REVIEW ',assignmentKind:'GROUP',assignmentRef:' Finance ',instanceId:'9007199254740993'};
 await page.load();assert.deepEqual(Array.from(page.appliedChips(),chip=>chip.value),[' REVIEW ','GROUP',' Finance ','9007199254740993']);
 await page.nextPage();assert.equal(page.pageIndex,1);
 await page.removeFilter('assignmentKind');assert.equal(page.pageIndex,0);assert.equal(calls.at(-1).cursor,undefined);
 assert.equal(page.filters.assignmentKind,undefined);assert.equal(page.filters.assignmentRef,undefined);assert.equal(page.filters.taskType,' REVIEW ');
 await page.removeFilter('instanceId');assert.equal(calls.at(-1).filters.instanceId,undefined);assert.equal(page.appliedChips().length,1);
 page.filters.taskType='draft';assert.equal(page.appliedChips()[0].value,' REVIEW ');
 page.filtersChanged();assert.equal(page.appliedChips().length,0);
});

test('chip removal is guarded during reads and failed or invalidated reads expose no applied chips',async t=>{
 const requests=[];const {page}=component(t,Administration,{administrationTasks:(filters,cursor,size,signal)=>new Promise((resolve,reject)=>requests.push({filters,signal,resolve,reject}))});
 page.filters={status:'OPEN',taskType:'REVIEW'};
 const first=page.load();await page.removeFilter('taskType');assert.equal(requests.length,1);
 requests[0].resolve({items:[],nextCursor:null});await first;assert.equal(page.appliedChips().length,1);
 const removing=page.removeFilter('taskType');assert.equal(requests.length,2);requests[1].reject(Error('read failed'));await removing;assert.equal(page.appliedChips().length,0);
 const stale=page.load();page.filtersChanged();requests[2].resolve({items:[],nextCursor:null});await stale;assert.equal(page.appliedChips().length,0);
});

test('destination search feedback distinguishes pending, loaded pages, empty results, failures and changed queries',async t=>{
 const requests=[];const {page}=component(t,Dialog,{},undefined,undefined,[{provide:MAT_DIALOG_DATA,useValue:{task:detail,taskId:'30',version:envelope.expectedToken}},
  {provide:WORKFLOW_TASK_REASSIGNMENT,useValue:{targets:async()=>new Promise((resolve,reject)=>requests.push({resolve,reject}))}},{provide:MatDialogRef,useValue:{close(){}}}]);
 assert.equal(page.searched,false);
 const first=page.search();assert.equal(page.busy,true);assert.equal(page.searched,false);
 requests[0].resolve({items:[{ref:'one',label:'One'}],nextCursor:'next'});await first;
 assert.equal(page.busy,false);assert.equal(page.searched,true);assert.equal(page.items.length,1);assert.equal(page.next,'next');
 const more=page.search(true);assert.equal(page.searched,false);requests[1].resolve({items:[{ref:'two',label:'Two'}],nextCursor:null});await more;assert.equal(page.searched,true);assert.equal(page.items.length,2);
 page.resetChoices();assert.equal(page.searched,false);assert.equal(page.items.length,0);
 const empty=page.search();requests[2].resolve({items:[],nextCursor:null});await empty;assert.equal(page.searched,true);assert.equal(page.items.length,0);
 const failed=page.search();requests[3].reject(Error('failed'));await failed;assert.equal(page.searched,false);assert.match(page.error,/could not be loaded/);
 const late=page.search();page.resetChoices();requests[4].resolve({items:[{ref:'old',label:'Old'}],nextCursor:null});await late;assert.equal(page.searched,false);assert.equal(page.items.length,0);
});

test('task name filter is preserved exactly in reads and chips and removal resets the cursor',async t=>{
 const calls=[];const {page}=component(t,Administration,{administrationTasks:async(filters,cursor)=>{calls.push({filters,cursor});return {items:[],nextCursor:'40'};}});
 page.filters={status:'OPEN',taskName:' Review 100%_ ',instanceId:'64'};await page.load();await page.nextPage();
 assert.equal(calls.at(-1).filters.taskName,' Review 100%_ ');assert.equal(page.appliedChips()[0].label,'Task name');assert.equal(page.appliedChips()[0].value,' Review 100%_ ');
 await page.removeFilter('taskName');assert.equal(calls.at(-1).cursor,undefined);assert.equal(calls.at(-1).filters.taskName,undefined);assert.equal(page.filters.instanceId,'64');assert.equal(page.pageIndex,0);
});
