import { Component, inject } from '@angular/core';
import { WorkflowCommandsComponent } from '@workflow-engine/ui/runtime';
import { ConsoleSession } from '../../data/console-session';
@Component({selector:'console-commands',standalone:true,imports:[WorkflowCommandsComponent],templateUrl:'./commands.html'})
export class CommandsPage {readonly session=inject(ConsoleSession);}
