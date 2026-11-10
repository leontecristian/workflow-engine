import { validateEvent } from './read-validation';
import { Component, Input, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { ViewerReadState } from './read-state';
import type { HistoryEvent } from './models';
import { routingEvidence } from './routing-evidence';
import { downloadJson } from './download-json';
import { DEFAULT_VIEWER_MESSAGES } from './contracts';
@Component({selector:'workflow-history-event',standalone:true,imports:[JsonPipe,MatButtonModule],
  template: `<section [attr.aria-label]="messages.event"><h2>{{messages.event}} #{{sequence}}</h2>
    <button mat-button (click)="load()" [disabled]="busy()">{{messages.refresh}}</button>
    @if(busy()){<p role="status">{{messages.loading}}</p>}
    @if(error()){<p role="alert">{{errorText()}}</p>}
    @if(detail();as event){<pre>{{event.summary | json}}</pre>
      @if(routing();as route){<h3>{{messages.routing}} {{route.nodeKey}}</h3>
        <p>{{messages.selected}} {{route.selectedTransitionKey}}{{route.usedDefault ? ' · '+messages.defaultRoute : ''}}</p>
        @for(evaluation of route.evaluations;track evaluation.transitionKey){<h4>{{evaluation.transitionKey}}: {{evaluation.result}}</h4>
          @for(read of evaluation.reads;track $index){
            <p>{{read.variableKey}} ({{read.type}}): @if(read.state === 'EXISTS'){ {{existenceLabel()}} = {{read.value}} } @else { {{read.state}} }</p>}
          @if(!evaluation.reads.length){<p>{{messages.noReads}}</p>}}
        <p>{{messages.routingHint}}</p><p>{{route.usedDefault ? messages.defaultHint : messages.shortCircuit}}</p>}
      <h3>{{messages.details}}</h3><button mat-button (click)="exportDetails()">{{messages.download}}</button><pre>{{event.detailsJson}}</pre>}
  </section>`,styles:[`pre{white-space:pre-wrap;overflow-wrap:anywhere;max-height:40rem;overflow:auto}`]})
export class WorkflowHistoryEventComponent extends ViewerReadState {
  @Input() sequence = '';
  readonly detail = signal<HistoryEvent | null>(null);
  clear() { this.detail.set(null); }
  existenceLabel() { return this.messages.existence ?? DEFAULT_VIEWER_MESSAGES.existence; }
  routing() { return routingEvidence(this.detail()?.summary.eventType, this.detail()?.detailsJson); }
  exportDetails() { const text = this.detail()?.detailsJson; if(text !== undefined) downloadJson(text, 'workflow-history-details.json'); }
  async load() {
    const request = this.begin(); if(!request) return; const sequence = this.sequence;
    try { const event = await this.reads.historyEvent(request.id, sequence, request.signal);
      if(request.current() && sequence === this.sequence) { validateEvent(event, request.id, sequence); this.detail.set(event); }
    } catch(error) { if(request.current() && sequence === this.sequence) this.fail(error); }
    finally { if(request.current()) this.busy.set(false); }
  }
}
