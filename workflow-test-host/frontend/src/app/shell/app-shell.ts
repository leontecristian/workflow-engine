import { Component, DestroyRef, computed, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { CommonModule } from "@angular/common";
import { RouterOutlet, RouterLink, RouterLinkActive } from "@angular/router";
import { BreakpointObserver } from "@angular/cdk/layout";
import { MatButtonModule } from "@angular/material/button";
import { MatListModule } from "@angular/material/list";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatIconModule } from "@angular/material/icon";
import { ConsoleApi } from "../data/console-api";
import { PageHeaderComponent, type PageHeaderSource } from "./page-header";
@Component({
  selector: "app-root",
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatButtonModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    PageHeaderComponent,
  ],
  templateUrl: "./app-shell.html",
})
export class AppShell {
  compact = signal(false);
  protected readonly detailPage = signal<PageHeaderSource | null>(null);
  protected readonly pageHeader = computed(() => this.detailPage()?.pageHeader() ?? null);
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
  protected activatePage(page: object) {
    this.detailPage.set('pageHeader' in page && typeof page.pageHeader === 'function' && 'pageHeaderParent' in page
      ? page as PageHeaderSource : null);
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
