import { Component, inject } from '@angular/core';
import { WorkflowDefinitionFamilyComponent } from '@workflow-engine/ui/administration';
import { ConsoleSession } from '../../data/console-session';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
@Component({selector:'console-family',standalone:true,imports:[WorkflowDefinitionFamilyComponent],template:`<workflow-definition-family [sessionScope]="session.admin ? session.viewerScope() : null" [definitionId]="id()"  />`})
export class FamilyPage{readonly session=inject(ConsoleSession);readonly id=toSignal(inject(ActivatedRoute).paramMap.pipe(map(params=>params.get('id')||'')),{initialValue:''});}
