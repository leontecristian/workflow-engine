import { Component, input } from "@angular/core";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { ConsoleIcon } from "./icon";
@Component({
  selector: "console-feedback",
  standalone: true,
  imports: [ConsoleIcon, MatProgressBarModule],
  templateUrl: "./feedback.html",
})
export class ConsoleFeedback {
  busy = input(false);
  error = input("");
  notice = input("");
}
