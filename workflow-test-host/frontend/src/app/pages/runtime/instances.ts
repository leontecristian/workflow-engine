import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkflowInstancesComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:"console-instances",standalone:true,imports:[WorkflowInstancesComponent],templateUrl:"./instances.html"})
export class InstancesPage {
 readonly session=inject(ConsoleSession);id="";
}
