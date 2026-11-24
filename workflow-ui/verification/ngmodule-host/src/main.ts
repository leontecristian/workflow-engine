import 'zone.js';
import { Component, ENVIRONMENT_INITIALIZER, Injectable, NgModule, inject } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { BrowserModule, platformBrowser } from '@angular/platform-browser';
import { provideRouter, Router, RouterLink, RouterOutlet } from '@angular/router';
import { HttpBackend, HttpClient, HttpErrorResponse, HttpEvent, HttpRequest, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { Observable, firstValueFrom, fromEvent, takeUntil } from 'rxjs';
import { WorkflowInstanceViewerComponent, WorkflowHistoryComponent, WorkflowHistoryEventComponent, WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION, DEFAULT_VIEWER_MESSAGES, normalizeViewerReadError } from '@workflow-engine/ui';
import type {WorkflowViewerReads, WorkflowViewerNavigation, ViewerDestination, InstanceView, HistoryPage, HistoryEvent, InstanceGraph} from '@workflow-engine/ui';
import { WORKFLOW_RUNTIME_READS, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_CANCELLATION, WORKFLOW_TASK_REASSIGNMENT, CommandRecovery, ConsoleDetails } from '@workflow-engine/ui';
import type { WorkflowCancellation, CancellationResult, WorkflowReassignmentTargetPage } from '@workflow-engine/ui';
import type { WorkflowRuntimeReads, WorkflowRuntimeCommands, TaskView, TaskContract, TaskSubmission, Envelope, ActivityView, TaskCommandResult, TaskCommandReplay, PreviousDecisionPage } from '@workflow-engine/ui';
import { WorkflowTaskAdministrationComponent, WorkflowTaskComponent, WorkflowCommandsComponent, WorkflowInstancesComponent } from '@workflow-engine/ui/runtime';
import { WorkflowDefinitionsComponent } from '@workflow-engine/ui/administration';
import { provideWorkflowAdministrationHttp } from '@workflow-engine/ui/http';
const administrationMount = '/host-context/workflow-administration';
const administrationHttpEvidence = new URLSearchParams(location.search).has('administrationHttpEvidence');
const styleOwnershipEvidence = new URLSearchParams(location.search).get('styleOwnershipEvidence');
const id='9007199254740993';
const instance:InstanceView={id,definitionVersionId:'7',definitionKey:'alternate-consumer',definitionVersion:1,definitionHash:'hash',status:'ACTIVE',currentNodeKey:'REVIEW',resultKey:null,businessType:null,businessId:null,startedAt:'2026-11-07T00:00:00Z',startedBy:'fixture',endedAt:null,endedBy:null,terminationReason:null,lastHistorySequence:'4',lockVersion:'9007199254740993'};
const graph={instance,nodes:[{key:'BEGIN',name:'Start',type:'START',state:'COMPLETED',activityId:null,taskId:null},{key:'REVIEW',name:'Review <script>as text</script>',type:'HUMAN_TASK',state:'ACTIVE',activityId:'20',taskId:'30'},{key:'DONE',name:'End',type:'END',state:'PENDING',activityId:null,taskId:null}],edges:[{key:'enter',from:'BEGIN',to:'REVIEW',outcome:null,traversed:true},{key:'end',from:'REVIEW',to:'DONE',outcome:'APPROVE',traversed:false}]};
const summary={instanceId:id,historySequence:'4',eventType:'UNKNOWN_FUTURE',commandId:'00000000-0000-0000-0000-000000000001',correlationId:'fixture',actorKind:'USER',actorRef:'fixture',occurredAt:instance.startedAt,activityId:null,taskId:null,sourceNodeKey:null,targetNodeKey:null,transitionKey:null,detailsSchemaVersion:1};
const namedOutputEvidence = new URLSearchParams(location.search).has('namedOutputEvidence');
const taskInputEvidence = new URLSearchParams(location.search).get('taskInputEvidence');
const businessContextEvidence = new URLSearchParams(location.search).get('businessContextEvidence');
const previousDecisionEvidence = new URLSearchParams(location.search).get('previousDecisionEvidence');
const taskLayoutEvidence = new URLSearchParams(location.search).has('taskLayoutEvidence');
const reassignmentEvidence = new URLSearchParams(location.search).has('reassignmentEvidence') || taskLayoutEvidence;
const cancellationEvidence = new URLSearchParams(location.search).has('cancellationEvidence');
function cancellationFixture():WorkflowCancellation|null {
 if(!cancellationEvidence)return null;
 let committed:CancellationResult|null=null;
 return {
  availability:async()=>({allowed:instance.status==='ACTIVE',lockVersion:instance.lockVersion}),
  cancel:async envelope=>{
   if(committed)return {...committed,replayed:true};
   const at='2026-11-14T12:00:00Z';
   Object.assign(instance,{status:'CANCELLED',endedAt:at,endedBy:'fixture-admin',terminationReason:envelope.body['reason'],lockVersion:'9007199254740994',lastHistorySequence:'7'});
   graph.nodes[1].state='CANCELLED';
   Object.assign(fixtureTask,{status:'CANCELLED',closedAt:at,closureLabel:'The workflow was cancelled.'});
   committed={replayed:false,execution:{commandId:envelope.body['commandId']!,operation:'CANCEL_WORKFLOW',instance:{...instance},activity:{...fixtureActivity,status:'CANCELLED',outcomeKey:null,closureReason:'INSTANCE_CANCELLED',closedBy:'fixture-admin',closedAt:at},cancelledTaskCount:1,firstHistorySequence:'5',lastHistorySequence:'7'}};
   return committed;
  },
  replay:async()=>({found:!!committed,result:committed&&{...committed,replayed:true}}),
 };
}
const evidenceEvents: HistoryEvent[] = [
  {
    summary: {...summary, historySequence:'3', eventType:'VARIABLES_WRITTEN', activityId:'20', taskId:'30'},
    detailsJson:'{"nodeKey":"reviewAmount","writes":[{"variableKey":"reviewResult","type":"JSON","source":{"source":"taskResult","key":"reviewAmount"},"previousInitialized":false,"previousValue":null,"value":{"approvedAmount":12.00}}]}',
  },
  {
    summary: {...summary, eventType:'GATEWAY_ROUTED'},
    detailsJson:'{"nodeKey":"route","selectedTransitionKey":"default","usedDefault":true,"evaluations":[{"transitionKey":"useReview","result":false,"reads":[{"variableKey":"reviewResult","type":"JSON","path":[],"state":"EXISTS","value":false}]}]}',
  },
];
@Injectable() class FixtureState { mode='success'; intercepted=0;commands=0; }
@Injectable() class FixtureBackend implements HttpBackend {
  private state=inject(FixtureState);
  handle(request:HttpRequest<unknown>):Observable<HttpEvent<unknown>> {
    return new Observable(observer=>{
      if(request.headers.get('X-Consumer')!=='NgModule') {observer.error(new Error('Missing host interceptor'));return;}
      this.state.intercepted++;
      if(request.url.startsWith('/host-command/'))this.state.commands++;
      const mode=this.state.mode;
      const timer=setTimeout(()=>{
        if(mode==='denied' || mode==='failed' || styleOwnershipEvidence==='error'){observer.error(new HttpErrorResponse({status:mode==='denied'?404:500}));return;}
        let body:unknown=instance;
        if(request.method==='GET' && new URL(request.urlWithParams,location.origin).pathname===administrationMount+'/definitions'){
          const afterId=new URL(request.urlWithParams,location.origin).searchParams.get('afterId');
          const row={id,key:'alternate-definition',name:'Host administration definition',description:null,createdAt:instance.startedAt,createdBy:'fixture',lockVersion:'9007199254740993'};
          body={items:styleOwnershipEvidence==='empty'?[]:[afterId?{...row,id:'9007199254740994',key:'next-definition'}:row],nextAfterId:styleOwnershipEvidence==='populated'&&!afterId?id:null};
        }
        else if(request.url==='/host-task/30')body=fixtureTask;
        else if(request.url==='/host-task/30/contract')body=fixtureContract();
        else if(request.url==='/host-task/30/submission')body=fixtureSubmission;
        else if(request.url.startsWith('/host-task/30/previous-decisions'))body={
          items:[{nodeName:'Manager <img src=x onerror=alert(1)>',outcomeLabel:'Approved',
            actorLabel:'Fixture manager',occurredAt:instance.startedAt,
            comment:request.url.includes('cursor=page-two')?null:'  <script>alert("unsafe")</script>\n  Exact comment whitespace  '}],
          nextCursor:request.url.includes('cursor=page-two')?null:'page-two',
        };
        else if(request.url==='/host-task/instance-tasks')body={items:[fixtureTask],nextCursor:null};
        else if(request.url.startsWith('/host-command/'))body=request.url.endsWith('/replay')?{found:false,result:null}:completeFixture(request.body as Envelope);
        else if(request.url.endsWith('/graph'))body=graph;
        else if(request.url.includes('/history/'))body=namedOutputEvidence
          ? evidenceEvents.find(event=>request.url.endsWith('/'+event.summary.historySequence))
          : {summary,detailsJson:'{"n":9007199254740993,"d":12500.00,"nil":null}'};
        else if(request.url.endsWith('/history'))body={items:namedOutputEvidence ? evidenceEvents.map(event=>event.summary) : [summary],nextAfterSequence:null};
        observer.next(new HttpResponse({status:200,body:request.responseType==='text'?JSON.stringify(body):body}));observer.complete();
      },mode==='delayed'?1500:50);
      return ()=>clearTimeout(timer);
    });
  }
}
@Injectable() class Reads implements WorkflowViewerReads {
  private http=inject(HttpClient);
  private async read<T>(path:string,signal:AbortSignal):Promise<T>{
    try {if(signal.aborted)throw new DOMException('Aborted','AbortError');return await firstValueFrom(this.http.get<T>(path).pipe(takeUntil(fromEvent(signal,'abort'))));}
    catch(error){if(signal.aborted)throw normalizeViewerReadError({name:'AbortError'});const failure=error as HttpErrorResponse;throw normalizeViewerReadError({httpStatus:failure.status});}
  }
  instance(id:string,s:AbortSignal){return this.read<InstanceView>('/host-read/'+id,s);}
  graph(id:string,s:AbortSignal){return this.read<InstanceGraph>('/host-read/'+id+'/graph',s);}
  history(id:string,afterSequence:string,limit:number,s:AbortSignal){return this.read<HistoryPage>('/host-read/'+id+'/history',s);}
  historyEvent(id:string,sequence:string,s:AbortSignal){return this.read<HistoryEvent>('/host-read/'+id+'/history/'+sequence,s);}
}
@Injectable() class Navigation implements WorkflowViewerNavigation {
  private router=inject(Router);
  destination(intent:ViewerDestination){return this.router.createUrlTree(['/alternate',intent.kind,'id' in intent?intent.id:'instanceId' in intent?intent.instanceId:'recovery']);}
}
@Component({selector:'fixture-destination',standalone:false,template:'<p>Alternate host destination</p>'}) class Destination {}
function reassignmentFixture(){
 if(!reassignmentEvidence)return null;
 let committed:TaskCommandResult|null=null;
 return {
  availability:async()=>({allowed:fixtureTask.status==='OPEN',precondition:fixtureContract().precondition}),
  targets:async(_id:string,kind:string,query:string):Promise<WorkflowReassignmentTargetPage>=>{if(taskLayoutEvidence && new URLSearchParams(location.search).has('delayedDestinationSearch'))await new Promise(resolve=>setTimeout(resolve,1800));return {items:[{ref:kind==='USER'?'new-reviewer':kind==='GROUP'?'Finance':'Approver',label:kind==='USER'?'New reviewer':kind==='GROUP'?'Finance reviewers':'Approver role'}].filter(row=>(row.ref+' '+row.label).toLowerCase().includes(query.toLowerCase())),nextCursor:null};},
  reassign:async(envelope:Envelope,replay:boolean)=>{
   if(replay)return {found:!!committed,result:committed&&{...committed,replayed:true}};
   const assignment={kind:envelope.body['assignmentKind'] as 'USER'|'GROUP'|'ROLE',ref:envelope.body['assignmentRef']!};
   fixtureTarget=assignment;
   fixtureTask.ownershipLabel=assignment.kind==='USER'?'Assigned to '+assignment.ref:undefined;
   fixtureTask.assignmentLabel=assignment.kind==='USER'?undefined:assignment.ref;
   fixtureVersion++;
   committed={replayed:false,status:instance.status,taskStatus:fixtureTask.status};
   return committed;
  }
 };
}
const completedInputFixture = businessContextEvidence === 'completed' || taskInputEvidence === 'completed' || previousDecisionEvidence === 'completed';
const fixtureTask:TaskView={id:'30',nodeName:'Host assigned review',taskTypeName:'Review task',ownershipLabel:'Assigned to you',
 status:completedInputFixture?'COMPLETED':'OPEN',closedAt:completedInputFixture?instance.startedAt:null};
let fixtureVersion=1;
let fixtureTarget={kind:'USER' as 'USER'|'GROUP'|'ROLE',ref:taskLayoutEvidence?'reviewer.'+'longreference'.repeat(14):'fixture'};
let fixtureSubmission: TaskSubmission | null = businessContextEvidence === 'completed' ? {
  outcomeLabel: 'Host approve',
  actorLabel: 'Fixture reviewer',
  occurredAt: instance.startedAt,
  comment: 'Reviewed the frozen inputs.',
  resultJson: '{"approvedAmount":12.00,"evidence":{"verified":true}}',
} : null;
const fixtureActivity:ActivityView={id:'20',instanceId:id,nodeKey:'REVIEW',activationSequence:'1',taskTypeId:'1',completionPolicy:'SINGLE',contributionOutcomeKey:null,requiredTaskCount:1,reviewRevisionRef:null,status:'ACTIVE',outcomeKey:null,enteredAt:instance.startedAt,closedAt:null,closedBy:null,closureReason:null};
function fixtureContract():TaskContract{
 const {id:taskId,...task}=fixtureTask;
 const open=task.status==='OPEN';
 return {task,precondition:open?'fixture-version-'+fixtureVersion:undefined,
  taskType:open?'REVIEW':undefined,claimRequired:false,canClaim:false,canRelease:false,canAdminRelease:false,canComplete:open,
  outcomes:[{...(open?{key:'APPROVE'}:{}),label:'Host approve',requiresComment:true}],
  previousDecisionsAvailable:!!previousDecisionEvidence && previousDecisionEvidence!=='none',
  ...(taskInputEvidence&&{inputs:{
   booking:{type:'JSON',valueJson:'{"amount":9007199254740993.00}'},
   financeReview:{type:'JSON',valueJson:'{"approved":true}'},
   dueOn:{type:'DATE',valueJson:'"2026-11-10"'},
   note:{type:'STRING',valueJson:'null'},
  },resultContract:{mode:'REQUIRED',form:{label:'Result',kind:'object',fields:[]}}}),
 };
}
function completeFixture(envelope:Envelope):TaskCommandResult{
 fixtureTask.status='COMPLETED';
 fixtureTask.closedAt=instance.startedAt;
 fixtureSubmission={outcomeLabel:'Host approve',actorLabel:'You',occurredAt:instance.startedAt,comment:envelope.body['comment']??null};
 return {replayed:false,status:'COMPLETED',taskStatus:'COMPLETED'};
}
function administrationRow(taskId:string,instanceId:string,name:string,status:string,assignment=fixtureTarget){
 return {...fixtureTask,id:taskId,nodeName:name,status,taskType:'REVIEW',instanceId,createdAt:instance.startedAt,
  assignmentLabel:assignment.kind==='USER'?'Reviewer':assignment.ref,assignment,
  assigneeRef:assignment.kind==='USER'?assignment.ref:null};
}
@Injectable() class RuntimeReads implements WorkflowRuntimeReads {
 private state=inject(FixtureState);
 administrationTasks(filters:{status:string;taskType?:string;taskName?:string;assignmentKind?:string;assignmentRef?:string;assigneeRef?:string;instanceId?:string},cursor?:string,limit=50,signal?:AbortSignal){
  if(!taskLayoutEvidence)return Promise.resolve({items:filters.status==='OPEN'||filters.status==='ALL'?[administrationRow('30',id,'Finance review','OPEN'),administrationRow('31','9007199254740994','Another instance review','OPEN',{kind:'GROUP',ref:'Finance'})]:[administrationRow('32',id,'Closed review',filters.status)],nextCursor:null});
  const kinds=['USER','GROUP','ROLE'] as const;
  const rows=Array.from({length:55},(_,index)=>{
   const kind=kinds[index%3];const ref=index===0?'reviewer.'+'longreference'.repeat(14):kind==='USER'?'fixture':kind==='GROUP'?'Finance':'Approver';
   return administrationRow(String(BigInt(id)+BigInt(index+1)),String(BigInt(id)+BigInt(Math.floor(index/20))),
    index===0?'Review the retained international request with a deliberately long descriptive task name that must wrap without changing table spacing':'Independent review '+(index+1),
    filters.status==='ALL'?'OPEN':filters.status,{kind,ref});
  }).filter(row=>(!filters.taskType||filters.taskType===row.taskType)&&(!filters.assignmentKind||filters.assignmentKind===row.assignment.kind)
    &&(!filters.assignmentRef||filters.assignmentRef===row.assignment.ref)&&(!filters.assigneeRef||filters.assigneeRef===row.assigneeRef)&&(!filters.instanceId||filters.instanceId===row.instanceId));
  const candidates=rows.filter(row=>!cursor||BigInt(row.id)>BigInt(cursor));const selected=candidates.slice(0,limit);
  const nextCursor=candidates.length>limit?selected[selected.length-1].id:null;
  const items=this.state.mode==='layout-empty'&&!cursor?[]:selected.filter(row=>!filters.taskName||row.nodeName.toLowerCase().includes(filters.taskName.toLowerCase()));
  return new Promise<{items:typeof rows;nextCursor:string|null}>((resolve,reject)=>{
   const abort=()=>{clearTimeout(timer);reject(new DOMException('Aborted','AbortError'));};
   const timer=setTimeout(()=>{signal?.removeEventListener('abort',abort);resolve({items,nextCursor});},this.state.mode==='delayed'?1500:50);
   if(signal?.aborted)abort();else signal?.addEventListener('abort',abort,{once:true});
  });
 }
 private http=inject(HttpClient);
 private read<T>(path:string,signal:AbortSignal){return firstValueFrom(this.http.get<T>(path).pipe(takeUntil(fromEvent(signal,'abort'))));}
 task(_id:string,signal:AbortSignal){return this.read<TaskView>('/host-task/30',signal);}
 async taskContract(_id:string,signal:AbortSignal):Promise<TaskContract>{
  const contract=await this.read<TaskContract>('/host-task/30/contract',signal);
  if (!businessContextEvidence || taskInputEvidence) return contract;
  const { businessContextInputs } = await import('./business-context-fixture');
  return { ...contract, inputs: businessContextInputs(businessContextEvidence) };
 }
 previousDecisions(_id:string,cursor:string|undefined,_limit:number,signal:AbortSignal){return this.read<PreviousDecisionPage>('/host-task/30/previous-decisions'+(cursor?'?cursor='+cursor:''),signal);}
 taskSubmission(_id:string,signal:AbortSignal){return this.read<TaskSubmission|null>('/host-task/30/submission',signal);}
 instanceTasks(_id:string,_cursor:string|undefined,_limit:number,signal:AbortSignal){return this.read<{items:TaskView[];nextCursor:null}>('/host-task/instance-tasks',signal);}
 listInstances(){
  if(styleOwnershipEvidence!=='runtime')throw new Error('Outside fixture');
  return Promise.resolve({items:[instance],nextAfterId:null});
 }
 variables():never{throw new Error('Outside fixture');}assignedTasks():never{throw new Error('Outside fixture');}activity():never{throw new Error('Outside fixture');}submissions():never{throw new Error('Outside fixture');}
}
@Injectable() class RuntimeCommands implements WorkflowRuntimeCommands {
 private http=inject(HttpClient);
 complete(envelope:Envelope,signal:AbortSignal){return firstValueFrom(this.http.post<TaskCommandResult>('/host-command/complete',envelope).pipe(takeUntil(fromEvent(signal,'abort'))));}
 replayComplete(envelope:Envelope,signal:AbortSignal){return firstValueFrom(this.http.post<TaskCommandReplay>('/host-command/replay',envelope).pipe(takeUntil(fromEvent(signal,'abort'))));}
}
@Component({selector:'fixture-read-only-task',standalone:false,providers:[{provide:WORKFLOW_TASK_REASSIGNMENT,useValue:null},{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:null},CommandRecovery],template:'<h2>Read-only consumer without commands</h2><workflow-task id="30" [sessionScope]="scope"/>'})
class ReadOnlyTask {scope={};}

@Component({selector:'fixture-root',standalone:false,template:`
  @if(styleOwnershipEvidence){
    <mat-card appearance="outlined" id="host-card">
      <mat-card-header><h2 id="host-heading">Host controls</h2></mat-card-header>
      <mat-card-content><button id="host-button" mat-flat-button (click)="dialog.open(hostDialog)">Open host dialog</button></mat-card-content>
    </mat-card>
    <ng-template #hostDialog>
      <h2 mat-dialog-title>Host dialog</h2>
      <mat-dialog-content>Host content</mat-dialog-content>
      <mat-dialog-actions><button mat-button mat-dialog-close>Close host dialog</button></mat-dialog-actions>
    </ng-template>
    @if(styleOwnershipEvidence==='runtime'){
      <workflow-instances [sessionScope]="scope"/>
      <workflow-commands [sessionScope]="scope"/>
      <console-details [value]="styleOwnershipRecord"/>
    }
    @else if(styleOwnershipEvidence!=='host'){<workflow-definitions [sessionScope]="scope" [messages]="administrationMessages"/>}
  }@else if(administrationHttpEvidence){
    <h1>NgModule administration HTTP consumer</h1>
    <p>Host interceptor reads: {{state.intercepted}}</p>
    <workflow-definitions [sessionScope]="scope" [messages]="administrationMessages"/>
  }@else if(businessContextEvidence){<workflow-task id="30" [sessionScope]="scope"/>}
  @else if(taskLayoutEvidence){<div class="layout-evidence">
   <h1>F026 revision 2 layout verification</h1>
   <nav class="fixture-controls" aria-label="Fixture views"><button (click)="layoutView='tasks'">Task administration view</button><button (click)="layoutView='definitions'">Definitions reference view</button><button (click)="layoutView='task'">Reassignment dialog view</button></nav>
   <nav class="fixture-controls" aria-label="Fixture response modes"><button (click)="mode('success')">Normal responses</button><button (click)="mode('delayed')">Delayed responses</button><button (click)="mode('layout-empty')">Empty page with continuation</button></nav>
   @if(layoutView==='tasks'){<workflow-task-administration [sessionScope]="scope"/>}
   @if(layoutView==='definitions'){<workflow-definitions [sessionScope]="scope" [messages]="administrationMessages"/>}
   @if(layoutView==='task'){<workflow-task id="30" [sessionScope]="scope"/>}
  </div>}@else if(reassignmentEvidence){<h1>Task administration fixture</h1><workflow-task-administration [sessionScope]="scope"/><workflow-task id="30" [sessionScope]="scope"/><fixture-read-only-task/>}@else{
  <h1>NgModule viewer consumer</h1><p>Host interceptor reads: {{state.intercepted}}</p>
  <button (click)="locale()">Change messages</button><button (click)="scope={}">Replace same-user session</button><button (click)="scope=null">Invalidate session</button>
  <button (click)="mode('success')">Successful reads</button><button (click)="mode('delayed')">Delayed reads</button><button (click)="mode('denied')">Denied reads</button><button (click)="mode('failed')">Failed reads</button>
  <div class="viewers"><workflow-instance-viewer [instanceId]="id" [sessionScope]="scope" [messages]="messages"/><workflow-instance-viewer [instanceId]="id" [sessionScope]="scope" [messages]="messages"/></div>
  <workflow-history [instanceId]="id" [sessionScope]="scope" [messages]="messages"/><workflow-history-event [instanceId]="id" sequence="4" [sessionScope]="scope" [messages]="messages"/>
  @if(namedOutputEvidence){<workflow-history-event [instanceId]="id" sequence="3" [sessionScope]="scope" [messages]="messages"/>}
  <workflow-definitions [sessionScope]="scope" [messages]="administrationMessages"/>
  <p>Custom host command calls: {{state.commands}}</p><workflow-task id="30" [sessionScope]="scope" [messages]="runtimeMessages"/><workflow-commands [sessionScope]="scope" [messages]="runtimeMessages"/><fixture-read-only-task/>}<router-outlet/>
`}) class App {
  state=inject(FixtureState); id=id; scope:object|null={};messages=DEFAULT_VIEWER_MESSAGES;
  administrationHttpEvidence=administrationHttpEvidence;
  businessContextEvidence=businessContextEvidence;
  styleOwnershipEvidence=styleOwnershipEvidence;styleOwnershipRecord=instance;dialog=inject(MatDialog);
  namedOutputEvidence=namedOutputEvidence;reassignmentEvidence=reassignmentEvidence;taskLayoutEvidence=taskLayoutEvidence;layoutView='tasks';
  administrationMessages={definitionsTitle:'Host workflow library'};runtimeMessages={taskTitle:'Host assigned review',commandsTitle:'Host command evidence'};
  constructor(){
    if (administrationHttpEvidence && (
      inject(WORKFLOW_VIEWER_READS) !== inject(Reads)
      || inject(WORKFLOW_RUNTIME_COMMANDS, {optional:true}) !== null
      || inject(WORKFLOW_CANCELLATION, {optional:true}) !== null
      || inject(WORKFLOW_TASK_REASSIGNMENT, {optional:true}) !== null
    )) throw new Error('Administration fixture requires host viewer and no command providers');
  }
  locale(){this.messages={...DEFAULT_VIEWER_MESSAGES,refresh:'Actualiser',current:'Étape actuelle',graph:'Graphe du workflow'};}
  mode(value:string){this.state.mode=value;this.scope={};}
}
@NgModule({declarations:[App,Destination,ReadOnlyTask],imports:[BrowserModule,RouterLink,RouterOutlet,MatButtonModule,MatCardModule,MatDialogModule,ConsoleDetails,WorkflowInstanceViewerComponent,WorkflowHistoryComponent,WorkflowHistoryEventComponent,WorkflowDefinitionsComponent,WorkflowTaskAdministrationComponent,WorkflowTaskComponent,WorkflowCommandsComponent,WorkflowInstancesComponent],
  providers:[FixtureState,Reads,Navigation,
    ...(administrationHttpEvidence ? [] : [
      RuntimeReads,RuntimeCommands,
      {provide:WORKFLOW_CANCELLATION,useFactory:cancellationFixture},
      {provide:WORKFLOW_TASK_REASSIGNMENT,useFactory:reassignmentFixture},
      {provide:WORKFLOW_RUNTIME_READS,useExisting:RuntimeReads},{provide:WORKFLOW_RUNTIME_COMMANDS,useExisting:RuntimeCommands},
    ]),
    {provide:ENVIRONMENT_INITIALIZER,multi:true,useValue:()=>inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined','mat-ligature-font')},
    provideRouter([{path:'alternate/:kind/:id',component:Destination}]),
    provideHttpClient(withInterceptors([(request,next)=>next(request.clone({setHeaders:{'X-Consumer':'NgModule'}}))])),
    provideWorkflowAdministrationHttp(administrationMount+'/'),
    {provide:HttpBackend,useClass:FixtureBackend},{provide:WORKFLOW_VIEWER_READS,useExisting:Reads},{provide:WORKFLOW_VIEWER_NAVIGATION,useExisting:Navigation}],bootstrap:[App]})
class AppModule {}
platformBrowser().bootstrapModule(AppModule).catch(console.error);
