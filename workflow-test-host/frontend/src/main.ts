import "zone.js";
import { ENVIRONMENT_INITIALIZER, inject, provideZoneChangeDetection } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { provideRouter, withHashLocation } from "@angular/router";
import { AppShell } from "./app/shell/app-shell";
import { APP_ROUTES } from "./app/app.routes";
import { WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION, WORKFLOW_ADMINISTRATION_API, WORKFLOW_RUNTIME_READS, WORKFLOW_RUNTIME_COMMANDS } from '@workflow-engine/ui';
import { ConsoleViewerReads, ConsoleViewerNavigation } from './app/data/viewer-adapters';
import { ConsoleAdministrationApi } from './app/data/administration-adapters';
import { ConsoleRuntimeReads } from './app/data/runtime-read-adapters';
import { ConsoleRuntimeCommands, ConsoleRuntimeSessionBridge } from './app/data/runtime-command-adapters';
bootstrapApplication(AppShell, {
  providers: [
    {provide:WORKFLOW_RUNTIME_READS,useExisting:ConsoleRuntimeReads},
    {provide:WORKFLOW_RUNTIME_COMMANDS,useExisting:ConsoleRuntimeCommands},
    {provide:ENVIRONMENT_INITIALIZER,multi:true,useValue:()=>inject(ConsoleRuntimeSessionBridge)},
    {provide:WORKFLOW_ADMINISTRATION_API,useExisting:ConsoleAdministrationApi},
    {provide:WORKFLOW_VIEWER_READS,useExisting:ConsoleViewerReads},
    {provide:WORKFLOW_VIEWER_NAVIGATION,useExisting:ConsoleViewerNavigation},
    provideZoneChangeDetection(),
    provideRouter(APP_ROUTES, withHashLocation()),
  ],
}).catch(console.error);
