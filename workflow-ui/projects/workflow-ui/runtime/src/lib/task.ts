import { Component, Injector, Input, computed, inject, signal } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { CommandRecovery, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_TASK_REASSIGNMENT } from '@workflow-engine/ui';
import type { Envelope, TaskView, TaskContract, TaskSubmission, PreviousDecision } from '@workflow-engine/ui';
import { RuntimeState } from './runtime-state';
import { PAGE_IMPORTS } from './page-imports';
import { download } from './variables';
import { currentWork } from './current-work';
import { DefaultTaskHandlerComponent } from './default-task-handler';
import { WORKFLOW_TASK_EDITORS, type WorkflowTaskDecision, type WorkflowTaskHandlerInputs } from './task-handler';
import { isBlankComment, isBlankResult } from './task-decision-validation';

@Component({selector:"workflow-task",standalone:true,imports:[...PAGE_IMPORTS, NgComponentOutlet],styleUrl:"./runtime.scss",templateUrl:"./task.html"})
export class WorkflowTaskComponent extends RuntimeState {
  readonly reassignment=inject(WORKFLOW_TASK_REASSIGNMENT,{optional:true});
  private readonly injector=inject(Injector);
  readonly reassignmentAvailability=signal<{allowed:boolean;instanceLockVersion:string}|null>(null);
  readonly reassignBusy=signal(false);readonly reassignNotice=signal('');readonly lookupMiss=signal(false);
  private dialogOpen=false;private confirmation?:{close():void};private closed=false;
  constructor(){super();this.destroyRef.onDestroy(()=>{this.closed=true;this.confirmation?.close();});}
  protected override onInvalidate(){this.confirmation?.close();this.dialogOpen=false;}
  pendingReassignment():Envelope|null{return this.commands.buffer.entries.find(e=>e.kind==='reassign'&&e.body['taskId']===this.id&&this.commands.pendingIds.has(e.body['commandId']!))??null;}
  canReassign(){const a=this.reassignmentAvailability();return !!(this.reassignment&&this.detail()?.status==='OPEN'&&a?.allowed&&a.instanceLockVersion===this.contract()?.instanceLockVersion&&!this.uncertain);}
  async confirmReassignment(){
   if(!this.canReassign()||this.busy()||this.reassignBusy()||this.dialogOpen)return;
   const id=this.id,scope=this.sessionScope,version=this.contract()!.instanceLockVersion;this.dialogOpen=true;
   try{const [{MatDialog},{WorkflowReassignmentDialog}]=await Promise.all([import('@angular/material/dialog'),import('./reassignment-dialog')]);
    if(this.closed||scope!==this.sessionScope||id!==this.id||version!==this.contract()?.instanceLockVersion||!this.canReassign()){this.dialogOpen=false;return;}
    const dialog=this.confirmation=this.injector.get(MatDialog).open(WorkflowReassignmentDialog,{data:{task:this.detail(),version},width:'36rem',maxWidth:'95vw'});
    dialog.afterClosed().subscribe((envelope:Envelope|undefined)=>{this.dialogOpen=false;
     if(!envelope||this.closed||scope!==this.sessionScope||id!==this.id||version!==this.contract()?.instanceLockVersion||!this.canReassign())return;
     const frozen=this.commands.buffer.remember(envelope);void this.sendReassignment(frozen,false);
    });
   }catch{this.dialogOpen=false;if(!this.closed&&scope===this.sessionScope&&id===this.id)this.reassignNotice.set('Confirmation could not be opened. Try again.');}
  }
  async sendReassignment(envelope:Envelope,replay:boolean){
   if(this.reassignBusy()||this.closed)return;const id=this.id,scope=this.sessionScope;
   const current=()=>!this.closed&&scope===this.sessionScope&&id===this.id;
   this.reassignBusy.set(true);this.reassignNotice.set('');this.commands.buffer.select(envelope.body['commandId']!);
   try{const result=await this.commands.execute(replay);if(!current())return;
    if('found' in result&&!result.found){this.lookupMiss.set(true);this.reassignNotice.set('No committed reassignment result was found.');}
    else{this.lookupMiss.set(false);await this.load();if(current())this.reassignNotice.set('Task reassigned.');}
   }catch{if(!current())return;this.lookupMiss.set(false);
    if(this.commands.pendingIds.has(envelope.body['commandId']!))this.reassignNotice.set('Reassignment outcome unknown. Check the result before retrying.');
    else{await this.load();if(current())this.reassignNotice.set('Reassignment was rejected. Current details have been refreshed; confirm a new request if available.');}
   }finally{if(current())this.reassignBusy.set(false);}
  }
  @Input() diagnosticsEnabled = false;
  get uncertain(): boolean {
    return !!this.pendingReassignment() || this.commands.buffer.entries.some(e => e.kind !== "start" && e.body["taskId"] === this.id && this.commands.observations.get(e.body["commandId"]!)?.outcome === "UNKNOWN_OUTCOME");
  }
 protected clearContent(){this.completing=false;this.handlerLoad.update(load=>load+1);this.reassignmentAvailability.set(null);this.reassignNotice.set('');this.lookupMiss.set(false);this.cancelPreviousRead();this.previousDecisions.set([]);this.previousCursor.set(null);this.previousError.set('');this.detail.set(null);this.contract.set(null);this.submission.set(null);this.currentTasks.set([]);this.reason="";}
  previousDecisions = signal<PreviousDecision[]>([]);
  previousCursor = signal<string | null>(null);
  previousBusy = signal(false);
  previousError = signal('');
  private previousController?: AbortController;
  private previousSequence = 0;
  private cancelPreviousRead(){this.previousSequence++;this.previousController?.abort();this.previousBusy.set(false);}
  get hasPreviousDecisions(){const mode=this.contract()?.previousDecisions?.mode;return !!mode && mode!=='NONE';}
  noPreviousComment(comment:string|null){return comment===null || /^\s*$/u.test(comment);}
  async loadPreviousDecisions(more=false):Promise<void>{
    if(!this.hasPreviousDecisions || this.previousBusy() || !this.sessionScope || (more && !this.previousCursor()))return;
    this.cancelPreviousRead();
    const controller=this.previousController=new AbortController();
    const sequence=this.previousSequence, scope=this.sessionScope, id=this.id;
    const current=()=>!controller.signal.aborted && scope===this.sessionScope
      && sequence===this.previousSequence && id===this.id;
    this.previousBusy.set(true);this.previousError.set('');
    try{
      const page=await this.api.previousDecisions(id,more?this.previousCursor()!:undefined,20,controller.signal);
      if(!current())return;
      this.previousDecisions.set(more?[...this.previousDecisions(),...page.items]:page.items);
      this.previousCursor.set(page.nextAfterSequence);
    }catch(failure){
      if(!current() || (failure as {name?:string})?.name==='AbortError')return;
      const status=(failure as {httpStatus?:number})?.httpStatus;
      if(status===401 || status===403 || status===404){this.clearContent();this.error.set('Resource missing or unavailable.');}
      else this.previousError.set('Previous decisions could not be loaded. Try again.');
    }finally{if(current())this.previousBusy.set(false);}
  }
  get meta() {return {
    title: this.messages.taskTitle,
    eyebrow: "ASSIGNED WORK",
    description:
      "Inspect the stored assignment, complete your task and read its accepted submission.",
    icon: "assignment_turned_in",
  };}
  detail = signal<TaskView | null>(null);
  contract = signal<TaskContract | null>(null);
  readonly inputs = computed(() => Object.entries(this.contract()?.inputs ?? {}));
  submission = signal<TaskSubmission | null>(null);
  currentTasks = signal<TaskView[]>([]);
  reason = "";
  downloadResult() {
    const text = this.submission()?.resultJson;
    if (text !== undefined) download(text, 'task-result.json');
  }
  commands = inject(CommandRecovery);
  readonly commandProvider=inject(WORKFLOW_RUNTIME_COMMANDS,{optional:true});
  private readonly taskEditors = inject(WORKFLOW_TASK_EDITORS, { optional: true });
  private readonly handlerLoad = signal(0);
  private readonly handlerView = computed(() => {
    const load = this.handlerLoad();
    const contract = this.contract(), detail = this.detail();
    const taskId = this.id, scope = this.sessionScope;
    if (!this.commandProvider || !scope || !contract || detail?.status !== 'OPEN') return null;

    const sameTask = () => !this.closed && this.sessionScope === scope && this.id === taskId;
    const current = () => sameTask() && this.handlerLoad() === load
      && this.contract() === contract && this.detail() === detail;
    return {
      contract,
      component: this.taskEditors?.get(contract.taskType) ?? DefaultTaskHandlerComponent,
      submitDecision: async (decision: WorkflowTaskDecision) => {
        if (current() && this.canSubmitDecision()) {
          await this.complete(decision, taskId, detail.instanceId, contract, current, sameTask);
        }
      },
    };
  });
  readonly handler = computed(() => this.handlerView()?.component ?? null);
  readonly handlerInputs = computed((): WorkflowTaskHandlerInputs | null => {
    const view = this.handlerView();
    if (!view) return null;
    return { contract: view.contract, editable: this.canSubmitDecision(), submitDecision: view.submitDecision };
  });

