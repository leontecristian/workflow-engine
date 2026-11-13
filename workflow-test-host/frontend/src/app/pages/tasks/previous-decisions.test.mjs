import {test} from 'node:test';
import assert from 'node:assert/strict';
import {component,runtimeType,tick} from '../../../../unit/runtime-component.mjs';
const {WorkflowTaskComponent:Task}=runtimeType('task');
const decision={taskId:'9007199254740993',activityId:'2',historySequence:'9',nodeKey:'MANAGER',nodeName:'Manager',outcomeKey:'APPROVE',outcomeLabel:'Approve',actorKind:'USER',actorRef:'actual-user',occurredAt:'2026-11-13T00:00:00Z',comment:' <script>unsafe</script>\n  exact '};
function api(previousDecisions,mode='ALL_PREVIOUS'){
 return {task:async()=>({id:'5',status:'COMPLETED'}),taskContract:async()=>({previousDecisions:{mode},outcomes:[]}),taskSubmission:async()=>null,previousDecisions};
}
test('completed task appends exact previous decisions and preserves entries on failed continuations',async t=>{
 const calls=[];let failure=true;
 const reads=api(async(...args)=>{calls.push(args);if(calls.length===1)return {items:[decision],nextAfterSequence:'9'};if(failure)throw Error('failed');return {items:[{...decision,historySequence:'12',comment:null}],nextAfterSequence:null};});
 const {page}=component(t,Task,reads);page.id='5';
 await page.load();assert.equal(page.previousDecisions()[0].comment,decision.comment);assert.equal(page.previousCursor(),'9');
 await page.loadPreviousDecisions(true);assert.equal(page.previousDecisions().length,1);assert.match(page.previousError(),/could not be loaded/);assert.equal(page.previousCursor(),'9');
 failure=false;await page.loadPreviousDecisions(true);assert.equal(calls[2][1],'9');assert.equal(page.previousDecisions().length,2);assert.equal(page.previousCursor(),null);assert.equal(page.previousError(),'');assert.equal(page.noPreviousComment(null),true);assert.equal(page.noPreviousComment('  \n'),true);
});
test('NONE avoids the request while enabled empty and failed states remain distinct',async t=>{
 let calls=0;const {page}=component(t,Task,api(async()=>{calls++;return {items:[],nextAfterSequence:null};},'NONE'));await page.load();assert.equal(page.hasPreviousDecisions,false);assert.equal(calls,0);
 page.api.taskContract=async()=>({previousDecisions:{mode:'SELECTED'},outcomes:[]});await page.load();assert.equal(calls,1);assert.equal(page.previousDecisions().length,0);assert.equal(page.previousError(),'');
 page.api.previousDecisions=async()=>{throw Error('failed');};await page.loadPreviousDecisions();assert.match(page.previousError(),/could not be loaded/);assert.equal(page.detail().status,'COMPLETED');
});
test('task/session changes and destruction reject uncancellable late previous decisions',async t=>{
 for(const change of ['task','session','destroy']){
  let resolve,signal;
  const {page,injector}=component(t,Task,api((id,cursor,limit,s)=>{signal=s;return new Promise(r=>resolve=r);}));page.id='5';
  const pending=page.load();await tick();
  if(change==='destroy')injector.destroy();
  else if(change==='task'){page.id='6';page.sessionScope=null;page.ngOnChanges({id:true});}
  else {page.sessionScope=null;page.ngOnChanges({sessionScope:true});}
  assert.equal(signal.aborted,true);resolve({items:[decision],nextAfterSequence:null});await pending;assert.equal(page.previousDecisions().length,0);assert.equal(page.contract(),null);
 }
});
test('receiving-task access loss clears disclosed comments',async t=>{
 const {page}=component(t,Task,api(async()=>({items:[decision],nextAfterSequence:'9'})));await page.load();
 page.api.previousDecisions=async()=>{throw Object.assign(Error('denied'),{httpStatus:403});};await page.loadPreviousDecisions(true);
 assert.equal(page.previousDecisions().length,0);assert.equal(page.contract(),null);assert.match(page.error(),/unavailable/);
});

test('previous-decision loading has independent state in a read-only host',async t=>{
 const {WORKFLOW_RUNTIME_COMMANDS}=await import('@workflow-engine/ui');
 let resolve;
 const {page}=component(t,Task,api(()=>new Promise(r=>resolve=r)),undefined,undefined,[{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:null}]);
 const pending=page.load();await tick();assert.equal(page.commandProvider,null);assert.equal(page.busy(),false);assert.equal(page.previousBusy(),true);assert.equal(page.detail().status,'COMPLETED');
 resolve({items:[decision],nextAfterSequence:null});await pending;assert.equal(page.previousBusy(),false);assert.equal(page.previousDecisions().length,1);
});

test('failed completion preserves an independent pending previous-decision request',async t=>{
 const {CommandRecovery,CommandBuffer}=await import('../../../../unit/runtime-component.mjs');
 for(const failure of ['required-comment','host-validation']){
  let resolve,signal,executed=0;
  const reads=api((id,cursor,limit,s)=>{signal=s;return new Promise(r=>resolve=r);});
  reads.task=async()=>({id:'5',instanceId:'1',status:'OPEN'});
  reads.taskContract=async()=>({previousDecisions:{mode:'ALL_PREVIOUS'},instanceLockVersion:'2',canComplete:true,outcomes:[{key:'APPROVE',label:'Approve',requiresComment:true}]});
  const commands={buffer:new CommandBuffer(),observations:new Map(),async execute(){executed++;throw Object.assign(Error('Host rejected input'),{httpStatus:422});}};
  const {page}=component(t,Task,reads,undefined,undefined,[{provide:CommandRecovery,useValue:commands}]);page.id='5';
  const pending=page.load();await tick();assert.equal(page.previousBusy(),true);assert.equal(page.busy(),false);
  page.comment=failure==='host-validation'?'Accepted local comment':'';
  await page.complete('APPROVE');
  assert.equal(executed,failure==='host-validation'?1:0);
  assert.match(page.error(),failure==='host-validation'?/Host rejected input/:/comment is required/);
  assert.equal(page.detail().status,'OPEN');assert.equal(signal.aborted,false);assert.equal(page.previousBusy(),true);
  resolve({items:[decision],nextAfterSequence:'9'});await pending;
  assert.equal(page.previousDecisions()[0].comment,decision.comment);assert.equal(page.previousCursor(),'9');assert.equal(page.previousBusy(),false);assert.equal(page.previousError(),'');
 }
});
