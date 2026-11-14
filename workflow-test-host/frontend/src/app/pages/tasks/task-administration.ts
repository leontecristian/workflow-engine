import { Component, inject } from '@angular/core';
import { WorkflowTaskAdministrationComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:'console-task-administration',standalone:true,imports:[WorkflowTaskAdministrationComponent],template:'<workflow-task-administration [sessionScope]="session.viewerScope()" />'})
export class TaskAdministrationPage {readonly session=inject(ConsoleSession);}
