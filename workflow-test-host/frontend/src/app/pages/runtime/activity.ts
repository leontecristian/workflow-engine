import { Component, DestroyRef, inject, signal } from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { ActivatedRoute, Router } from "@angular/router";
import { ConsoleApi } from "../../data/console-api";
import type { ActivityView } from "../../data/dtos";
import { PAGE_IMPORTS } from "../../ui/page-imports";
import { pageState } from "../../ui/page-state";
@Component({
  selector: "console-activity",
  standalone: true,
  imports: [...PAGE_IMPORTS],
  templateUrl: "./activity.html",
})
export class ActivityPage {
  api = inject(ConsoleApi);
  router = inject(Router);
  private readonly state = pageState();
  busy = this.state.busy;
  error = this.state.error;
  notice = this.state.notice;
  run = this.state.run;
  readonly meta = {
    title: "Activity details",
    eyebrow: "RUNTIME INSPECTION",
    description: "Inspect the human activity and its completion state.",
    icon: "tasks",
  };
  id = "";
  detail = signal<ActivityView | null>(null);
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
      const d = await this.api.activity(id);
      if (!this.destroyRef.destroyed && this.id === id) this.detail.set(d);
    });
  }
}
