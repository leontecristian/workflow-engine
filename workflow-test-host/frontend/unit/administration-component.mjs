import { loadTs, core, router, rxjs, routeAt, tick } from './component-loader.mjs';
export { loadTs, router, rxjs, routeAt, tick };
import { WORKFLOW_ADMINISTRATION_API, WORKFLOW_VIEWER_NAVIGATION } from '@workflow-engine/ui';
export function component(t,Type,api={},route=routeAt(),navigate={calls:[],async navigateByUrl(parts){this.calls.push({parts:Array.from(parts)});return true;}},extra=[]){
 const injector=core.createEnvironmentInjector([{provide:WORKFLOW_ADMINISTRATION_API,useValue:api},{provide:WORKFLOW_VIEWER_NAVIGATION,useValue:{destination:i=>[i.kind,i.id]}},{provide:router.Router,useValue:navigate},...extra],null);
 const page=core.runInInjectionContext(injector,()=>new Type());page.sessionScope={};
 const identity='versionId' in page?'versionId':'definitionId' in page?'definitionId':null;
 Object.defineProperty(page,'id',{get(){return identity?this[identity]:'';},set(value){if(identity)this[identity]=value;},configurable:true});
 let subscription;
 page.ngOnInit=()=>{if(identity)subscription=route.paramMap.subscribe(params=>{page[identity]=params.get('id')||'';page.ngOnChanges({[identity]:true});});else page.ngOnChanges({sessionScope:true});};
 injector.onDestroy(()=>subscription?.unsubscribe());t.after(()=>{if(!injector.destroyed)injector.destroy();});return {page,injector,route,router:navigate};
}
