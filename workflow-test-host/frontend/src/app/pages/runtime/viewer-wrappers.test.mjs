import {test} from 'node:test';
import assert from 'node:assert/strict';
import {loadTs,component,routeAt,router} from '../../../../unit/component-loader.mjs';
const {ConsoleSession}=loadTs(new URL('../../data/console-session.ts',import.meta.url));
for(const [file,name] of [['history','HistoryPage'],['history-event','HistoryEventPage']]){
  test(`${name} passes exact route inputs and the host session to the shared component`,t=>{
    const Type=loadTs(new URL(`./${file}.ts`,import.meta.url))[name];
    const session={viewerScope:()=>({})};
    const {page,route,injector}=component(t,Type,{},routeAt({id:'9007199254740993',sequence:'9007199254740995'}),undefined,[{provide:ConsoleSession,useValue:session}]);
    page.ngOnInit();assert.equal(page.id,'9007199254740993');assert.equal(page.sequence,'9007199254740995');assert.equal(page.session,session);
    route.paramMap.next(router.convertToParamMap({id:'9007199254740997',sequence:'9007199254740999'}));
    assert.equal(page.id,'9007199254740997');assert.equal(page.sequence,'9007199254740999');
    injector.destroy();route.paramMap.next(router.convertToParamMap({id:'2',sequence:'3'}));assert.equal(page.id,'9007199254740997');
  });
}
