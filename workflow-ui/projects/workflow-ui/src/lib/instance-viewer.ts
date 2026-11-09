import { validateInstance, validateGraph } from './read-validation';
import { Component, signal } from '@angular/core';
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
      </div>
      @if(busy()) { <p role="status">{{messages.loading}}</p> }
      @if(error()) { <p role="alert">{{errorText()}}</p> }
      @if(detail(); as d) {
        <mat-card appearance="outlined" class="overview">
          <mat-card-content>
            <p class="identity">{{messages.instance}} #{{d.id}}</p>
            <h2>{{d.status === 'COMPLETED' ? messages.completed : d.status === 'CANCELLED' ? presentation.cancelled : presentation.active}}
              @if(d.resultKey) { <span class="outcome"> · {{presentation.eventLabel(d.resultKey)}}</span> }
            </h2>
            <p class="workflow">{{d.definitionKey}} · {{presentation.version}} {{d.definitionVersion}}</p>
            <dl>
              <div><dt>{{messages.currentPosition}}</dt><dd>{{currentStep(d)}}</dd></div>
              <div><dt>{{presentation.started}}</dt><dd>{{d.startedBy}}</dd><dd><time [attr.datetime]="d.startedAt">{{d.startedAt | date:'medium':'UTC'}} UTC</time></dd></div>
              @if(d.endedAt) {
                <div><dt>{{presentation.finished}}</dt>@if(d.endedBy) { <dd>{{d.endedBy}}</dd> }<dd><time [attr.datetime]="d.endedAt">{{d.endedAt | date:'medium':'UTC'}} UTC</time></dd></div>
              }
            </dl>
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
    .toolbar, dl { display:flex; flex-wrap:wrap; gap:.75rem 1.75rem; align-items:start; }
    .toolbar { gap:.5rem; align-items:center; }
    mat-card { margin-block:1rem; }
    mat-card-content { padding:1.25rem; }
    h2, .identity, .workflow { margin:0 0 .5rem; overflow-wrap:anywhere; }
    .overview h2 { font-size:1.65rem; }
    .identity, dt, time { font-size:.85rem; color:var(--mat-sys-on-surface-variant, #475467); }
    .outcome { font-weight:500; }
    dl { margin:1rem 0 0; }
    dd { margin:.2rem 0 0; overflow-wrap:anywhere; }
    summary { cursor:pointer; font-weight:600; }
    pre { white-space:pre-wrap; overflow-wrap:anywhere; max-height:32rem; overflow:auto; }
  `]})
export class WorkflowInstanceViewerComponent extends ViewerReadState {
  readonly detail = signal<InstanceView | null>(null);
  readonly graph = signal<InstanceGraph | null>(null);
  readonly graphError = signal<ViewerReadError | null>(null);
  currentStep(detail: InstanceView) {
    return this.graph()?.nodes.find(node => node.key === detail.currentNodeKey)?.name ?? detail.currentNodeKey;
  }
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
