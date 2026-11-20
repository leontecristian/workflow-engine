import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';
import "zone.js";
import { ENVIRONMENT_INITIALIZER, inject, provideZoneChangeDetection } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { provideRouter } from "@angular/router";
import { MatIconRegistry } from '@angular/material/icon';
import { AppShell } from "./app/shell/app-shell";
import { APP_ROUTES } from "./app/app.routes";
import { WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION, WORKFLOW_RUNTIME_COMMANDS } from '@workflow-engine/ui';
import {
  provideWorkflowAdministrationHttp,
  provideWorkflowRuntimeReadsHttp,
  provideWorkflowCancellationHttp,
  provideWorkflowTaskReassignmentHttp,
} from '@workflow-engine/ui/http';
import { CONSOLE_WORKFLOW_API, consoleWorkflowSessionInterceptor } from './app/data/workflow-http-session';
import { provideWorkflowTaskEditors } from '@workflow-engine/ui/runtime';
import { ConsoleViewerReads, ConsoleViewerNavigation } from './app/data/viewer-adapters';
import { consoleRuntimeCommands, ConsoleRuntimeSessionBridge } from './app/data/runtime-command-adapters';
import { BookingReviewComponent } from './app/pages/tasks/booking-review';
bootstrapApplication(AppShell, {
  providers: [
    {provide:ENVIRONMENT_INITIALIZER,multi:true,useValue:()=>inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-outlined','mat-ligature-font')},
    provideHttpClient(withNoXsrfProtection(), withInterceptors([consoleWorkflowSessionInterceptor])),
    provideWorkflowRuntimeReadsHttp(CONSOLE_WORKFLOW_API),
    {provide:WORKFLOW_RUNTIME_COMMANDS,useFactory:consoleRuntimeCommands},
    provideWorkflowTaskEditors({ BOOKING_REVIEW: BookingReviewComponent }),
    provideWorkflowCancellationHttp(CONSOLE_WORKFLOW_API),
    provideWorkflowTaskReassignmentHttp(CONSOLE_WORKFLOW_API),
    {provide:ENVIRONMENT_INITIALIZER,multi:true,useValue:()=>inject(ConsoleRuntimeSessionBridge)},
    provideWorkflowAdministrationHttp(CONSOLE_WORKFLOW_API),
    {provide:WORKFLOW_VIEWER_READS,useExisting:ConsoleViewerReads},
    {provide:WORKFLOW_VIEWER_NAVIGATION,useExisting:ConsoleViewerNavigation},
    provideZoneChangeDetection(),
    provideRouter(APP_ROUTES),
  ],
}).catch(console.error);
