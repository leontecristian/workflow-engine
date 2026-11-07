import { validateInstance, validateGraph } from './read-validation';
import { Component, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ViewerReadState } from './read-state';
import { normalizeViewerReadError, ViewerReadError } from './contracts';
import type { InstanceView, InstanceGraph } from './models';
import { WorkflowInstanceGraphComponent } from './graph/instance-graph';
@Component({selector:'workflow-instance-viewer', standalone:true, imports:[JsonPipe, RouterLink, MatButtonModule, MatCardModule, WorkflowInstanceGraphComponent],
  template: `
    <section [attr.aria-label]="messages.instance">
      <button mat-button (click)="load()" [disabled]="busy()">{{messages.refresh}}</button>
      @if(busy()) { <p role="status">{{messages.loading}}</p> }
      @if(error()) { <p role="alert">{{errorText()}}</p> }
      @if(detail(); as d) {
        <mat-card appearance="outlined"><mat-card-header><h2>{{messages.instance}} #{{d.id}} · {{d.definitionKey}} · {{d.definitionVersion}}</h2></mat-card-header>
        <mat-card-content>
          <dl><div><dt>{{messages.currentPosition}}</dt><dd>{{d.currentNodeKey}}</dd></div><div><dt>{{messages.result}}</dt><dd>{{d.resultKey || messages.notFinished}}</dd></div><div><dt>{{messages.token}}</dt><dd>{{d.lockVersion}}</dd></div><div><dt>{{messages.lastSequence}}</dt><dd>{{d.lastHistorySequence}}</dd></div></dl>
          <pre>{{d | json}}</pre>
          @if(navigation?.destination({kind:'history',instanceId:instanceId}); as link) { <a mat-button [routerLink]="link">{{messages.history}}</a> }
        </mat-card-content></mat-card>
      }
      @if(graph(); as model) { <h2>{{messages.graph}}</h2><workflow-instance-graph [graph]="model" [messages]="messages" /> }
      @if(graphError(); as failure) { <p role="status">{{messages.unavailable}}: {{failure.category === 'LIMIT_EXCEEDED' ? messages.limit : messages.failed}}</p> }
    </section>
  `, styles:[`:host{display:block;min-width:0} pre { white-space:pre-wrap; overflow-wrap:anywhere; } mat-card { margin-block:1rem; } dl{display:flex;flex-wrap:wrap;gap:1.5rem} dd{margin:0;font-weight:600;overflow-wrap:anywhere} dt{font-size:.85rem}`]})
export class WorkflowInstanceViewerComponent extends ViewerReadState {
  readonly detail = signal<InstanceView | null>(null);
  readonly graph = signal<InstanceGraph | null>(null);
  readonly graphError = signal<ViewerReadError | null>(null);
  clear() { this.detail.set(null); this.graph.set(null); this.graphError.set(null); }
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
    } catch (error) { if (request.current()) this.fail(error); }
    finally { if (request.current()) this.busy.set(false); }
  }
}
