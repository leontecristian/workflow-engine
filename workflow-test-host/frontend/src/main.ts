import { bootstrapApplication } from "@angular/platform-browser";
import { provideRouter, withHashLocation } from "@angular/router";
import { AppShell } from "./app/shell/app-shell";
import { APP_ROUTES } from "./app/app.routes";
bootstrapApplication(AppShell, {
  providers: [provideRouter(APP_ROUTES, withHashLocation())],
}).catch(console.error);
