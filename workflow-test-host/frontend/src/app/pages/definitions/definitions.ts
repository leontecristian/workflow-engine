import { Component, inject } from '@angular/core';
import { WorkflowDefinitionsComponent } from '@workflow-engine/ui/administration';
import { ConsoleSession } from '../../data/console-session';

@Component({selector:'console-definitions',standalone:true,imports:[WorkflowDefinitionsComponent],template:`<workflow-definitions [sessionScope]="session.admin ? session.viewerScope() : null"   />`})
export class DefinitionsPage{readonly session=inject(ConsoleSession);}
