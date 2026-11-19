import { validateHistory } from './read-validation';
import { Component, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ViewerReadState } from './read-state';
import type { BusinessHistorySummary, HistorySummary } from './models';
@Component({selector:'workflow-history', standalone:true, imports:[DatePipe, RouterLink, MatButtonModule, MatCardModule],
  template: `
    <section [attr.aria-label]="messages.history">
      <header>
        @if(rawEvidence()){<p>{{messages.instance}} #{{instanceId}}</p>}
        <h1>{{messages.history}}</h1>
        @if(navigation?.destination({kind:'instance',instanceId:instanceId}); as link) {
          <a mat-stroked-button [routerLink]="link">{{presentation.backToInstance}}</a>
        }
      </header>
      <mat-card appearance="outlined">
        <mat-card-content>
          <div class="toolbar">
            <button mat-button (click)="load()" [disabled]="busy()">{{messages.refresh}}</button>
            <label>{{messages.pageSize}}
              <select #size [value]="pageSize" (change)="pageSize=+size.value;load()">
                <option>10</option><option>50</option><option>100</option>
              </select>
            </label>
          </div>
          @if(busy()){<p role="status">{{messages.loading}}</p>}
          @if(error()){<p role="alert">{{errorText()}}</p>}
          @if(!busy() && !error() && !rows().length){<p>{{messages.empty}}</p>}
          @if(rows().length) {
            <div class="scroll">
              <table [attr.aria-label]="messages.history">
                <thead><tr>
                  <th scope="col">{{messages.type}}</th><th scope="col">{{messages.actor}}</th>
                  <th scope="col">{{messages.occurred}} (UTC)</th>
                  @if(rawEvidence()){<th scope="col">{{messages.sequence}}</th>}
                  <th scope="col">{{rawEvidence() ? messages.details : messages.open}}</th>
                </tr></thead>
                <tbody>
                  @for(row of rows();track eventRef(row)) {
                    <tr>
                      <th scope="row">{{eventLabel(row)}}</th>
                      <td>{{actorLabel(row)}}</td>
                      <td><time [attr.datetime]="row.occurredAt">{{row.occurredAt | date:'medium':'UTC'}}</time></td>
                      @if(rawEvidence()){<td>{{eventRef(row)}}</td>}
                      <td>
                        @if(navigation?.destination({kind:'event',instanceId:instanceId,sequence:eventRef(row)}); as link) {
                          <a mat-button [routerLink]="link" [attr.aria-label]="presentation.viewDetails + ' · ' + eventLabel(row)">{{presentation.viewDetails}}</a>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
          @if(next()){<button class="next" mat-stroked-button (click)="load(next()!)" [disabled]="busy()">{{messages.next}}</button>}
        </mat-card-content>
      </mat-card>
    </section>
  `, styles:[`
    :host { display:block; min-width:0; }
    header { margin-block:1rem; }
    header p { margin:0; color:var(--mat-sys-on-surface-variant, #475467); }
    h1 { margin:.25rem 0 .75rem; font-size:clamp(1.75rem, 4vw, 2.25rem); }
    mat-card-content { padding:1rem; }
    .toolbar { display:flex; flex-wrap:wrap; align-items:center; gap:.5rem 1rem; margin-bottom:.5rem; }
    select { font:inherit; color:inherit; background:transparent; border:1px solid var(--mat-sys-outline, #667085); border-radius:.35rem; padding:.35rem; }
    .scroll { overflow:auto; }
    table { width:100%; border-collapse:collapse; }
    th, td { text-align:left; padding:.55rem .75rem; overflow-wrap:anywhere; }
    thead { background:var(--mat-sys-surface-container, #f2f4f7); }
    tbody tr { border-bottom:1px solid var(--mat-sys-outline-variant, #e0e4eb); }
    th { font-weight:600; }
    time { white-space:nowrap; }
    .next { margin-top:1rem; }
  `]})
export class WorkflowHistoryComponent extends ViewerReadState {
  readonly rows = signal<(HistorySummary | BusinessHistorySummary)[]>([]);
  readonly next = signal<string | null>(null);
  readonly rawEvidence = signal(false);
  pageSize = 50;
  clear() { this.rows.set([]); this.next.set(null); this.rawEvidence.set(false); }
  eventRef(row: HistorySummary | BusinessHistorySummary) { return 'eventRef' in row ? row.eventRef : row.historySequence; }
  eventLabel(row: HistorySummary | BusinessHistorySummary) {
    return 'label' in row ? row.label : this.presentation.eventLabel(row.eventType);
  }
  actorLabel(row: HistorySummary | BusinessHistorySummary) { return 'actorLabel' in row ? row.actorLabel : row.actorRef; }
  async load(cursor='0') {
    const request = this.begin(); if (!request) return;
    try { const page = await this.reads.history(request.id, cursor, this.pageSize, request.signal);
      if (request.current()) {
        validateHistory(page, request.id, cursor);
        this.rows.set(page.items);
        this.rawEvidence.set(!('representation' in page));
        this.next.set('representation' in page ? page.nextCursor : page.nextAfterSequence);
      }
    } catch(error) { if(request.current()) this.fail(error); }
    finally { if(request.current()) this.busy.set(false); }
  }
}
