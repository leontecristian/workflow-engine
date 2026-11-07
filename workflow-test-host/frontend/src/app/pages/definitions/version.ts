import { Component, inject } from '@angular/core';
import { WorkflowDefinitionVersionComponent } from '@workflow-engine/ui/administration';
import { ConsoleSession } from '../../data/console-session';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
@Component({selector:'console-version',standalone:true,imports:[WorkflowDefinitionVersionComponent],template:`<workflow-definition-version [sessionScope]="session.admin ? session.viewerScope() : null" [versionId]="id()"  />`})
export class VersionPage{readonly session=inject(ConsoleSession);readonly id=toSignal(inject(ActivatedRoute).paramMap.pipe(map(params=>params.get('id')||'')),{initialValue:''});}
