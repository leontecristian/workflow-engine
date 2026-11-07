import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkflowHistoryEventComponent } from '@workflow-engine/ui';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:'console-history-event',standalone:true,imports:[WorkflowHistoryEventComponent],templateUrl:'./history-event.html'})
export class HistoryEventPage {
  readonly session=inject(ConsoleSession); id=''; sequence='';
  private readonly route=inject(ActivatedRoute); private readonly destroyRef=inject(DestroyRef);
  ngOnInit(){this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(p=>{this.id=p.get('id')!;this.sequence=p.get('sequence')||'';});}
}
