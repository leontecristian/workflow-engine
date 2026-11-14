import { validateInstance, validateGraph } from './read-validation';
import { Component, Injector, inject, signal } from '@angular/core';
import type { MatDialogRef } from '@angular/material/dialog';
import type { WorkflowCancellationDialog } from './cancellation-dialog';
import { WORKFLOW_CANCELLATION } from './runtime/contracts';
import { CommandRecovery } from './runtime/command/command-recovery';
import type { Envelope } from './runtime/command/command-buffer';
import { DatePipe, JsonPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ViewerReadState } from './read-state';
import { normalizeViewerReadError, ViewerReadError } from './contracts';
import type { InstanceView, InstanceGraph } from './models';
import { WorkflowInstanceGraphComponent } from './graph/instance-graph';
@Component({selector:'workflow-instance-viewer', standalone:true, imports:[DatePipe, JsonPipe, RouterLink, MatButtonModule, MatCardModule, WorkflowInstanceGraphComponent],
  template: `
    <section [attr.aria-label]="messages.instance">
      <div class="toolbar">
        @if(navigation?.destination({kind:'instanceTasks',id:instanceId}); as link) {
          <a mat-stroked-button [routerLink]="link">{{presentation.tasks}}</a>
        }
        @if(navigation?.destination({kind:'history',instanceId:instanceId}); as link) {
          <a mat-stroked-button [routerLink]="link">{{messages.history}}</a>
        }
        <button mat-button (click)="load()" [disabled]="busy()">{{messages.refresh}}</button>
        @if(canCancel()) { <button mat-stroked-button (click)="confirmCancellation()" [disabled]="commandBusy()">Cancel workflow</button> }
      </div>
      @if(pendingCancellation(); as command) {
        <p role="status">The cancellation outcome needs checking. Keep the original request until its result is known.</p>
        <button mat-button (click)="sendCancellation(command,true)" [disabled]="commandBusy()">Check cancellation result</button>
        @if(lookupMiss()) { <p>A missing result does not prove the first attempt cannot commit.</p>
          <button mat-stroked-button (click)="sendCancellation(command,false)" [disabled]="commandBusy()">Retry original cancellation</button> }
      }
      @if(commandNotice()) { <p role="status">{{commandNotice()}}</p> }
      @if(busy()) { <p role="status">{{messages.loading}}</p> }
      @if(error()) { <p role="alert">{{errorText()}}</p> }
      @if(detail(); as d) {
        <mat-card appearance="outlined" class="overview">
          <mat-card-content>
            <div class="overview-heading">
            <p class="identity">{{messages.instance}} #{{d.id}}</p>
            <h2>{{d.status === 'COMPLETED' ? messages.completed : d.status === 'CANCELLED' ? presentation.cancelled : presentation.active}}
              @if(d.resultKey) { <span class="outcome"> · {{presentation.eventLabel(d.resultKey)}}</span> }
            </h2>
            <p class="workflow">{{d.definitionKey}} · <span class="definition-version">{{presentation.version}} {{d.definitionVersion}}</span></p>
            </div>
            <dl>
              <div><dt>{{messages.currentPosition}}</dt><dd>{{currentStep(d)}}</dd></div>
              <div><dt>{{presentation.started}}</dt><dd>{{d.startedBy}}</dd><dd><time [attr.datetime]="d.startedAt">{{d.startedAt | date:'medium':'UTC'}} UTC</time></dd></div>
              @if(d.endedAt) {
                <div><dt>{{presentation.finished}}</dt>@if(d.endedBy) { <dd>{{d.endedBy}}</dd> }<dd><time [attr.datetime]="d.endedAt">{{d.endedAt | date:'medium':'UTC'}} UTC</time></dd></div>
              }
            </dl>
            @if(d.status === 'CANCELLED') { <div class="cancellation-reason"><h3>Cancellation reason</h3><p>{{d.terminationReason}}</p></div> }
          </mat-card-content>
        </mat-card>
      }
      @if(graph(); as model) {
        <mat-card appearance="outlined"><mat-card-content>
          <h2>{{messages.graph}}</h2><workflow-instance-graph [graph]="model" [messages]="messages" />
        </mat-card-content></mat-card>
      }
      @if(graphError(); as failure) { <p role="status">{{messages.unavailable}}: {{failure.category === 'LIMIT_EXCEEDED' ? messages.limit : messages.failed}}</p> }
      @if(detail(); as d) {
        <mat-card appearance="outlined"><mat-card-content>
          <details>
            <summary>{{presentation.technical}}</summary>
            <dl><div><dt>{{messages.token}}</dt><dd>{{d.lockVersion}}</dd></div><div><dt>{{messages.lastSequence}}</dt><dd>{{d.lastHistorySequence}}</dd></div></dl>
            <pre>{{d | json}}</pre>
          </details>
        </mat-card-content></mat-card>
      }
    </section>
  `, styles:[`
    :host { display:block; min-width:0; }
    .toolbar { display:flex; flex-wrap:wrap; gap:.5rem; align-items:center; }
    mat-card { margin-block:.75rem; }
    mat-card-content { padding:1rem; }
    .overview mat-card-content { display:grid; grid-template-columns:minmax(12rem, .8fr) minmax(0, 2fr); gap:1rem 1.5rem; align-items:start; }
    h2, .identity, .workflow { margin:0 0 .5rem; overflow-wrap:anywhere; }
    .overview h2 { font-size:1.65rem; }
    .identity, dt, time { font-size:.85rem; color:var(--mat-sys-on-surface-variant, #475467); }
    .outcome { font-weight:500; }
    .definition-version { white-space:nowrap; }
    dl { display:grid; grid-template-columns:repeat(auto-fit, minmax(min(100%, 9rem), 1fr)); gap:.75rem 1.25rem; margin:.75rem 0 0; }
    .overview dl { margin:0; }
    dd { margin:.2rem 0 0; overflow-wrap:anywhere; }
    summary { cursor:pointer; font-weight:600; }
    pre { white-space:pre-wrap; overflow-wrap:anywhere; max-height:32rem; overflow:auto; }
    .cancellation-reason { grid-column:1/-1; } .cancellation-reason p { white-space:pre-wrap; overflow-wrap:anywhere; }
    @media(max-width: 700px) { .overview mat-card-content { grid-template-columns:1fr; } }
  `]})
