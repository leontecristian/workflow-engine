import {test} from 'node:test';
import assert from 'node:assert/strict';
import '@angular/compiler';
import {MatDialog} from '@angular/material/dialog';
import {Subject} from 'rxjs';
import {loadTs,component,tick} from '../../../../unit/component-loader.mjs';
const {WorkflowInstanceViewerComponent:Viewer}=loadTs(new URL('./instance-viewer.ts',import.meta.url));
const {WORKFLOW_CANCELLATION}=loadTs(new URL('./runtime/contracts.ts',import.meta.url));
const {CommandRecovery}=loadTs(new URL('./runtime/command/command-recovery.ts',import.meta.url));
const {CommandBuffer,freezeEnvelope}=loadTs(new URL('./runtime/command/command-buffer.ts',import.meta.url));
const id='9007199254740993',commandId='12345678-1234-1234-1234-123456789abc';
const envelope={kind:'cancel',body:{commandId,instanceId:id,reason:'  exact reason\n😀  '},expectedToken:'9007199254740995'};
const instance={id,definitionVersionId:'7',definitionKey:'review',definitionVersion:1,definitionHash:'hash',status:'ACTIVE',currentNodeKey:'review',resultKey:null,businessType:null,businessId:null,startedAt:'2026-11-07T00:00:00Z',startedBy:'actor',endedAt:null,endedBy:null,terminationReason:null,lastHistorySequence:'1',lockVersion:envelope.expectedToken};
const graph={instance,nodes:[{key:'review',name:'Review',type:'HUMAN_TASK',state:'ACTIVE',activityId:'3',taskId:'4'}],edges:[]};
const reads={instance:async()=>instance,graph:async()=>graph};
function setup(t,transport,extra=[]){const result=component(t,Viewer,reads,null,[{provide:WORKFLOW_CANCELLATION,useValue:transport},...extra]);result.page.instanceId=id;result.recovery=result.injector.get(CommandRecovery);result.recovery.setScope(result.page.sessionScope);return result;}
test('cancellation freezes exact reason, identifiers and version; rejects task/client fields and invalid Unicode',()=>{
 const buffer=new CommandBuffer(),source=structuredClone(envelope),frozen=buffer.remember(source);source.body.reason='changed';
 assert.equal(frozen.body.reason,envelope.body.reason);assert.ok(Object.isFrozen(frozen.body));
 for(const reason of ['', ' \n', '\ud800', '\udc00','x'.repeat(2001)])assert.throws(()=>freezeEnvelope({...envelope,body:{...envelope.body,reason}}));
 for(const extra of [{taskId:'3'},{actorRef:'admin'}])assert.throws(()=>freezeEnvelope({...envelope,body:{...envelope.body,...extra}}));
 assert.equal(freezeEnvelope({...envelope,body:{...envelope.body,reason:'x'.repeat(2000)}}).body.reason.length,2000);
 assert.throws(()=>buffer.remember({...envelope,body:{...envelope.body,reason:'other'}}),/different original/);
});
test('viewer requires optional transport and matching allowed availability; read errors fail closed',async t=>{
 for(const availability of [null,{allowed:false,lockVersion:instance.lockVersion},{allowed:true,lockVersion:'1'},{allowed:true,lockVersion:instance.lockVersion}]){
  const transport=availability?{availability:async()=>availability}:null;
  const {page}=setup(t,transport);await page.load();assert.equal(!!page.canCancel(),!!availability?.allowed&&availability.lockVersion===instance.lockVersion);
 }
 const {page}=setup(t,{availability:async()=>{throw Error('permission lookup failed');}});await page.load();assert.equal(page.canCancel(),false);
});
test('unknown outcome retains original across lookup miss, lookup rejection and rejected explicit retry',async t=>{
 const calls=[];let mode='unknown';const {page,recovery}=setup(t,{
  availability:async()=>({allowed:true,lockVersion:instance.lockVersion}),
  async cancel(e){calls.push(['cancel',e]);if(mode==='unknown')throw Error('network');throw Object.assign(Error('stale'),{httpStatus:409});},
  async replay(e){calls.push(['lookup',e]);if(mode==='denied')throw Object.assign(Error('denied'),{httpStatus:403});return {found:false,result:null};}
 });await page.load();const frozen=recovery.buffer.remember(envelope);
 await page.sendCancellation(frozen,false);assert.equal(page.canCancel(),false);assert.equal(page.pendingCancellation(),frozen);assert.equal(calls.length,1);
 await page.sendCancellation(frozen,true);assert.equal(page.lookupMiss(),true);assert.equal(page.pendingCancellation(),frozen);
 mode='denied';await page.sendCancellation(frozen,true);assert.equal(page.pendingCancellation(),frozen);
 mode='stale';await page.sendCancellation(frozen,false);assert.equal(page.pendingCancellation(),frozen);
 for(const [,e] of calls)assert.equal(e,frozen);
});
test('definite fresh rejection refreshes availability and requires a new confirmation',async t=>{
 let count=0;const {page,recovery}=setup(t,{availability:async()=>{count++;return {allowed:true,lockVersion:instance.lockVersion};},cancel:async()=>{throw Object.assign(Error('stale'),{httpStatus:409});}});
 await page.load();await page.sendCancellation(recovery.buffer.remember(envelope),false);assert.equal(count,2);assert.equal(page.pendingCancellation(),null);assert.equal(page.canCancel(),true);
});
test('pending command prevents duplicates and late responses cannot enter replacement session',async t=>{
 let resolve,calls=0;const {page,recovery}=setup(t,{cancel:async()=>{calls++;return new Promise(r=>resolve=r);}});
 const frozen=recovery.buffer.remember(envelope),pending=page.sendCancellation(frozen,false);await tick();
 await page.sendCancellation(frozen,false);assert.equal(calls,1);
 page.sessionScope={};page.ngOnChanges({sessionScope:true});resolve({replayed:false,execution:{instance}});await pending;
 assert.equal(recovery.buffer.entries.length,0);assert.equal(recovery.result(),null);assert.equal(page.commandNotice(),'');
});
test('Material confirmation preserves reason and cannot submit after a session replacement',async t=>{
 const closed=new Subject();let closeCount=0,calls=0;
 const {page}=setup(t,{availability:async()=>({allowed:true,lockVersion:instance.lockVersion}),cancel:async()=>{calls++;return {replayed:false,execution:{instance}};}},[
  {provide:MatDialog,useValue:{open(){return {afterClosed:()=>closed,close(){closeCount++;}};}}}
 ]);await page.load();await page.confirmCancellation();page.sessionScope={};page.ngOnChanges({sessionScope:true});closed.next(envelope.body.reason);await tick();
 assert.equal(calls,0);assert.equal(closeCount,1);
});
test('cancelled graph validates and labels stopped activity without inventing a completed node or edge',()=>{
 const {validateGraph}=loadTs(new URL('./read-validation.ts',import.meta.url));
 const stopped={instance:{...instance,status:'CANCELLED',terminationReason:'<script>text</script>',endedBy:'admin',endedAt:'2026-11-14T12:00:00Z'},nodes:[{...graph.nodes[0],state:'CANCELLED'}],edges:[]};
 assert.doesNotThrow(()=>validateGraph(stopped,id));assert.equal(stopped.nodes[0].state,'CANCELLED');assert.equal(stopped.edges.length,0);
});
test('lazy confirmation cannot open after destruction and opening failure permits a new attempt',async t=>{
 let opens=0;const transport={availability:async()=>({allowed:true,lockVersion:instance.lockVersion})};
 const {page}=setup(t,transport,[{provide:MatDialog,useValue:{open(){opens++;throw Error('module failure');}}}]);await page.load();
 await page.confirmCancellation();assert.equal(opens,1);assert.match(page.commandNotice(),/could not be opened/);
 await page.confirmCancellation();assert.equal(opens,2);
 const late=page.confirmCancellation();page.ngOnDestroy();await late;assert.equal(opens,2);
});
test('session replacement during post-command refresh cannot receive old success or rejection feedback',async t=>{
 for(const rejected of [false,true]){
  const {page,recovery}=setup(t,{cancel:async()=>{if(rejected)throw Object.assign(Error('stale'),{httpStatus:409});return {replayed:false,execution:{instance}};}});
  let release;page.load=()=>new Promise(r=>release=r);
  const pending=page.sendCancellation(recovery.buffer.remember(envelope),false);await tick();
  const finishOld=release;page.sessionScope={};page.ngOnChanges({sessionScope:true});finishOld();await pending;assert.equal(page.commandNotice(),'');
 }
});
