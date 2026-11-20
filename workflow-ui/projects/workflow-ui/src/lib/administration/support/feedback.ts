import { Component, input } from "@angular/core";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatIconModule } from "@angular/material/icon";
@Component({
  selector: "console-feedback",
  standalone: true,
  styles: [`
    :host { display: block; min-width: 0; }
    .feedback {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 16px;
      margin-bottom: 16px;
      border: 1px solid currentColor;
      border-radius: 8px;
      font-size: 0.875rem;
      line-height: 1.5;
      overflow-wrap: anywhere;
    }
    mat-icon { flex: none; }
    .feedback-error {
      color: var(--mat-sys-on-error-container, #851d18);
      background: var(--mat-sys-error-container, #fff1ef);
    }
    .feedback-success {
      color: var(--mat-sys-on-secondary-container, #205b47);
      background: var(--mat-sys-secondary-container, #edf7f2);
    }
    p { margin: 4px 0 0; white-space: pre-wrap; }
    mat-progress-bar { margin-bottom: 20px; }
  `],
  imports: [MatIconModule, MatProgressBarModule],
  templateUrl: "./feedback.html",
})
export class ConsoleFeedback {
  busy = input(false);
  error = input("");
  notice = input("");
}
