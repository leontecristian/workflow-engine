import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { SandboxRequest } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { destroyedState, pageState } from "../../ui/page-state";
@Component({
  selector: "console-request",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./request.html",
})
export class RequestPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Sandbox request",
    eyebrow: "HOST RECORD",
    description:
      "Inspect the request, its sandbox business status and committed workflow link.",
    icon: "file",
  };
  id = "";
  detail = signal<SandboxRequest | null>(null);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isDestroyed = destroyedState(this.destroyRef);
  ngOnInit() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        this.id = params.get("id")!;
        void this.load();
      });
  }
  async load() {
    const id = this.id;
    await this.run(async () => {
      const d = await this.api.request(id);
      if (!this.isDestroyed() && this.id === id) this.detail.set(d);
    });
  }
  selectRequest(row: SandboxRequest | null) {
    if (row)
      void this.router.navigate(["/requests"], {
        queryParams: { requestId: row.id, requestVersion: row.lockVersion },
      });
  }
}
