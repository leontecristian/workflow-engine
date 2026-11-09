import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,component,tick} from '../../../../unit/component-loader.mjs';
const {WorkflowInstanceViewerComponent:Viewer}=loadTs(new URL('./instance-viewer.ts',import.meta.url));
const {WorkflowHistoryComponent:History}=loadTs(new URL('./history.ts',import.meta.url));
const {WorkflowHistoryEventComponent:Event}=loadTs(new URL('./history-event.ts',import.meta.url));
const {WorkflowInstanceGraphComponent:Graph}=loadTs(new URL('./graph/instance-graph.ts',import.meta.url));
const {DEFAULT_VIEWER_MESSAGES:messages,ViewerReadError,normalizeViewerReadError}=loadTs(new URL('./contracts.ts',import.meta.url));
const id='9007199254740993', sequence='9007199254740995';
const instance={id,definitionVersionId:'7',definitionKey:'review',definitionVersion:1,definitionHash:'hash',status:'ACTIVE',currentNodeKey:'START',resultKey:null,businessType:null,businessId:null,startedAt:'2026-11-07T00:00:00Z',startedBy:'actor',endedAt:null,endedBy:null,terminationReason:null,lastHistorySequence:'1',lockVersion:'1'};
const graph={instance,nodes:[{key:'START',name:'<script>label</script>',type:'START',state:'ACTIVE',activityId:null,taskId:null}],edges:[]};
const summary={instanceId:id,historySequence:sequence,eventType:'UNKNOWN_FUTURE',commandId:'uuid',correlationId:null,actorKind:'USER',actorRef:'actor',occurredAt:instance.startedAt,activityId:null,taskId:null,sourceNodeKey:null,targetNodeKey:null,transitionKey:null,detailsSchemaVersion:1};
const exact='{"n":9007199254740993,"d":12500.00,"nil":null}';
test('graph denial clears composite; ordinary failure preserves independently authorized metadata',async t=>{
  for(const category of ['NOT_FOUND','UNAUTHENTICATED','FAILED','LIMIT_EXCEEDED']){
    const {page}=component(t,Viewer,{instance:async()=>instance,graph:async()=>{throw new ViewerReadError(category);}});
    await page.load();
    assert.equal(page.detail(),['NOT_FOUND','UNAUTHENTICATED'].includes(category)?null:instance);
    assert.equal(page.graph(),null);assert.equal(page.busy(),false);
  }
});
test('same-user session replacement, logout, input changes and destroy invalidate uncancellable late reads',async t=>{
  for(const invalidate of [p=>{p.sessionScope={};p.ngOnChanges({sessionScope:true});},p=>{p.sessionScope=null;p.ngOnChanges({sessionScope:true});},p=>{p.instanceId='2';p.ngOnChanges({instanceId:true});},p=>p.ngOnDestroy()]){
    let release,signal;
    const {page}=component(t,Viewer,{instance:async()=>instance,graph:(_,s)=>{signal=s;return new Promise(r=>release=r);}});
    const pending=page.load();await tick();invalidate(page);assert.equal(signal.aborted,true);
    release(graph);await pending;assert.equal(page.graph(),null);
  }
});
test('history owns only summary requests, preserves exact exclusive cursors and latest page wins',async t=>{
  const calls=[],pending=[];
  const {page}=component(t,History,{history:(...args)=>{calls.push(args);return new Promise(r=>pending.push(r));}});
  const first=page.load();pending[0]({items:[summary],nextAfterSequence:sequence});await first;
  assert.equal(page.next(),sequence);
  const second=page.load(sequence), third=page.load('0');
  pending[2]({items:[],nextAfterSequence:null});await third;
  pending[1]({items:[{...summary,historySequence:'9007199254740996'}],nextAfterSequence:null});await second;
  assert.equal(calls[1][0],id);assert.equal(calls[1][1],sequence);assert.equal(calls[1][2],50);
  assert.equal(page.rows().length,0);assert.equal(page.next(),null);
});
test('history can advance past hidden events without accepting backward or stalled cursors',async t=>{
  const calls=[];
  const sourceNext='9007199254740996', emptyNext='9007199254740997';
  const responses=[
    {items:[summary],nextAfterSequence:sourceNext},
    {items:[],nextAfterSequence:emptyNext},
    {items:[{...summary,historySequence:'9007199254740998'}],nextAfterSequence:null},
    {items:[],nextAfterSequence:emptyNext},
    {items:[{...summary,historySequence:'9007199254740998'}],nextAfterSequence:emptyNext},
  ];
  const {page}=component(t,History,{history:async(_,after)=>{calls.push(after);return responses.shift();}});
  await page.load();
  assert.equal(page.rows().length,1);assert.equal(page.next(),sourceNext);
  await page.load(page.next());
  assert.equal(page.rows().length,0);assert.equal(page.next(),emptyNext);assert.equal(page.error(),null);
  await page.load(page.next());
  assert.deepEqual(calls,['0',sourceNext,emptyNext]);assert.equal(page.rows().length,1);assert.equal(page.next(),null);
  await page.load(emptyNext);
  assert.equal(page.error().category,'FAILED');
  await page.load(sourceNext);
  assert.equal(page.error().category,'FAILED');
});
test('exact event retains input IDs, unknown event types and raw downloadable number spelling',async t=>{
  const calls=[];const {page}=component(t,Event,{historyEvent:async(...args)=>{calls.push(args);return {summary,detailsJson:exact};}});
  page.sequence=sequence;await page.load();assert.equal(calls[0][0],id);assert.equal(calls[0][1],sequence);
  assert.equal(page.detail().detailsJson,exact);assert.equal(page.routing(),null);
  let downloaded,revoked;
  const original={document:globalThis.document,create:URL.createObjectURL,revoke:URL.revokeObjectURL};
  t.after(()=>{globalThis.document=original.document;URL.createObjectURL=original.create;URL.revokeObjectURL=original.revoke;});
  URL.createObjectURL=blob=>{downloaded=blob;return 'blob:exact';};URL.revokeObjectURL=url=>revoked=url;
  globalThis.document={createElement:()=>({click(){}})};page.exportDetails();
  assert.equal(await downloaded.text(),exact);assert.equal(revoked,'blob:exact');
});
test('history/event clear stale protected content and do not display cancellations',async t=>{
  for(const Type of [History,Event]){
    let release;const reads=Type===History?{history:()=>new Promise(r=>release=r)}:{historyEvent:()=>new Promise(r=>release=r)};
    const {page}=component(t,Type,reads);page.sequence=sequence;
    const pending=page.load();page.sessionScope=null;page.ngOnChanges({sessionScope:true});
    release(Type===History?{items:[summary],nextAfterSequence:null}:{summary,detailsJson:exact});await pending;
    assert.equal(Type===History?page.rows().length:page.detail(),Type===History?0:null);
    page.sessionScope={};page.reads[Type===History?'history':'historyEvent']=async()=>{throw new ViewerReadError('CANCELLED');};
    await page.load();assert.equal(page.error(),null);
  }
});
test('malformed response is a FAILED read; no command recovery wording or automatic retry',async t=>{
  let calls=0;const {page}=component(t,History,{history:async()=>{calls++;return {items:[{historySequence:3}],nextAfterSequence:null};}});
  await page.load();assert.equal(page.error().category,'FAILED');assert.equal(page.rows().length,0);assert.equal(calls,1);
  for(const [error,expected] of [[{httpStatus:401},'UNAUTHENTICATED'],[{httpStatus:403},'NOT_FOUND'],[{httpStatus:404},'NOT_FOUND'],[{code:'LIMIT_EXCEEDED'},'LIMIT_EXCEEDED'],[{name:'AbortError'},'CANCELLED'],[new Error('network'),'FAILED']])assert.equal(normalizeViewerReadError(error).category,expected);
});
test('two same-instance graphs have private markers, optional navigation and reactive messages',t=>{
  const a=component(t,Graph,{},null).page,b=component(t,Graph,{},null).page;
  a.graph=b.graph=()=>graph;
  assert.notEqual(a.arrowId(true),b.arrowId(true));assert.equal(a.route(graph.nodes[0]),null);
  a.messages=()=>({...messages,current:'Étape actuelle'});assert.equal(a.label(graph.nodes[0]),'Étape actuelle');
  assert.equal(b.label(graph.nodes[0]),'Current step');a.changeZoom(.25);assert.equal(b.zoom(),.5);
});