  private canSubmitDecision(): boolean {
    // Observe settlement even when a different recovery command is selected.
    const runningIds = this.commands.runningIds;
    return !!this.contract()?.canComplete && this.detail()?.status === 'OPEN'
      && !this.busy() && !this.reassignBusy() && !this.uncertain
      && !this.commands.buffer.entries.some(entry => entry.kind === 'complete'
        && entry.body['taskId'] === this.id && runningIds.has(entry.body['commandId']!));
  }
  protected onScope(){this.commands.setScope(this.sessionScope);this.reassignBusy.set(false);}
  private completing=false;
  protected clearFailedRead(){if(!this.completing)this.clearContent();}
  async load() {
    this.clearContent();
    const id = this.id;
    const loaded = await this.run(async (signal,current) => {
      // An older contract token must never authorize actions against a newer, unseen assignee.
      const snapshot = async () => {
        const contract = await this.api.taskContract(id, signal);
        if (!current()) return null;
        return { contract, detail: await this.api.task(id, signal) };
      };
      const [task, s] = await Promise.all([
        snapshot(),
        this.api.taskSubmission(id,signal).catch(error => {
          const status=(error as {httpStatus?:number})?.httpStatus;
          if(status===403 || status===404)return null;
          throw error;
        }),
      ]);
      if (!current() || !task) return;
      this.detail.set(task.detail);
      this.contract.set(task.contract);
      this.submission.set(s);
      if(this.reassignment&&task.detail.status==='OPEN'){
       const availability=await this.reassignment.availability(id,signal);
       if(current())this.reassignmentAvailability.set(availability);
      }
    });
    // The read-only context has its own request state and never gates completion.
    if(loaded)await this.loadPreviousDecisions();
  }
  reasonBlank(): boolean { return isBlankComment(this.reason); }
  async ownership(kind: 'claim'|'release'|'admin-release'): Promise<void> {
    const contract=this.contract(), detail=this.detail();
    const allowed=kind==='claim'?contract?.canClaim:kind==='release'?contract?.canRelease:contract?.canAdminRelease;
    if(this.busy() || this.reassignBusy() || !this.commandProvider?.ownership || !detail || detail.status!=='OPEN' || detail.assignment.kind==='USER' || !allowed || this.uncertain)return;
    this.completing=true;
    let conflict=false;
    const succeeded=await this.run(async(signal) => {
      const body: Record<string,string>={commandId:crypto.randomUUID(),instanceId:detail.instanceId,taskId:this.id};
      if(kind==='admin-release')body['reason']=this.reason;
      this.commands.buffer.remember({kind,body,expectedToken:contract!.instanceLockVersion});
      try { await this.commands.execute(false,signal); }
      catch(error) {
        const status=(error as {httpStatus?:number})?.httpStatus;
        conflict=status===409;
        if((!status || status>=500) && !this.diagnosticsEnabled)throw new Error('The outcome is uncertain. Contact your administrator before trying again.');
        throw error;
      }
    });
    this.completing=false;
    if(succeeded){await this.load();this.notice.set('Task ownership updated.');}
    else if(conflict){await this.load();this.error.set('The task changed. Current details have been refreshed.');}
  }
  private async complete(
    decision: WorkflowTaskDecision,
    taskId: string,
    instanceId: string,
    contract: TaskContract,
    currentView: () => boolean,
    sameTask: () => boolean,
  ): Promise<void> {
    this.completing=true;
    let conflict = false;
    let completionNotice = 'Task completed.';
    const succeeded = await this.run(async (signal,current) => {
      const { outcomeKey, comment, resultJson } = decision ?? {};
      const selected = contract.outcomes.find(outcome => outcome.key === outcomeKey);
      if (!selected) throw new Error("Choose an available outcome on an open task.");
      if (typeof comment !== 'string') throw new Error("Comment must be text.");
      if (comment.length > 2000) throw new Error("Comment exceeds 2000 characters.");
      if (selected.requiresComment && isBlankComment(comment)) throw new Error("A comment is required for " + selected.label + ".");
      if (resultJson !== undefined && typeof resultJson !== 'string') throw new Error("Result must be JSON object text.");
      const resultMode = contract.resultContract?.mode ?? 'FORBIDDEN';
      const hasResult = resultJson !== undefined && !isBlankResult(resultJson);
      if (resultMode === 'REQUIRED' && !hasResult) throw new Error("A JSON result is required.");
      if (resultMode === 'FORBIDDEN' && hasResult) throw new Error("This task does not accept a JSON result.");
      this.commands.buffer.remember({
        kind: "complete",
        body: {
          commandId: crypto.randomUUID(),
          instanceId,
          taskId,
          outcomeKey: selected.key,
          comment: comment || null,
          ...(hasResult ? {resultJson} : {}),
        },
        expectedToken: contract.instanceLockVersion,
      });
      try {
        await this.commands.execute(false,signal);
        const activity = this.commands.execution()?.activity;
        if (activity?.completionPolicy === 'ALL') completionNotice = activity.status === 'ACTIVE'
          ? 'Your contribution was accepted. The review is waiting for other tasks.'
          : 'Your submission resolved the review.';
      }
      catch (error) {
        const failure = error as {httpStatus?:number;code?:string};
        const status = failure?.httpStatus;
        conflict = status === 409;
        const validationFailed = status === 503 && failure?.code === 'RESULT_VALIDATION_FAILED';
        if ((!status || status >= 500) && !validationFailed && !this.diagnosticsEnabled) throw new Error("The outcome is uncertain. Contact your administrator before trying again.");
        throw error;
      }
      if(current() && this.diagnosticsEnabled)this.navigate({kind:"commands"});
    });
    if (!currentView()) return;
    this.completing=false;
    if ((!succeeded || this.diagnosticsEnabled) && !conflict) return;

    const refresh = this.load();
    const refreshLoad = this.handlerLoad();
    const refreshIsCurrent = () => sameTask() && this.handlerLoad() === refreshLoad;
    await refresh;
    if (!refreshIsCurrent()) return;
    if (conflict) {
      this.error.set("The task changed. Current details have been refreshed.");
      return;
    }
    await this.run(async (signal, current) => {
      const refreshedInstanceId = this.detail()?.instanceId;
      if (!refreshedInstanceId) return;
      const tasks = await currentWork(this.api, refreshedInstanceId, signal);
      if (current()) this.currentTasks.set(tasks);
    });
    if (refreshIsCurrent()) this.notice.set(completionNotice);
  }
}
