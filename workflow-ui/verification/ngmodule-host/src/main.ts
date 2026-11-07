import 'zone.js';
import { Component, Injectable, NgModule, inject } from '@angular/core';
import { BrowserModule, platformBrowser } from '@angular/platform-browser';
import { provideRouter, Router, RouterLink, RouterOutlet } from '@angular/router';
import { HttpBackend, HttpClient, HttpErrorResponse, HttpEvent, HttpRequest, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { Observable, firstValueFrom, fromEvent, takeUntil } from 'rxjs';
import { WorkflowInstanceViewerComponent, WorkflowHistoryComponent, WorkflowHistoryEventComponent, WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION, DEFAULT_VIEWER_MESSAGES, normalizeViewerReadError } from '@workflow-engine/ui';
import type {WorkflowViewerReads, WorkflowViewerNavigation, ViewerDestination, InstanceView, HistoryPage, HistoryEvent, InstanceGraph} from '@workflow-engine/ui';
import { WORKFLOW_ADMINISTRATION_API } from '@workflow-engine/ui';
import { WorkflowDefinitionsComponent } from '@workflow-engine/ui/administration';
import type { WorkflowAdministrationApi, AdministrationPage, DefinitionView } from '@workflow-engine/ui';
const id='9007199254740993';
const instance={id,definitionVersionId:'7',definitionKey:'alternate-consumer',definitionVersion:1,definitionHash:'hash',status:'ACTIVE',currentNodeKey:'REVIEW',resultKey:null,businessType:null,businessId:null,startedAt:'2026-11-07T00:00:00Z',startedBy:'fixture',endedAt:null,endedBy:null,terminationReason:null,lastHistorySequence:'4',lockVersion:'9007199254740993'};
const graph={instance,nodes:[{key:'BEGIN',name:'Start',type:'START',state:'COMPLETED',activityId:null,taskId:null},{key:'REVIEW',name:'Review <script>as text</script>',type:'HUMAN_TASK',state:'ACTIVE',activityId:'20',taskId:'30'},{key:'DONE',name:'End',type:'END',state:'PENDING',activityId:null,taskId:null}],edges:[{key:'enter',from:'BEGIN',to:'REVIEW',outcome:null,traversed:true},{key:'end',from:'REVIEW',to:'DONE',outcome:'APPROVE',traversed:false}]};
const summary={instanceId:id,historySequence:'4',eventType:'UNKNOWN_FUTURE',commandId:'00000000-0000-0000-0000-000000000001',correlationId:null,actorKind:'USER',actorRef:'fixture',occurredAt:instance.startedAt,activityId:null,taskId:null,sourceNodeKey:null,targetNodeKey:null,transitionKey:null,detailsSchemaVersion:1};
@Injectable() class FixtureState { mode='success'; intercepted=0; }
@Injectable() class FixtureBackend implements HttpBackend {
  private state=inject(FixtureState);
  handle(request:HttpRequest<unknown>):Observable<HttpEvent<unknown>> {
    return new Observable(observer=>{
      if(request.headers.get('X-Consumer')!=='NgModule') {observer.error(new Error('Missing host interceptor'));return;}
      this.state.intercepted++;
      const mode=this.state.mode;
      const timer=setTimeout(()=>{
        if(mode==='denied' || mode==='failed'){observer.error(new HttpErrorResponse({status:mode==='denied'?404:500}));return;}
        let body:unknown=instance;
        if(request.url==='/host-administration/definitions')body={items:[{id,key:'alternate-definition',name:'Host administration definition',description:null,createdAt:instance.startedAt,createdBy:'fixture',lockVersion:'9007199254740993'}],nextAfterId:null};
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
  destination(intent:ViewerDestination){return this.router.createUrlTree(['/alternate',intent.kind,'id' in intent?intent.id:intent.instanceId]);}
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
@Component({selector:'fixture-root',standalone:false,template:`
  <h1>NgModule viewer consumer</h1><p>Host interceptor reads: {{state.intercepted}}</p>
  <button (click)="locale()">Change messages</button><button (click)="scope={}">Replace same-user session</button><button (click)="scope=null">Invalidate session</button>
  <button (click)="mode('success')">Successful reads</button><button (click)="mode('delayed')">Delayed reads</button><button (click)="mode('denied')">Denied reads</button><button (click)="mode('failed')">Failed reads</button>
  <div class="viewers"><workflow-instance-viewer [instanceId]="id" [sessionScope]="scope" [messages]="messages"/><workflow-instance-viewer [instanceId]="id" [sessionScope]="scope" [messages]="messages"/></div>
  <workflow-history [instanceId]="id" [sessionScope]="scope" [messages]="messages"/><workflow-history-event [instanceId]="id" sequence="4" [sessionScope]="scope" [messages]="messages"/>
  <workflow-definitions [sessionScope]="scope" [messages]="administrationMessages"/>
  <router-outlet/>
`}) class App {
  state=inject(FixtureState); id=id; scope:object|null={};messages=DEFAULT_VIEWER_MESSAGES;
  administrationMessages={definitionsTitle:'Host workflow library'};
  locale(){this.messages={...DEFAULT_VIEWER_MESSAGES,refresh:'Actualiser',current:'Étape actuelle',graph:'Graphe du workflow'};}
  mode(value:string){this.state.mode=value;this.scope={};}
}
@NgModule({declarations:[App,Destination],imports:[BrowserModule,RouterLink,RouterOutlet,WorkflowInstanceViewerComponent,WorkflowHistoryComponent,WorkflowHistoryEventComponent,WorkflowDefinitionsComponent],
  providers:[FixtureState,Reads,Navigation,provideRouter([{path:'alternate/:kind/:id',component:Destination}]),
    provideHttpClient(withInterceptors([(request,next)=>next(request.clone({setHeaders:{'X-Consumer':'NgModule'}}))])),
    {provide:WORKFLOW_ADMINISTRATION_API,useFactory:administrationFixture},
    {provide:HttpBackend,useClass:FixtureBackend},{provide:WORKFLOW_VIEWER_READS,useExisting:Reads},{provide:WORKFLOW_VIEWER_NAVIGATION,useExisting:Navigation}],bootstrap:[App]})
class AppModule {}
platformBrowser().bootstrapModule(AppModule).catch(console.error);
