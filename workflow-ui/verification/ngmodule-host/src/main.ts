import 'zone.js';
import { Component, Injectable, NgModule, inject } from '@angular/core';
import { BrowserModule, platformBrowser } from '@angular/platform-browser';
import { provideRouter, Router, RouterLink, RouterOutlet } from '@angular/router';
import { HttpBackend, HttpClient, HttpErrorResponse, HttpEvent, HttpRequest, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { Observable, firstValueFrom, fromEvent, takeUntil } from 'rxjs';
import { WorkflowInstanceViewerComponent, WorkflowHistoryComponent, WorkflowHistoryEventComponent, WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION, DEFAULT_VIEWER_MESSAGES, normalizeViewerReadError } from '@workflow-engine/ui';
import type {WorkflowViewerReads, WorkflowViewerNavigation, ViewerDestination, InstanceView, HistoryPage, HistoryEvent, InstanceGraph} from '@workflow-engine/ui';
import { WORKFLOW_ADMINISTRATION_API } from '@workflow-engine/ui';
import { WORKFLOW_RUNTIME_READS, WORKFLOW_RUNTIME_COMMANDS, CommandRecovery } from '@workflow-engine/ui';
import type { WorkflowRuntimeReads, WorkflowRuntimeCommands, TaskView, TaskContract, Envelope, CommandResult, ReplayResult } from '@workflow-engine/ui';
import { WorkflowTaskComponent, WorkflowCommandsComponent } from '@workflow-engine/ui/runtime';
import { WorkflowDefinitionsComponent } from '@workflow-engine/ui/administration';
import type { WorkflowAdministrationApi, AdministrationPage, DefinitionView } from '@workflow-engine/ui';
const id='9007199254740993';
const instance:InstanceView={id,definitionVersionId:'7',definitionKey:'alternate-consumer',definitionVersion:1,definitionHash:'hash',status:'ACTIVE',currentNodeKey:'REVIEW',resultKey:null,businessType:null,businessId:null,startedAt:'2026-11-07T00:00:00Z',startedBy:'fixture',endedAt:null,endedBy:null,terminationReason:null,lastHistorySequence:'4',lockVersion:'9007199254740993'};
const graph={instance,nodes:[{key:'BEGIN',name:'Start',type:'START',state:'COMPLETED',activityId:null,taskId:null},{key:'REVIEW',name:'Review <script>as text</script>',type:'HUMAN_TASK',state:'ACTIVE',activityId:'20',taskId:'30'},{key:'DONE',name:'End',type:'END',state:'PENDING',activityId:null,taskId:null}],edges:[{key:'enter',from:'BEGIN',to:'REVIEW',outcome:null,traversed:true},{key:'end',from:'REVIEW',to:'DONE',outcome:'APPROVE',traversed:false}]};
const summary={instanceId:id,historySequence:'4',eventType:'UNKNOWN_FUTURE',commandId:'00000000-0000-0000-0000-000000000001',correlationId:null,actorKind:'USER',actorRef:'fixture',occurredAt:instance.startedAt,activityId:null,taskId:null,sourceNodeKey:null,targetNodeKey:null,transitionKey:null,detailsSchemaVersion:1};
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
        if(mode==='denied' || mode==='failed'){observer.error(new HttpErrorResponse({status:mode==='denied'?404:500}));return;}
        let body:unknown=instance;
        if(request.url==='/host-administration/definitions')body={items:[{id,key:'alternate-definition',name:'Host administration definition',description:null,createdAt:instance.startedAt,createdBy:'fixture',lockVersion:'9007199254740993'}],nextAfterId:null};
        else if(request.url==='/host-task/30')body=fixtureTask;
        else if(request.url==='/host-task/30/contract')body=fixtureContract;
        else if(request.url==='/host-task/30/submission')body=null;
        else if(request.url==='/host-task/instance-tasks')body={items:[fixtureTask],nextAfterId:null};
        else if(request.url.startsWith('/host-command/'))body=request.url.endsWith('/replay')?{found:false,result:null}:fixtureResult(request.body as Envelope);
        else if(request.url.endsWith('/graph'))body=graph;
        else if(request.url.includes('/history/'))body={summary,detailsJson:'{"n":9007199254740993,"d":12500.00,"nil":null}'};
        else if(request.url.endsWith('/history'))body={items:[summary],nextAfterSequence:null};
        observer.next(new HttpResponse({status:200,body}));observer.complete();
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
/** This existing consumer mounts the definition list; other administration operations fail explicitly. */
function administrationFixture(): WorkflowAdministrationApi {
  const http=inject(HttpClient);
  const unused=():never=>{throw new Error('Operation outside the consumer fixture');};
  return {
    listDefinitions:(_cursor,_limit,signal)=>firstValueFrom(http.get<AdministrationPage<DefinitionView>>('/host-administration/definitions').pipe(takeUntil(fromEvent(signal,'abort')))),
    listTaskTypes:unused,createTaskType:unused,updateTaskType:unused,definition:unused,
    saveMetadata:unused,listVersions:unused,version:unused,lookupVersion:unused,
    importDraft:unused,saveDocument:unused,checkVersion:unused,retireVersion:unused,exportVersion:unused,
  };
}

const fixtureTask:TaskView={id:'30',instanceId:id,activityId:'20',taskTypeId:'1',taskKey:'REVIEW',assignment:{kind:'USER',ref:'fixture'},status:'OPEN',outcomeKey:null,createdAt:instance.startedAt,dueAt:null,closedAt:null,closedBy:null,closureReason:null};
const fixtureContract:TaskContract={taskId:'30',instanceId:id,instanceLockVersion:instance.lockVersion,taskType:'REVIEW',canComplete:true,assignmentLabel:'Fixture reviewer',outcomes:[{key:'APPROVE',label:'Host approve',requiresComment:true}]};
function fixtureResult(envelope:Envelope):CommandResult{return {replayed:false,execution:{commandId:envelope.body['commandId']!,operation:'COMPLETE',instance,activity:{id:'20',instanceId:id,nodeKey:'REVIEW',activationSequence:'1',taskTypeId:'1',completionPolicy:'SINGLE',contributionOutcomeKey:null,requiredTaskCount:1,reviewRevisionRef:null,status:'COMPLETED',outcomeKey:'APPROVE',enteredAt:instance.startedAt,closedAt:instance.startedAt,closedBy:'fixture',closureReason:null},task:{...fixtureTask,status:'COMPLETED'},firstHistorySequence:'5',lastHistorySequence:'6'}};}
@Injectable() class RuntimeReads implements WorkflowRuntimeReads {
 private http=inject(HttpClient);
 private read<T>(path:string,signal:AbortSignal){return firstValueFrom(this.http.get<T>(path).pipe(takeUntil(fromEvent(signal,'abort'))));}
 task(_id:string,signal:AbortSignal){return this.read<TaskView>('/host-task/30',signal);}
 taskContract(_id:string,signal:AbortSignal){return this.read<TaskContract>('/host-task/30/contract',signal);}
 taskSubmission(_id:string,signal:AbortSignal){return this.read<null>('/host-task/30/submission',signal);}
 instanceTasks(_id:string,_cursor:string|undefined,_limit:number,signal:AbortSignal){return this.read<{items:TaskView[];nextAfterId:null}>('/host-task/instance-tasks',signal);}
 listInstances():never{throw new Error('Outside fixture');}variables():never{throw new Error('Outside fixture');}assignedTasks():never{throw new Error('Outside fixture');}activity():never{throw new Error('Outside fixture');}submissions():never{throw new Error('Outside fixture');}
}
@Injectable() class RuntimeCommands implements WorkflowRuntimeCommands {
 private http=inject(HttpClient);
 complete(envelope:Envelope,signal:AbortSignal){return firstValueFrom(this.http.post<CommandResult>('/host-command/complete',envelope).pipe(takeUntil(fromEvent(signal,'abort'))));}
 replayComplete(envelope:Envelope,signal:AbortSignal){return firstValueFrom(this.http.post<ReplayResult>('/host-command/replay',envelope).pipe(takeUntil(fromEvent(signal,'abort'))));}
}
@Component({selector:'fixture-read-only-task',standalone:false,providers:[{provide:WORKFLOW_RUNTIME_COMMANDS,useValue:null},CommandRecovery],template:'<h2>Read-only consumer without commands</h2><workflow-task id="30" [sessionScope]="scope"/>'})
class ReadOnlyTask {scope={};}

@Component({selector:'fixture-root',standalone:false,template:`
  <h1>NgModule viewer consumer</h1><p>Host interceptor reads: {{state.intercepted}}</p>
  <button (click)="locale()">Change messages</button><button (click)="scope={}">Replace same-user session</button><button (click)="scope=null">Invalidate session</button>
  <button (click)="mode('success')">Successful reads</button><button (click)="mode('delayed')">Delayed reads</button><button (click)="mode('denied')">Denied reads</button><button (click)="mode('failed')">Failed reads</button>
  <div class="viewers"><workflow-instance-viewer [instanceId]="id" [sessionScope]="scope" [messages]="messages"/><workflow-instance-viewer [instanceId]="id" [sessionScope]="scope" [messages]="messages"/></div>
  <workflow-history [instanceId]="id" [sessionScope]="scope" [messages]="messages"/><workflow-history-event [instanceId]="id" sequence="4" [sessionScope]="scope" [messages]="messages"/>
  <workflow-definitions [sessionScope]="scope" [messages]="administrationMessages"/>
  <p>Custom host command calls: {{state.commands}}</p><workflow-task id="30" [sessionScope]="scope" [messages]="runtimeMessages"/><workflow-commands [sessionScope]="scope" [messages]="runtimeMessages"/><fixture-read-only-task/><router-outlet/>
`}) class App {
  state=inject(FixtureState); id=id; scope:object|null={};messages=DEFAULT_VIEWER_MESSAGES;
  administrationMessages={definitionsTitle:'Host workflow library'};runtimeMessages={taskTitle:'Host assigned review',commandsTitle:'Host command evidence'};
  locale(){this.messages={...DEFAULT_VIEWER_MESSAGES,refresh:'Actualiser',current:'Étape actuelle',graph:'Graphe du workflow'};}
  mode(value:string){this.state.mode=value;this.scope={};}
}
@NgModule({declarations:[App,Destination,ReadOnlyTask],imports:[BrowserModule,RouterLink,RouterOutlet,WorkflowInstanceViewerComponent,WorkflowHistoryComponent,WorkflowHistoryEventComponent,WorkflowDefinitionsComponent,WorkflowTaskComponent,WorkflowCommandsComponent],
  providers:[FixtureState,Reads,RuntimeReads,RuntimeCommands,Navigation,provideRouter([{path:'alternate/:kind/:id',component:Destination}]),
    provideHttpClient(withInterceptors([(request,next)=>next(request.clone({setHeaders:{'X-Consumer':'NgModule'}}))])),
    {provide:WORKFLOW_ADMINISTRATION_API,useFactory:administrationFixture},
    {provide:WORKFLOW_RUNTIME_READS,useExisting:RuntimeReads},{provide:WORKFLOW_RUNTIME_COMMANDS,useExisting:RuntimeCommands},
    {provide:HttpBackend,useClass:FixtureBackend},{provide:WORKFLOW_VIEWER_READS,useExisting:Reads},{provide:WORKFLOW_VIEWER_NAVIGATION,useExisting:Navigation}],bootstrap:[App]})
class AppModule {}
platformBrowser().bootstrapModule(AppModule).catch(console.error);