export class WorkflowInstanceViewerComponent extends ViewerReadState {
  private readonly cancellation=inject(WORKFLOW_CANCELLATION,{optional:true});
  private readonly recovery=inject(CommandRecovery);
  private readonly injector=inject(Injector);
  readonly availability=signal<{allowed:boolean;lockVersion:string}|null>(null);
  readonly commandBusy=signal(false);
  readonly commandNotice=signal('');
  readonly lookupMiss=signal(false);
  private dialogOpen=false;
  private closed=false;
  private confirmation?:MatDialogRef<WorkflowCancellationDialog>;
  pendingCancellation():Envelope|null {
    return this.recovery.buffer.entries.find(e=>e.kind==='cancel'&&e.body['instanceId']===this.instanceId&&this.recovery.pendingIds.has(e.body['commandId']!))??null;
  }
  canCancel(){const d=this.detail(),a=this.availability();return !!(this.cancellation&&d&&d.status==='ACTIVE'&&a?.allowed&&a.lockVersion===d.lockVersion&&!this.pendingCancellation());}
  async confirmCancellation(){
    if(!this.canCancel()||this.commandBusy()||this.dialogOpen)return;
    const id=this.instanceId,scope=this.sessionScope,version=this.detail()!.lockVersion;
    this.dialogOpen=true;
    try {
    const [{MatDialog},{WorkflowCancellationDialog}]=await Promise.all([import('@angular/material/dialog'),import('./cancellation-dialog')]);
    if(this.closed||scope!==this.sessionScope||id!==this.instanceId||!this.canCancel()||version!==this.detail()?.lockVersion){this.dialogOpen=false;return;}
    const dialog=this.confirmation=this.injector.get(MatDialog).open(WorkflowCancellationDialog,{data:{instanceId:id},width:'34rem',maxWidth:'95vw'});
    dialog.afterClosed().subscribe(reason=>{
      this.dialogOpen=false;
      if(typeof reason!=='string'||scope!==this.sessionScope||id!==this.instanceId||!this.canCancel()||version!==this.detail()?.lockVersion)return;
      this.recovery.setScope(scope as object);
      const envelope=this.recovery.buffer.remember({kind:'cancel',body:{commandId:crypto.randomUUID(),instanceId:id,reason},expectedToken:version});
      void this.sendCancellation(envelope,false);
    });
    }catch{if(!this.closed&&scope===this.sessionScope&&id===this.instanceId)this.commandNotice.set('Confirmation could not be opened. Try again.');this.dialogOpen=false;}
  }
  async sendCancellation(envelope:Envelope,replay:boolean){
    if(this.commandBusy())return;
    const id=this.instanceId,scope=this.sessionScope;
    this.commandBusy.set(true);this.commandNotice.set('');
    this.recovery.buffer.select(envelope.body['commandId']!);
    try {
      const result=await this.recovery.execute(replay);
      if(scope!==this.sessionScope||id!==this.instanceId)return;
      if('found' in result&&!result.found){this.lookupMiss.set(true);this.commandNotice.set('No committed cancellation result was found.');}
      else {this.lookupMiss.set(false);await this.load();if(!this.closed&&scope===this.sessionScope&&id===this.instanceId)this.commandNotice.set('Workflow cancelled.');}
    }catch(error){
      if(scope!==this.sessionScope||id!==this.instanceId)return;
      this.lookupMiss.set(false);
      if(this.recovery.pendingIds.has(envelope.body['commandId']!))this.commandNotice.set('Cancellation outcome unknown. Check the result before retrying.');
      else {await this.load();if(!this.closed&&scope===this.sessionScope&&id===this.instanceId)this.commandNotice.set('Cancellation was rejected. Refresh and confirm a new request if it is still available.');}
    }finally{if(scope===this.sessionScope&&id===this.instanceId)this.commandBusy.set(false);}
  }
  readonly detail = signal<InstanceView | null>(null);
  readonly graph = signal<InstanceGraph | null>(null);
  readonly graphError = signal<ViewerReadError | null>(null);
  currentStep(detail: InstanceView) {
    return this.graph()?.nodes.find(node => node.key === detail.currentNodeKey)?.name ?? detail.currentNodeKey;
  }
  clear() { this.detail.set(null); this.graph.set(null); this.graphError.set(null); this.availability.set(null); }
  override ngOnChanges(changes:Record<string,unknown>){
    if('sessionScope' in changes)this.recovery.setScope(this.sessionScope as object|null);
    if('sessionScope' in changes||'instanceId' in changes){this.commandBusy.set(false);this.commandNotice.set('');this.lookupMiss.set(false);this.confirmation?.close();}
    super.ngOnChanges(changes);
  }
  override ngOnDestroy(){this.closed=true;this.confirmation?.close();super.ngOnDestroy();}
  async load() {
    const request = this.begin(); if (!request) return;
    try {
      const detail = await this.reads.instance(request.id, request.signal);
      if (!request.current()) return; validateInstance(detail, request.id); this.detail.set(detail);
      try {
        const graph = await this.reads.graph(request.id, request.signal);
        if (!request.current()) return; validateGraph(graph, request.id); this.graph.set(graph); this.detail.set(graph.instance);
      } catch (error) {
        if (!request.current()) return;
        const normalized = normalizeViewerReadError(error);
        if (normalized.category === 'NOT_FOUND' || normalized.category === 'UNAUTHENTICATED') this.fail(normalized);
        else if (normalized.category !== 'CANCELLED') this.graphError.set(normalized);
      }
      if(this.cancellation&&request.current()){
        try {const available=await this.cancellation.availability(request.id,request.signal);
          if(request.current()&&typeof available.allowed==='boolean'&&available.lockVersion===this.detail()?.lockVersion)this.availability.set(available);
        }catch{if(request.current())this.availability.set(null);}
      }
    } catch (error) { if (request.current()) this.fail(error); }
    finally { if (request.current()) this.busy.set(false); }
  }
}
