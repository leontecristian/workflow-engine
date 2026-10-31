import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { InstanceView } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-instance",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./instance.html",
})
export class InstancePage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Workflow instance",
    eyebrow: "CURRENT RUNTIME STATE",
    description:
      "Inspect the current position, pinned version and authoritative execution state.",
    icon: "workflow",
  };
  id = "";
  detail = signal<InstanceView | null>(null);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
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
      const d = await this.api.instance(id);
      if (!this.destroyRef.destroyed && this.id === id) this.detail.set(d);
    });
  }
}
