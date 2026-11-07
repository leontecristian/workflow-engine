import { test } from 'node:test';
import assert from 'node:assert/strict';
import { services, ConsoleSession, CommandRecovery, core } from '../../../unit/component-loader.mjs';
import { WORKFLOW_RUNTIME_COMMANDS } from '@workflow-engine/ui';
const original={kind:'complete',body:{commandId:'12345678-1234-1234-1234-123456789abc',instanceId:'9007199254740993',taskId:'9223372036854775807',outcomeKey:'COMPLETE',comment:'Original'},expectedToken:'9007199254740999'};
function recovery(t,executeCommand){
 const session={session:core.signal({actorRef:'reviewer'})};
 const injector=services(t,[{provide:ConsoleSession,useValue:session},{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:{complete:(e)=>executeCommand(e,false),replayComplete:(e)=>executeCommand(e,true)}}]);
 const commands=injector.get(CommandRecovery);commands.setScope({});return {commands,session};
}
test('recovery survives page replacement and retries the original envelope',async t=>{
 const calls=[];const {commands}=recovery(t,async(e,replay)=>{calls.push({e,replay});return replay?{found:true,result:{replayed:true,execution:{instance:{id:'1'}}}}:{replayed:false,execution:{instance:{id:'1'}}};});
 commands.buffer.remember(original);await commands.execute(false);const first=commands.result();commands.select(original.body.commandId);await commands.execute(true);
 assert.equal(calls[0].e,calls[1].e);assert.equal(calls[1].e.expectedToken,original.expectedToken);assert.equal(calls[1].replay,true);assert.equal(first.outcome,'NEW_COMMITTED_RESULT');assert.equal(commands.result().outcome,'REPLAY_ONLY_LOOKUP');assert.equal(commands.execution().instance.id,'1');
});
test('unknown outcomes retain original input; session replacement clears all private recovery state',async t=>{
 const {commands}=recovery(t,async()=>{throw new Error('network');});commands.buffer.remember(original);await assert.rejects(commands.execute(false),/network/);
 assert.equal(commands.result().outcome,'UNKNOWN_OUTCOME');assert.equal(commands.buffer.command.expectedToken,original.expectedToken);commands.setScope({});assert.equal(commands.buffer.command,null);assert.equal(commands.observations.size,0);assert.equal(commands.result(),null);
});
test('late command responses cannot repopulate buffers after logout or same-user replacement',async t=>{
 let resolve;const {commands}=recovery(t,()=>new Promise(r=>resolve=r));commands.buffer.remember(original);const pending=commands.execute(false);commands.setScope(null);commands.setScope({});resolve({replayed:false,execution:{}});await pending;assert.equal(commands.result(),null);assert.equal(commands.observations.size,0);
});
test('late response for A records A without replacing selected B',async t=>{
 let resolve;const {commands}=recovery(t,()=>new Promise(r=>resolve=r));commands.buffer.remember(original);const pending=commands.execute(false);
 const second={...original,body:{...original.body,commandId:'12345678-1234-1234-1234-123456789abd'}};commands.buffer.remember(second);commands.select(second.body.commandId);
 resolve({replayed:false,execution:{instance:{id:'1'}}});await pending;assert.equal(commands.result(),null);assert.equal(commands.observations.get(original.body.commandId).outcome,'NEW_COMMITTED_RESULT');commands.select(original.body.commandId);assert.equal(commands.execution().instance.id,'1');
});
test('lookup not found remains replay-only; rejection versus uncertainty is explicit',async t=>{
 let mode=0;const {commands}=recovery(t,async(_e,replay)=>{assert.equal(replay,true);if(!mode)return {found:false,result:null};throw Object.assign(new Error('failed'),{httpStatus:mode});});commands.buffer.remember(original);await commands.execute(true);assert.equal(commands.result().result.found,false);assert.equal(commands.execution(),null);
 for(const [status,outcome] of [[422,'REJECTED'],[500,'UNKNOWN_OUTCOME']]){mode=status;await assert.rejects(commands.execute(true));assert.equal(commands.result().outcome,outcome);assert.equal(commands.buffer.command.body.commandId,original.body.commandId);}
});
