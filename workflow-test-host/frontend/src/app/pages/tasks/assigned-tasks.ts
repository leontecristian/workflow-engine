import { Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WorkflowAssignedTasksComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:"console-assigned-tasks",standalone:true,imports:[WorkflowAssignedTasksComponent],templateUrl:"./assigned-tasks.html"})
export class AssignedTasksPage {
 readonly session=inject(ConsoleSession);id="";
}
