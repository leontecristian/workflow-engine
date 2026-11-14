import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,component,rxjs,tick} from '../../../../unit/administration-component.mjs';
import {MatDialog} from '@angular/material/dialog';
const {WorkflowDefinitionVersionComponent: VersionPage}=loadTs(new URL('../../../../../../workflow-ui/projects/workflow-ui/administration/src/lib/definition/version.ts',import.meta.url));
function fixture(t,api={},navigate) {
  let closed; const dialogs=[];
  const dialog={open(_type,options){closed=new rxjs.Subject();const subject=closed;dialogs.push(options);return {afterClosed:()=>subject,close(){subject.next();subject.complete();}};}};
  const result=component(t,VersionPage,api,undefined,navigate,[{provide:MatDialog,useValue:dialog}]);
  const page=result.page;page.id='9007199254740993';
  page.detail.set({summary:{id:page.id,definitionId:'12',key:'review',version:3,status:'DRAFT',lockVersion:'9007199254740995'},documentJson:'{}'});
  page.document='unsaved text';
  return {...result,dialogs,close(value){closed.next(value);closed.complete();}};
}
test('creation blocks dirty state; confirmation identifies saved source, cancellation never writes',async t=>{
  let calls=0;const f=fixture(t,{async createDraftFromVersion(){calls++;}});
  f.page.dirty=true;await f.page.confirmDraftAction('create');assert.equal(f.dialogs.length,0);
  f.page.dirty=false;await f.page.confirmDraftAction('create');await f.page.confirmDraftAction('create');
  assert.equal(f.dialogs.length,1);assert.equal(f.dialogs[0].data.key,'review');assert.equal(f.dialogs[0].data.version,3);
  f.close(false);await tick();assert.equal(calls,0);assert.equal(f.page.document,'unsaved text');
});
test('confirmed create uses source token once while pending and preserves success on navigation rejection',async t=>{
  let finish;const calls=[];
  const f=fixture(t,{createDraftFromVersion(...args){calls.push(args);return new Promise(resolve=>finish=resolve);}}, {async navigateByUrl(){return false;}});
  await f.page.confirmDraftAction('create');f.close(true);await tick();
  await f.page.confirmDraftAction('create');assert.equal(calls.length,1);assert.equal(calls[0][0],f.page.id);assert.equal(calls[0][1],'9007199254740995');
  finish({after:{id:'20',key:'review',version:8},operation:'CREATE_DRAFT_FROM_VERSION'});await tick();await tick();
  assert.match(f.page.notice(),/version 8.*ID 20/);assert.match(f.page.error(),/succeeded.*navigation failed/);
  assert.equal(f.page.completedDestination().id,'20');assert.equal(f.page.document,'unsaved text');
});
test('dirty deletion cancellation preserves edits, confirmed success clears obsolete content with family fallback',async t=>{
  let calls=0;const f=fixture(t,{async deleteDraft(){calls++;return {before:{id:'9'},operation:'DELETE_DRAFT',context:{}};}},{async navigateByUrl(){throw new Error('routing failed');}});
  f.page.dirty=true;await f.page.confirmDraftAction('delete');f.close();await tick();assert.equal(f.page.document,'unsaved text');assert.equal(calls,0);
  await f.page.confirmDraftAction('delete');f.close(true);await tick();await tick();
  assert.equal(calls,1);assert.equal(f.page.document,'');assert.equal(f.page.detail(),null);assert.equal(f.page.dirty,false);
  assert.equal(f.page.completedDestination().kind,'definition');assert.equal(f.page.completedDestination().id,'12');assert.match(f.page.notice(),/Deleted review version 3/);
});
test('uncertain create and delete retain text and freeze repeat attempts without retry',async t=>{
  for(const action of ['create','delete']) {
    let calls=0;const f=fixture(t,{async createDraftFromVersion(){calls++;throw new Error('transport');},async deleteDraft(){calls++;throw new Error('transport');}});
    await f.page.confirmDraftAction(action);f.close(true);await tick();
    assert.equal(f.page.outcomeUnknown(),true);assert.match(f.page.error(),/may have succeeded/);assert.equal(f.page.document,'unsaved text');
    await f.page.confirmDraftAction(action);assert.equal(calls,1);
  }
});
test('stale and ordinary command errors preserve editor and never report success',async t=>{
  for(const status of [400,409,404]) {
    const f=fixture(t,{async deleteDraft(){throw Object.assign(new Error('rejected'),{httpStatus:status});}});
    f.page.dirty=true;await f.page.confirmDraftAction('delete');f.close(true);await tick();
    assert.equal(f.page.document,'unsaved text');assert.equal(f.page.dirty,true);assert.equal(f.page.completedDestination(),null);assert.equal(f.page.outcomeUnknown(),false);
    if(status===409){await f.page.confirmDraftAction('delete');assert.equal(f.dialogs.length,1);assert.equal(f.page.refreshRequired(),true);}
  }
});
test('immutable sources permit create but never delete; access denial clears private text',async t=>{
  for(const status of ['PUBLISHED','RETIRED']) {
    const f=fixture(t);f.page.detail().summary.status=status;
    await f.page.confirmDraftAction('delete');assert.equal(f.dialogs.length,0);
    await f.page.confirmDraftAction('create');assert.equal(f.dialogs.length,1);f.close(false);
  }
  const f=fixture(t,{async deleteDraft(){throw Object.assign(new Error('denied'),{httpStatus:403});}});
  await f.page.confirmDraftAction('delete');f.close(true);await tick();
  assert.equal(f.page.document,'');assert.equal(f.page.detail(),null);assert.match(f.page.error(),/access denied/i);
});
test('session replacement closes dialog and suppresses late create feedback/navigation',async t=>{
  let finish;const f=fixture(t,{createDraftFromVersion(){return new Promise(resolve=>finish=resolve);}});
  await f.page.confirmDraftAction('create');f.close(true);await tick();
  f.page.load=async()=>{};f.page.sessionScope={};f.page.ngOnChanges({sessionScope:true});
  finish({after:{id:'20',key:'private',version:8}});await tick();
  assert.equal(f.page.notice(),'');assert.equal(f.page.detail(),null);assert.equal(f.router.calls.length,0);
  const g=fixture(t);await g.page.confirmDraftAction('delete');g.injector.destroy();g.close(true);assert.equal(g.page.notice(),'');
});
