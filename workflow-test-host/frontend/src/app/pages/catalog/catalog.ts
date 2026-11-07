import { Component, inject } from '@angular/core';
import { WorkflowCatalogComponent } from '@workflow-engine/ui/administration';
import { ConsoleSession } from '../../data/console-session';

@Component({selector:'console-catalog',standalone:true,imports:[WorkflowCatalogComponent],template:`<workflow-catalog [sessionScope]="session.admin ? session.viewerScope() : null"  initialCode="SANDBOX_REVIEW" />`})
export class CatalogPage{readonly session=inject(ConsoleSession);}
