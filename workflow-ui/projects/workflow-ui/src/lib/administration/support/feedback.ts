import { Component, input } from "@angular/core";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { ConsoleIcon } from "./icon";
@Component({
  selector: "console-feedback",
  standalone: true,
  styles: [".feedback{display:flex;align-items:center;gap:12px;padding:16px;margin-bottom:16px;border:1px solid currentColor;border-radius:8px}.feedback-error{color:var(--mat-sys-error,#a00)}p{margin:4px 0}"],
  imports: [ConsoleIcon, MatProgressBarModule],
  templateUrl: "./feedback.html",
})
export class ConsoleFeedback {
  busy = input(false);
  error = input("");
  notice = input("");
}
