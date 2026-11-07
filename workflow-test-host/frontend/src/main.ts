import "zone.js";
import { provideZoneChangeDetection } from "@angular/core";
import { bootstrapApplication } from "@angular/platform-browser";
import { provideRouter, withHashLocation } from "@angular/router";
import { AppShell } from "./app/shell/app-shell";
import { APP_ROUTES } from "./app/app.routes";
import { WORKFLOW_VIEWER_READS, WORKFLOW_VIEWER_NAVIGATION } from '@workflow-engine/ui';
import { ConsoleViewerReads, ConsoleViewerNavigation } from './app/data/viewer-adapters';
bootstrapApplication(AppShell, {
  providers: [
    {provide:WORKFLOW_VIEWER_READS,useExisting:ConsoleViewerReads},
    {provide:WORKFLOW_VIEWER_NAVIGATION,useExisting:ConsoleViewerNavigation},
    provideZoneChangeDetection(),
    provideRouter(APP_ROUTES, withHashLocation()),
  ],
}).catch(console.error);
