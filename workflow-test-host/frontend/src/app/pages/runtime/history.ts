import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkflowHistoryComponent } from '@workflow-engine/ui';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:'console-history',standalone:true,imports:[WorkflowHistoryComponent],templateUrl:'./history.html'})
export class HistoryPage {
  readonly session=inject(ConsoleSession); id=''; sequence='';
  private readonly route=inject(ActivatedRoute); private readonly destroyRef=inject(DestroyRef);
  ngOnInit(){this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(p=>{this.id=p.get('id')!;this.sequence=p.get('sequence')||'';});}
}
