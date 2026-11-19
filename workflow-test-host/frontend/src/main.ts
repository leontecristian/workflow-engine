import "zone.js";
import { ENVIRONMENT_INITIALIZER, inject, provideZoneChangeDetection } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { MatIconRegistry } from '@angular/material/icon';
import { AppShell } from "./app/shell/app-shell";
import { APP_ROUTES } from "./app/app.routes";
import { WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION, WORKFLOW_ADMINISTRATION_API, WORKFLOW_RUNTIME_READS, WORKFLOW_RUNTIME_COMMANDS, WORKFLOW_CANCELLATION, WORKFLOW_TASK_REASSIGNMENT } from '@workflow-engine/ui';
import { provideWorkflowTaskEditors } from '@workflow-engine/ui/runtime';
import { ConsoleViewerReads, ConsoleViewerNavigation } from './app/data/viewer-adapters';
import { ConsoleAdministrationApi } from './app/data/administration-adapters';
import { ConsoleRuntimeReads } from './app/data/runtime-read-adapters';
import { ConsoleRuntimeCommands, ConsoleRuntimeSessionBridge } from './app/data/runtime-command-adapters';
import { BookingReviewComponent } from './app/pages/tasks/booking-review';
bootstrapApplication(AppShell, {
  providers: [
    {provide:ENVIRONMENT_INITIALIZER,multi:true,useValue:()=>inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined','mat-ligature-font')},
    {provide:WORKFLOW_RUNTIME_READS,useExisting:ConsoleRuntimeReads},
    {provide:WORKFLOW_RUNTIME_COMMANDS,useExisting:ConsoleRuntimeCommands},
    provideWorkflowTaskEditors({ BOOKING_REVIEW: BookingReviewComponent }),
    {provide:WORKFLOW_CANCELLATION,useExisting:ConsoleRuntimeCommands},
    {provide:WORKFLOW_TASK_REASSIGNMENT,useFactory:(commands:ConsoleRuntimeCommands)=>({availability:commands.reassignmentAvailability.bind(commands),targets:commands.targets.bind(commands),reassign:commands.reassign.bind(commands)}),deps:[ConsoleRuntimeCommands]},
    {provide:ENVIRONMENT_INITIALIZER,multi:true,useValue:()=>inject(ConsoleRuntimeSessionBridge)},
    {provide:WORKFLOW_ADMINISTRATION_API,useExisting:ConsoleAdministrationApi},
    {provide:WORKFLOW_VIEWER_READS,useExisting:ConsoleViewerReads},
    {provide:WORKFLOW_VIEWER_NAVIGATION,useExisting:ConsoleViewerNavigation},
    provideZoneChangeDetection(),
    provideRouter(APP_ROUTES),
  ],
}).catch(console.error);
