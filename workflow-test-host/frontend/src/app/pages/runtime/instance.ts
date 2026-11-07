import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { ConsoleApi } from '../../data/console-api';
import { ConsoleSession } from '../../data/console-session';
import { WorkflowInstanceViewerComponent } from '@workflow-engine/ui';
import { WorkflowVariablesComponent } from '@workflow-engine/ui/runtime';
import { PAGE_IMPORTS } from '../../ui/page-imports';
import { destroyedState } from '../../ui/page-state';
@Component({selector:'console-instance',standalone:true,imports:[...PAGE_IMPORTS,WorkflowInstanceViewerComponent,WorkflowVariablesComponent],templateUrl:'./instance.html'})
export class InstancePage {
 readonly api=inject(ConsoleApi);readonly session=inject(ConsoleSession);
 readonly meta={title:'Workflow instance',eyebrow:'CURRENT RUNTIME STATE',description:'Inspect the current position and execution evidence.',icon:'workflow'};
 id='';effects=signal<import('../../data/dtos').Page<import('../../data/dtos').ServiceEffect>|null>(null);effectError=signal('');
 private readonly route=inject(ActivatedRoute);private readonly destroyRef=inject(DestroyRef);private readonly isDestroyed=destroyedState(this.destroyRef);
 ngOnInit(){this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(p=>{this.id=p.get('id')!;this.effects.set(null);this.effectError.set('');});}
  async loadEffects(next=false) {
    const id=this.id; this.effectError.set("");
    try {
      const result=await this.api.serviceEffects(id,next ? this.effects()?.nextAfterId || undefined : undefined);
      if(!this.isDestroyed() && id===this.id) this.effects.set(result);
    } catch(error) { if(id===this.id) this.effectError.set(String(error)); }
  }
}
