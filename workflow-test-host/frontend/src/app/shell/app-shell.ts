import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { RouterOutlet, RouterLink, RouterLinkActive } from "@angular/router";
import { BreakpointObserver } from "@angular/cdk/layout";
import { MatButtonModule } from "@angular/material/button";
import { MatListModule } from "@angular/material/list";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatToolbarModule } from "@angular/material/toolbar";
import { ConsoleIcon } from "../ui/icon";
import { ConsoleApi } from "../data/console-api";
@Component({
  selector: "app-root",
  standalone: true,
  imports: [
    CommonModule,
    ConsoleIcon,
    MatButtonModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
  ],
  templateUrl: "./app-shell.html",
})
export class AppShell {
  compact = signal(false);
  private readonly breakpoints = inject(BreakpointObserver);
  private readonly destroyRef = inject(DestroyRef);
  api = inject(ConsoleApi);
  constructor() {
    this.breakpoints
      .observe("(max-width: 960px)")
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((state) => this.compact.set(state.matches));
    void this.api.initialize().catch(() => (location.href = "/console/login"));
  }
  flags(): string {
    return Object.entries(this.api.capabilities()?.failureFlags || {})
      .filter(([, v]) => v)
      .map(([k]) => k)
      .join(", ");
  }
  failing(): boolean {
    return !!this.flags();
  }
}
