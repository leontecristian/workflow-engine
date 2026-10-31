import { Component, inject, signal } from "@angular/core";
import { Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { Capabilities } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-capabilities",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./capabilities.html",
})
export class CapabilitiesPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Capabilities",
    eyebrow: "DEPLOYED SUPPORT",
    description:
      "Inspect real executable sandbox support and startup-only settings.",
    icon: "settings",
  };
  detail = signal<Capabilities | null>(null);
  ngOnInit() {
    void this.load();
  }
  async load() {
    await this.run(async () => {
      this.detail.set(await this.api.getCapabilities());
    });
  }
}
