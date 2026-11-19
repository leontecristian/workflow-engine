import { validateEvent } from './read-validation';
import { Component, Input, signal } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ViewerReadState } from './read-state';
import type { BusinessHistoryEvent, HistoryEvent } from './models';
import { routingEvidence } from './routing-evidence';
import { downloadJson } from './download-json';
import { DEFAULT_VIEWER_MESSAGES } from './contracts';
@Component({selector:'workflow-history-event',standalone:true,imports:[DatePipe,JsonPipe,MatButtonModule,RouterLink],
  template: `<section [attr.aria-label]="messages.event"><h2>{{messages.event}}@if(technical()){ #{{sequence}} }</h2>
    <button mat-button (click)="load()" [disabled]="busy()">{{messages.refresh}}</button>
    @if(busy()){<p role="status">{{messages.loading}}</p>}
    @if(error()){<p role="alert">{{errorText()}}</p>}
    @if(business();as event){
      <h3>{{event.summary.label}}</h3>
      @if(event.stepName){<p>{{event.stepName}}</p>}
      <p>{{event.summary.actorLabel}} · <time [attr.datetime]="event.summary.occurredAt">{{event.summary.occurredAt | date:'medium':'UTC'}}</time> (UTC)</p>
      @if(event.outcomeLabel){<p>{{event.outcomeLabel}}</p>}
      @if(event.comment){<p class="comment">{{event.comment}}</p>}
      @if(event.taskId){
        @if(navigation?.destination({kind:'task',id:event.taskId}); as link){
          <a mat-button [routerLink]="link">{{messages.task}}</a>
        }
      }
    }
    @if(technical();as event){<pre>{{event.summary | json}}</pre>
      @if(routing();as route){<h3>{{messages.routing}} {{route.nodeKey}}</h3>
        <p>{{messages.selected}} {{route.selectedTransitionKey}}{{route.usedDefault ? ' · '+messages.defaultRoute : ''}}</p>
        @for(evaluation of route.evaluations;track evaluation.transitionKey){<h4>{{evaluation.transitionKey}}: {{evaluation.result}}</h4>
          @for(read of evaluation.reads;track $index){
            <p>{{read.variableKey}} ({{read.type}}): @if(read.state === 'EXISTS'){ {{existenceLabel()}} = {{read.value}} } @else { {{read.state}} }</p>}
          @if(!evaluation.reads.length){<p>{{messages.noReads}}</p>}}
        <p>{{messages.routingHint}}</p><p>{{route.usedDefault ? messages.defaultHint : messages.shortCircuit}}</p>}
      <h3>{{messages.details}}</h3><button mat-button (click)="exportDetails()">{{messages.download}}</button><pre>{{event.detailsJson}}</pre>}
  </section>`,styles:[`pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:40rem;overflow:auto}.comment{white-space:pre-wrap;overflow-wrap:anywhere}`]})
export class WorkflowHistoryEventComponent extends ViewerReadState {
  @Input() sequence = '';
  readonly detail = signal<HistoryEvent | BusinessHistoryEvent | null>(null);
  clear() { this.detail.set(null); }
  existenceLabel() { return this.messages.existence ?? DEFAULT_VIEWER_MESSAGES.existence; }
  business() { const event = this.detail(); return event && 'representation' in event ? event : null; }
  technical() { const event = this.detail(); return event && !('representation' in event) ? event : null; }
  routing() { const event = this.technical(); return routingEvidence(event?.summary.eventType, event?.detailsJson); }
  exportDetails() { const text = this.technical()?.detailsJson; if(text !== undefined) downloadJson(text, 'workflow-history-details.json'); }
  async load() {
    const request = this.begin(); if(!request) return; const sequence = this.sequence;
    try { const event = await this.reads.historyEvent(request.id, sequence, request.signal);
      if(request.current() && sequence === this.sequence) { validateEvent(event, request.id, sequence); this.detail.set(event); }
    } catch(error) { if(request.current() && sequence === this.sequence) this.fail(error); }
    finally { if(request.current()) this.busy.set(false); }
  }
}
