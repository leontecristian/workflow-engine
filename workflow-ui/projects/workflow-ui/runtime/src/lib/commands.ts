import { Component, inject, signal } from '@angular/core';
import { CommandRecovery, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_VIEWER_READS, type InstanceView, type TaskView } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
import { download } from './variables';
import { currentWork } from './current-work';
@Component({selector:'workflow-commands',standalone:true,imports:[...PAGE_IMPORTS],styleUrl:'./runtime.scss',templateUrl:'./commands.html'})
export class WorkflowCommandsComponent extends RuntimeState {
 readonly commands=inject(CommandRecovery);readonly commandProvider=inject(WORKFLOW_RUNTIME_COMMANDS,{optional:true});
 readonly viewer=inject(WORKFLOW_VIEWER_READS);
 readonly result=this.commands.result;
 get meta(){return {title:this.messages.commandsTitle,eyebrow:'RELIABLE EXECUTION',description:'Keep the original request intact. Retry explicitly or find its immutable result.',icon:'replay'};}
 envelope='';currentInstance=signal<InstanceView|null>(null);currentTasks=signal<TaskView[]>([]);private readGeneration=0;
 protected onScope(){this.commands.setScope(this.sessionScope);}
 protected clearContent(){this.envelope='';this.currentInstance.set(null);this.currentTasks.set([]);this.readGeneration++;}
 protected clearFailedRead(){this.currentInstance.set(null);this.currentTasks.set([]);}
 protected onInvalidate(){this.readGeneration++;}
 resultNotFound(){const result=this.result()?.result;return !!result&&'found' in result&&!result.found;}
 commandExecution(){return this.commands.execution();}
 async load(){await this.refreshCurrent();}
 async retry(replay:boolean){
  if(!this.commandProvider)return;
  const success=await this.run(async(signal,current)=>{
   await this.commands.execute(replay,signal);
   if(!current())return;
   this.notice.set(replay?'Replay-only lookup returned; no new command was executed.':'Command returned after commit. Inspect current state separately.');
  });
  if(success)await this.refreshCurrent();
 }
 async copyEnvelope(){
  const id=this.commands.buffer.command?.body['commandId'];
  await this.run(async(_signal,current)=>{await navigator.clipboard.writeText(this.commands.buffer.export());if(current()&&this.commands.buffer.command?.body['commandId']===id)this.notice.set('Envelope copied; contains command input. Keep it private.');});
 }
 downloadEnvelope(){if(this.sessionScope!==null&&this.commands.buffer.command)download(this.commands.buffer.export(),'workflow-command.json');}
 restoreEnvelope(){
  if(this.sessionScope===null)return;
  this.invalidate();
  try{this.commands.restore(this.envelope);this.error.set('');this.notice.set('Envelope restored. Find result does not execute it.');void this.refreshCurrent();}
  catch(error){this.error.set(String(error));}
 }
 clearCommand(){this.invalidate();this.clearContent();this.commands.clear();this.error.set('');this.notice.set('');}
 selectCommand(id:string){this.invalidate();this.commands.select(id);void this.refreshCurrent();}
 async refreshCurrent(){
  await this.run(async(signal,current)=>{
   const generation=++this.readGeneration,id=this.commands.buffer.command?.body['commandId'];
   this.currentInstance.set(null);this.currentTasks.set([]);
   const execution=this.commandExecution();if(!execution)return;
   const [instance,tasks]=await Promise.all([this.viewer.instance(execution.instance.id,signal),currentWork(this.api,execution.instance.id,signal)]);
   if(!current()||generation!==this.readGeneration||id!==this.commands.buffer.command?.body['commandId'])return;
   this.currentInstance.set(instance);this.currentTasks.set(tasks);
  });
 }
 refresh(){void this.refreshCurrent();}
}
