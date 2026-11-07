import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkflowSubmissionsComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:"console-submissions",standalone:true,imports:[WorkflowSubmissionsComponent],templateUrl:"./submissions.html"})
export class SubmissionsPage {
 readonly session=inject(ConsoleSession);id="";
 private readonly route=inject(ActivatedRoute);private readonly destroyRef=inject(DestroyRef);
 ngOnInit(){this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(p=>this.id=p.get("id")!);}
}
