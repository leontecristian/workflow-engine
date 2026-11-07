import { Directive, DestroyRef, Input, inject, signal } from '@angular/core';
import type { OnChanges, SimpleChanges } from '@angular/core';
import { Router } from '@angular/router';
import { WORKFLOW_VIEWER_NAVIGATION, type ViewerDestination } from '@workflow-engine/ui';
import { WORKFLOW_ADMINISTRATION_API, DEFAULT_ADMINISTRATION_MESSAGES, type WorkflowAdministrationMessages } from '@workflow-engine/ui';
/** One view generation guards reads, writes, uploads and delayed dialog callbacks. Aborting never implies server rollback. */
@Directive()
export abstract class AdministrationState implements OnChanges {
 @Input({required:true}) sessionScope:unknown|null = null;
 @Input() set messages(value:Partial<WorkflowAdministrationMessages>){this.messageOverrides=value;}
 get messages():WorkflowAdministrationMessages{return {...DEFAULT_ADMINISTRATION_MESSAGES,...this.messageOverrides};}
 private messageOverrides:Partial<WorkflowAdministrationMessages>={};
 readonly api=inject(WORKFLOW_ADMINISTRATION_API);
 readonly busy=signal(false); readonly error=signal(''); readonly notice=signal(''); readonly refreshError=signal('');
 protected readonly destroyRef=inject(DestroyRef);
 private readonly navigation=inject(WORKFLOW_VIEWER_NAVIGATION,{optional:true}); private readonly router=inject(Router,{optional:true});
 private generation=0; private sequence=0; private controller=new AbortController(); private destroyed=false;
 constructor(){this.destroyRef.onDestroy(()=>{this.destroyed=true;this.invalidate();this.clearContent();});}
 ngOnChanges(changes:SimpleChanges){
  if(changes['sessionScope'] || changes['definitionId'] || changes['versionId']){
   this.invalidate();this.clearContent();this.error.set('');this.notice.set('');this.refreshError.set('');
   if(this.sessionScope!==null)void this.load();
  }
 }
 protected invalidate(){this.generation++;this.sequence++;this.controller.abort();this.controller=new AbortController();this.busy.set(false);this.onInvalidate();}
 protected onInvalidate():void{}
 protected abstract clearContent():void;
 abstract load(cursor?:string,refresh?:boolean):Promise<void>;
 protected capture(){const generation=this.generation,scope=this.sessionScope;return ()=>!this.destroyed && generation===this.generation && scope===this.sessionScope && scope!==null;}
 protected navigate(intent:ViewerDestination){const target=this.navigation?.destination(intent);if(target)void this.router?.navigateByUrl(target);}
 protected async run(action:(signal:AbortSignal,current:()=>boolean)=>Promise<void>,refresh=false):Promise<boolean>{
  if(this.destroyed || this.sessionScope===null)return false;
  const capture=this.capture(),sequence=++this.sequence;const current=()=>capture()&&sequence===this.sequence;this.busy.set(true);if(refresh)this.refreshError.set('');else this.error.set('');
  try{await action(this.controller.signal,current);return current();}
  catch(failure){if(!current())return false;const e=failure as {name?:string;httpStatus?:number;message?:string};if(e?.name==='AbortError')return false;
   const message=e?.httpStatus===401?this.messages.unauthenticated:e?.httpStatus===403?this.messages.denied:e?.message||this.messages.failed;
   if(e?.httpStatus===401||e?.httpStatus===403){this.invalidate();this.clearContent();this.notice.set('');this.refreshError.set('');this.error.set(message);}
   else (refresh?this.refreshError:this.error).set(message);return false;
  }finally{if(current()&&sequence===this.sequence)this.busy.set(false);}
 }
}
