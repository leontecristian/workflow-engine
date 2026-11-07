import {component as create,loadTs,core,router,routeAt,navigation} from './component-loader.mjs';
import {WORKFLOW_RUNTIME_READS,WORKFLOW_RUNTIME_COMMANDS,WORKFLOW_VIEWER_READS,WORKFLOW_VIEWER_NAVIGATION,CommandRecovery,CommandBuffer} from '@workflow-engine/ui';
export {loadTs,core,router,routeAt,CommandRecovery,CommandBuffer};
export {tick} from './component-loader.mjs';
export function runtimeType(name){return loadTs(new URL('../../../workflow-ui/projects/workflow-ui/runtime/src/lib/'+name+'.ts',import.meta.url));}
export function component(t,Type,api={},route=routeAt(),navigate=navigation(),extra=[]){
 navigate.navigateByUrl=target=>navigate.navigate(target.parts);
 const navigationAdapter={destination(intent){return {parts:['/'+({instanceTasks:'instance-tasks'}[intent.kind]||intent.kind),...('id' in intent?[intent.id]:'instanceId' in intent?[intent.instanceId]:[])]};}};
 const created=create(t,Type,api,route,navigate,[
  {provide:WORKFLOW_RUNTIME_READS,useValue:api},{provide:WORKFLOW_VIEWER_READS,useValue:api},
  {provide:WORKFLOW_RUNTIME_COMMANDS,useValue:{}},{provide:WORKFLOW_VIEWER_NAVIGATION,useValue:navigationAdapter},
  {provide:CommandRecovery,useValue:{buffer:new CommandBuffer(),result:core.signal(null),execution:()=>null,setScope(){}}},...extra]);
 created.page.sessionScope={};created.page.id=route.paramMap.value.get('id')||'';
 return created;
}
