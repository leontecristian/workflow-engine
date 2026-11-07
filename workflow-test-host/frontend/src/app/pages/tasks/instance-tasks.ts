import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkflowInstanceTasksComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:"console-instance-tasks",standalone:true,imports:[WorkflowInstanceTasksComponent],templateUrl:"./instance-tasks.html"})
export class InstanceTasksPage {
 readonly session=inject(ConsoleSession);id="";
 private readonly route=inject(ActivatedRoute);private readonly destroyRef=inject(DestroyRef);
 ngOnInit(){this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(p=>this.id=p.get("id")!);}
}
