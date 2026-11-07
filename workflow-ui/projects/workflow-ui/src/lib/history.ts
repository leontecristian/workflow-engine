import { validateHistory } from './read-validation';
import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ViewerReadState } from './read-state';
import type { HistorySummary } from './models';
@Component({selector:'workflow-history', standalone:true, imports:[RouterLink,MatButtonModule],
  template: `<section [attr.aria-label]="messages.history"><h2>{{messages.history}}</h2>
    <button mat-button (click)="load()" [disabled]="busy()">{{messages.refresh}}</button>
    <label>{{messages.pageSize}} <select [value]="pageSize" (change)="pageSize=+$any($event.target).value;load()"><option>10</option><option>50</option><option>100</option></select></label>
    @if(busy()){<p role="status">{{messages.loading}}</p>}
    @if(error()){<p role="alert">{{errorText()}}</p>}
    @if(!busy() && !error() && !rows().length){<p>{{messages.empty}}</p>}
    <div class="scroll"><table><thead><tr><th>{{messages.sequence}}</th><th>{{messages.type}}</th><th>{{messages.actor}}</th><th>{{messages.occurred}}</th></tr></thead><tbody>
      @for(row of rows();track row.historySequence){<tr><td>
        @if(navigation?.destination({kind:'event',instanceId:instanceId,sequence:row.historySequence});as link){<a [routerLink]="link">{{row.historySequence}}</a>}@else{ {{row.historySequence}} }
      </td><td>{{row.eventType}}</td><td>{{row.actorRef}}</td><td>{{row.occurredAt}}</td></tr>}
    </tbody></table></div>
    @if(next()){<button mat-button (click)="load(next()!)" [disabled]="busy()">{{messages.next}}</button>}
  </section>`,styles:[`.scroll{overflow:auto} table{width:100%;border-collapse:collapse} th,td{text-align:left;padding:.75rem;overflow-wrap:anywhere} th{font-weight:600}`]})
export class WorkflowHistoryComponent extends ViewerReadState {
  readonly rows = signal<HistorySummary[]>([]);
  readonly next = signal<string | null>(null);
  pageSize = 50;
  clear() { this.rows.set([]); this.next.set(null); }
  async load(cursor='0') {
    const request = this.begin(); if (!request) return;
    try { const page = await this.reads.history(request.id, cursor, this.pageSize, request.signal);
      if (request.current()) { validateHistory(page, request.id, cursor); this.rows.set(page.items); this.next.set(page.nextAfterSequence); }
    } catch(error) { if(request.current()) this.fail(error); }
    finally { if(request.current()) this.busy.set(false); }
  }
}
